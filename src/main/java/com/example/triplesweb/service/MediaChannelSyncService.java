package com.example.triplesweb.service;

import com.example.triplesweb.entity.Artist;
import com.example.triplesweb.entity.Media;
import com.example.triplesweb.infra.YoutubeDataApiClient;
import com.example.triplesweb.media.MediaTabClassifier;
import com.example.triplesweb.repository.ArtistRepository;
import com.example.triplesweb.repository.MediaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 공식 채널 업로드(쇼츠 포함)를 media 테이블에 넣는다.
 * 이미 있는 행의 관리자 설명·멤버·탭은 유지하고, 빈 메타만 채운다.
 */
@Service
@RequiredArgsConstructor
public class MediaChannelSyncService {
    public record Result(int scanned, int inserted, int updated, int skipped) {}

    private static final int SAVE_BATCH = 50;
    private static final int TITLE_MAX = 255;

    private final YoutubeDataApiClient youtube;
    private final MediaRepository mediaRepository;
    private final ArtistRepository artistRepository;
    private final PlatformTransactionManager transactionManager;
    private final ReentrantLock lock = new ReentrantLock();

    @Value("${youtube.channel.handle:triplescosmos}")
    private String handle;

    @Value("${youtube.channel.id:UCJnL-TBcsYrF2SLs7tmiC8Q}")
    private String channelId;

    @Value("${media.sync.enabled:true}")
    private boolean enabled;

    @Value("${media.sync.latest-pages:3}")
    private int latestPages;

    public Result syncFull() {
        return sync(0);
    }

    public Result syncLatest() {
        return sync(Math.max(1, latestPages));
    }

    @Scheduled(cron = "${media.sync.cron:0 20 4 * * *}")
    public void scheduledLatest() {
        if (!enabled) return;
        try {
            syncLatest();
        } catch (IllegalStateException ignored) {
        }
    }

    private Result sync(int maxPages) {
        if (!lock.tryLock()) {
            throw new IllegalStateException("유튜브 채널 동기화가 이미 진행 중입니다.");
        }
        try {
            if (!youtube.hasApiKey()) {
                throw new IllegalStateException("유튜브 API 키가 없습니다.");
            }
            String playlist = youtube.resolveUploadsPlaylist(handle, channelId);
            if (playlist == null || playlist.isBlank()) {
                throw new IllegalStateException("채널을 찾지 못했습니다.");
            }
            List<String> ids = youtube.listUploadedVideoIds(playlist, maxPages);
            if (ids == null) {
                throw new IllegalStateException("채널 영상 목록을 가져오지 못했습니다.");
            }
            List<YoutubeDataApiClient.ChannelVideo> videos = youtube.fetchChannelVideos(ids);
            return new TransactionTemplate(transactionManager).execute(status -> upsert(ids.size(), videos));
        } finally {
            lock.unlock();
        }
    }

    private Result upsert(int scanned, List<YoutubeDataApiClient.ChannelVideo> videos) {
        Map<String, Media> existing = mediaRepository.findAllWithArtists().stream()
                .filter(media -> media.getYoutubeId() != null)
                .collect(Collectors.toMap(
                        media -> media.getYoutubeId().toLowerCase(Locale.ROOT),
                        Function.identity(),
                        (left, right) -> left));
        List<Artist> artists = artistRepository.findAll();
        int inserted = 0;
        int updated = 0;
        int skipped = 0;
        List<Media> batch = new ArrayList<>();
        for (YoutubeDataApiClient.ChannelVideo video : videos) {
            if (video == null || video.id() == null) {
                skipped++;
                continue;
            }
            if (video.liveOrUpcoming()) {
                skipped++;
                continue;
            }
            Media current = existing.get(video.id().toLowerCase(Locale.ROOT));
            if (current != null) {
                if (fillExisting(current, video, artists)) updated++;
                else skipped++;
                continue;
            }
            Media created = newMedia(video, artists);
            batch.add(created);
            existing.put(video.id().toLowerCase(Locale.ROOT), created);
            inserted++;
            if (batch.size() >= SAVE_BATCH) {
                mediaRepository.saveAll(batch);
                batch.clear();
            }
        }
        if (!batch.isEmpty()) mediaRepository.saveAll(batch);
        return new Result(scanned, inserted, updated, skipped);
    }

    private boolean fillExisting(Media media, YoutubeDataApiClient.ChannelVideo video, List<Artist> artists) {
        boolean changed = false;
        if (blank(media.getMediaTitle()) && !blank(video.title())) {
            media.setMediaTitle(clipTitle(video.title()));
            changed = true;
        }
        if (missingDuration(media.getMediaDur()) && !blank(video.duration())) {
            media.setMediaDur(video.duration());
            changed = true;
        }
        if ((media.getViewCount() == null || media.getViewCount() == 0L) && video.viewCount() != null) {
            media.setViewCount(video.viewCount());
            changed = true;
        }
        if (media.getPublishedOn() == null) {
            LocalDate published = parseDate(video.publishedOn());
            if (published != null) {
                media.setPublishedOn(published);
                changed = true;
            }
        }
        if ((media.getArtists() == null || media.getArtists().isEmpty())) {
            Set<Artist> matched = matchArtists(video.title(), artists);
            if (!matched.isEmpty()) {
                media.setArtists(matched);
                changed = true;
            }
        }
        return changed;
    }

    private Media newMedia(YoutubeDataApiClient.ChannelVideo video, List<Artist> artists) {
        LocalDate published = parseDate(video.publishedOn());
        String title = clipTitle(video.title());
        if (title.isBlank()) title = "제목 없음";
        Media media = Media.builder()
                .youtubeId(video.id())
                .tab(MediaTabClassifier.tabFor(video.title()))
                .mediaTitle(title)
                .mediaDur(video.duration() == null ? "" : video.duration())
                .viewCount(video.viewCount() == null ? 0L : video.viewCount())
                .description("")
                .publishedOn(published)
                .sortOrder(sortOrder(published))
                .build();
        media.setArtists(matchArtists(video.title(), artists));
        return media;
    }

    static Set<Artist> matchArtists(String title, List<Artist> artists) {
        Set<Artist> matched = new LinkedHashSet<>();
        if (title == null || title.isBlank() || artists == null || artists.isEmpty()) return matched;
        String folded = title.toLowerCase(Locale.ROOT);
        for (Artist artist : artists) {
            if (containsName(folded, artist.getName()) || containsName(folded, artist.getEngName())) {
                matched.add(artist);
            }
        }
        return matched;
    }

    private static boolean containsName(String foldedTitle, String name) {
        if (name == null) return false;
        String needle = name.trim().toLowerCase(Locale.ROOT);
        if (needle.length() < 2) return false;
        if (foldedTitle.contains(needle)) return true;
        String compact = needle.replace(" ", "");
        return compact.length() >= 2 && !compact.equals(needle) && foldedTitle.contains(compact);
    }

    private static String clipTitle(String title) {
        if (title == null) return "";
        String trimmed = title.trim();
        return trimmed.length() <= TITLE_MAX ? trimmed : trimmed.substring(0, TITLE_MAX);
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static boolean missingDuration(String duration) {
        if (duration == null || duration.isBlank()) return true;
        return "03:00".equals(duration) || "3:00".equals(duration);
    }

    private static LocalDate parseDate(String raw) {
        if (raw == null || raw.isBlank() || raw.length() < 10) return null;
        try {
            return LocalDate.parse(raw.substring(0, 10));
        } catch (DateTimeParseException ex) {
            return null;
        }
    }

    private static long sortOrder(LocalDate published) {
        if (published == null) return 0L;
        return published.getYear() * 10_000L + published.getMonthValue() * 100L + published.getDayOfMonth();
    }
}
