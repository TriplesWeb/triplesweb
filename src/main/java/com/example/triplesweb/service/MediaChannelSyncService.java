package com.example.triplesweb.service;

import com.example.triplesweb.entity.Artist;
import com.example.triplesweb.entity.Media;
import com.example.triplesweb.infra.NamuWikiYoutubeClient;
import com.example.triplesweb.infra.YoutubeDataApiClient;
import com.example.triplesweb.media.MediaHashtags;
import com.example.triplesweb.media.MediaTab;
import com.example.triplesweb.media.MediaTabClassifier;
import com.example.triplesweb.util.MediaOrdering;
import com.example.triplesweb.repository.ArtistRepository;
import com.example.triplesweb.repository.MediaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * 공식 채널 업로드와 나무위키 tripleS 본문·하위문서의 유튜브 링크를 media 테이블에 넣는다.
 * 기동 직후와 /media 진입 때 그 링크를 읽고, 실패하면 다시 시도한다.
 * 나무위키 목록이 비면 지정 채널 업로드 중 제목에 tripleS가 명시된 영상을 후보로 보충한다.
 * 팬메이드·지정 업로더가 아닌 영상·다른 가수 콘텐츠는 지우고, 이미 있는 공식·지정 출처 영상은 남긴다.
 * 이미 있는 행의 관리자 설명·멤버·탭은 유지하고, 빈 메타만 채운다.
 */
@Service
@RequiredArgsConstructor
public class MediaChannelSyncService {
    public record Result(int scanned, int inserted, int updated, int skipped) {}

    private static final int SAVE_BATCH = 50;
    private static final int TITLE_MAX = 255;
    private static final long MIN_EXTERNAL_AUDIENCE = 10_000;
    private static final Set<String> EXCLUDED_VIDEO_IDS = Set.of("PR6mJ59QMNc", "OYx2SGkx6f0");

    private final YoutubeDataApiClient youtube;
    private final NamuWikiYoutubeClient namu;
    private final MediaRepository mediaRepository;
    private final ArtistRepository artistRepository;
    private final PlatformTransactionManager transactionManager;
    private final ReentrantLock lock = new ReentrantLock();
    private final AtomicBoolean searchedOnce = new AtomicBoolean(false);
    private final AtomicBoolean searchStarted = new AtomicBoolean(false);

    @Value("${youtube.channel.handle:triplescosmos}")
    private String handle;

    @Value("${youtube.channel.id:UCJnL-TBcsYrF2SLs7tmiC8Q}")
    private String channelId;

    @Value("${media.sync.enabled:true}")
    private boolean enabled;

    @Value("${media.sync.latest-pages:3}")
    private int latestPages;

    @Value("${media.sync.external-latest-pages:1}")
    private int externalLatestPages;

    @Value("${media.sync.external-full-pages:5}")
    private int externalFullPages;

    @Value("${media.sync.external-handles:1theKOriginals,SBSKPOP,SBSKPOP_ZOOM,MBCkpop,KBSKpop,Mnet,MnetM2,ALLTHEKPOP,thekpop}")
    private String externalHandles;

    @Value("${youtube.mv.playlist-id:PLZlIsCBsz6M2cLA3F93I-snMa0sjAjAeY}")
    private String mvPlaylistId;

    public Result syncFull() {
        return sync(0, Math.max(1, externalFullPages));
    }

    public Result syncLatest() {
        return sync(Math.max(1, latestPages), 0);
    }

    public Result syncSearch() {
        return sync(Math.max(1, latestPages), Math.max(1, externalLatestPages));
    }

    public boolean externalSearchReady() {
        return searchedOnce.get();
    }

    public boolean externalSearchRunning() {
        return lock.isLocked() || (searchStarted.get() && !searchedOnce.get());
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        ensureExternalSearch();
    }

    /** 공식 계정이 아닌 영상을 아직 안 모았으면 백그라운드에서 나무위키 유튜브 링크를 읽는다. */
    public void ensureExternalSearch() {
        if (!enabled || searchedOnce.get() || !youtube.hasApiKey()) return;
        if (!searchStarted.compareAndSet(false, true)) return;
        Thread worker = new Thread(() -> {
            try {
                runQuietly(this::searchAndMark);
            } finally {
                if (!searchedOnce.get()) searchStarted.set(false);
            }
        }, "media-youtube-search");
        worker.setDaemon(true);
        worker.start();
    }

    @Scheduled(fixedDelayString = "${media.sync.latest-ms:1800000}", initialDelayString = "${media.sync.initial-delay-ms:5000}")
    public void scheduledLatest() {
        runQuietly(() -> searchedOnce.get() ? syncLatest() : searchAndMark());
    }

    @Scheduled(cron = "${media.sync.cron:0 20 4 * * *}", zone = "Asia/Seoul")
    public void scheduledSearch() {
        runQuietly(this::syncSearch);
    }

    private Result searchAndMark() {
        if (searchedOnce.get()) return new Result(0, 0, 0, 0);
        Result result = syncSearch();
        searchedOnce.set(true);
        return result;
    }

    private void runQuietly(Supplier<Result> action) {
        if (!enabled) return;
        try {
            action.get();
        } catch (IllegalStateException ignored) {
        }
    }

    private Result sync(int officialPages, int externalPages) {
        if (!lock.tryLock()) {
            throw new IllegalStateException("유튜브 채널 동기화가 이미 진행 중입니다.");
        }
        try {
            if (!youtube.hasApiKey()) {
                throw new IllegalStateException("유튜브 API 키가 없습니다.");
            }
            List<String> officialIds = officialVideoIds(officialPages);
            Set<String> namuIds = externalPages > 0 ? new LinkedHashSet<>(namu.videoIds()) : Set.of();
            Set<String> namuExternalIds = externalPages > 0 ? new LinkedHashSet<>(namu.externalVideoIds()) : Set.of();
            Set<String> mvIds = mvPlaylistIds();
            Set<String> allowedIds = externalPages > 0 ? allowedChannelIds() : Set.of();
            Set<String> fallbackIds = externalPages > 0 && (namuIds.isEmpty() || namuExternalIds.isEmpty())
                    ? publisherVideoIds(allowedIds, externalPages) : Set.of();
            LinkedHashSet<String> allIds = new LinkedHashSet<>();
            allIds.addAll(officialIds);
            allIds.addAll(namuIds);
            allIds.addAll(namuExternalIds);
            allIds.addAll(mvIds);
            allIds.addAll(fallbackIds);
            if (externalPages > 0) allIds.addAll(officialHashtagSearchIds());
            Set<String> hashtagIds = externalPages > 0 ? hashtagSearchIds(Math.min(externalPages, 2)) : Set.of();
            allIds.addAll(hashtagIds);
            List<YoutubeDataApiClient.ChannelVideo> videos = youtube.fetchChannelVideos(allIds);
            Set<String> hashtagUploads = eligibleHashtagUploads(videos, hashtagIds, allowedIds);
            Set<String> officialSet = new LinkedHashSet<>(officialIds);
            List<String> handles = splitCsv(externalHandles);
            UploadFormats formats = uploadFormats(videos, officialSet, officialPages);
            return new TransactionTemplate(transactionManager).execute(status ->
                    upsert(allIds.size(), videos, officialSet, namuIds, namuExternalIds, mvIds,
                            fallbackIds, allowedIds, handles, externalPages > 0, formats, hashtagUploads));
        } finally {
            lock.unlock();
        }
    }

    private List<String> officialVideoIds(int maxPages) {
        String playlist = youtube.resolveUploadsPlaylist(handle, channelId);
        if (playlist == null || playlist.isBlank()) {
            throw new IllegalStateException("채널을 찾지 못했습니다.");
        }
        List<String> ids = youtube.listUploadedVideoIds(playlist, maxPages);
        if (ids == null) {
            throw new IllegalStateException("채널 영상 목록을 가져오지 못했습니다.");
        }
        return ids;
    }

    private record UploadFormats(Set<String> shorts, Set<String> regular) {}

    private UploadFormats uploadFormats(List<YoutubeDataApiClient.ChannelVideo> videos,
                                        Set<String> officialIds, int maxPages) {
        Set<String> shortIds = formatPlaylistIds(true, maxPages);
        Set<String> regularIds = formatPlaylistIds(false, maxPages);
        for (YoutubeDataApiClient.ChannelVideo video : videos) {
            if (video == null || video.liveOrUpcoming() || officialIds.contains(video.id())
                    || MediaTabClassifier.isOfficialChannel(video.channelId(), video.channelTitle(), channelId)) continue;
            Integer seconds = MediaOrdering.durationSeconds(video.duration());
            if (seconds == null || seconds > 180) continue;
            if (youtube.hasUploadFormat(video.channelId(), video.id(), true)) shortIds.add(video.id());
            else if (youtube.hasUploadFormat(video.channelId(), video.id(), false)) regularIds.add(video.id());
        }
        return new UploadFormats(shortIds, regularIds);
    }

    private Set<String> formatPlaylistIds(boolean shorts, int maxPages) {
        List<String> ids = youtube.listUploadFormatVideoIds(channelId, shorts, maxPages);
        return ids == null ? new LinkedHashSet<>() : new LinkedHashSet<>(ids);
    }

    private Set<String> mvPlaylistIds() {
        if (mvPlaylistId == null || mvPlaylistId.isBlank()) return Set.of();
        List<String> ids = youtube.listUploadedVideoIds(mvPlaylistId.trim(), 0);
        return ids == null ? Set.of() : new LinkedHashSet<>(ids);
    }

    private List<String> officialHashtagSearchIds() {
        LinkedHashSet<String> ids = new LinkedHashSet<>();
        ids.addAll(youtube.searchVideoIds("#tripleS", null, 1));
        ids.addAll(youtube.searchVideoIds("#트리플에스", null, 1));
        return new ArrayList<>(ids);
    }

    private Set<String> hashtagSearchIds(int pages) {
        Set<String> ids = new LinkedHashSet<>();
        for (String query : MediaHashtags.tripleSHashtagQueries()) {
            ids.addAll(youtube.searchHashtagVideoIds(query, pages));
        }
        return ids;
    }

    private Set<String> eligibleHashtagUploads(List<YoutubeDataApiClient.ChannelVideo> videos, Set<String> searched,
                                               Set<String> allowedIds) {
        if (searched.isEmpty()) return Set.of();
        List<YoutubeDataApiClient.ChannelVideo> candidates = videos.stream()
                .filter(video -> video != null && searched.contains(video.id()) && !video.liveOrUpcoming())
                .filter(video -> video.viewCount() != null && video.viewCount() >= MIN_EXTERNAL_AUDIENCE)
                .filter(video -> !MediaTabClassifier.isOfficialChannel(video.channelId(), video.channelTitle(), channelId))
                .filter(video -> !MediaHashtags.reviewedFanChannel(video.channelId()))
                .filter(video -> MediaHashtags.hasDiscoveryHashtag(video.title(), video.description()))
                .filter(video -> !MediaHashtags.looksFanUpload(video.title(), video.description(), video.channelTitle(), null))
                .toList();
        Map<String, YoutubeDataApiClient.ChannelAudience> audiences = youtube.fetchChannelAudiences(
                candidates.stream().map(YoutubeDataApiClient.ChannelVideo::channelId).toList());
        Set<String> accepted = new LinkedHashSet<>();
        for (YoutubeDataApiClient.ChannelVideo video : candidates) {
            YoutubeDataApiClient.ChannelAudience audience = audiences.get(video.channelId());
            if (audience != null && audience.subscribers() >= MIN_EXTERNAL_AUDIENCE
                    && MediaHashtags.hasTripleSDiscoveryTag(video.title(), video.description(), video.channelTitle(), audience.description())
                    && (allowedIds.contains(video.channelId()) || MediaHashtags.channelNameEndsWithOfficial(video.channelTitle())
                        || MediaHashtags.hasPublisherEvidence(video.channelId(), audience.description(), video.description()))
                    && !MediaHashtags.looksFanUpload(video.title(), video.description(), video.channelTitle(), audience.description())) {
                accepted.add(video.id());
            }
        }
        return accepted;
    }

    private Set<String> allowedChannelIds() {
        LinkedHashSet<String> ids = new LinkedHashSet<>();
        for (String source : splitCsv(externalHandles)) {
            String id = youtube.resolveChannelId(source);
            if (id != null && !id.isBlank()) ids.add(id.trim());
        }
        return ids;
    }

    private Set<String> publisherVideoIds(Set<String> allowedIds, int maxPages) {
        Set<String> videoIds = new LinkedHashSet<>();
        for (String allowedId : allowedIds) {
            String playlist = youtube.resolveUploadsPlaylist(null, allowedId);
            if (playlist == null || playlist.isBlank()) continue;
            List<String> ids = youtube.listUploadedVideoIds(playlist, maxPages);
            if (ids != null) videoIds.addAll(ids);
        }
        return videoIds;
    }

    private Result upsert(
            int scanned,
            List<YoutubeDataApiClient.ChannelVideo> videos,
            Set<String> officialIds,
            Set<String> namuIds,
            Set<String> namuExternalIds,
            Set<String> mvIds,
            Set<String> fallbackIds,
            Set<String> allowedIds,
            List<String> handles,
            boolean dropUnrelated,
            UploadFormats formats,
            Set<String> hashtagUploads
    ) {
        Map<String, Media> existing = mediaRepository.findAllWithArtists().stream()
                .filter(media -> media.getYoutubeId() != null)
                .collect(Collectors.toMap(
                        media -> media.getYoutubeId().toLowerCase(Locale.ROOT),
                        Function.identity(),
                        (left, right) -> left));
        List<Artist> artists = artistRepository.findAll();
        List<String> memberNames = memberNames(artists);
        int inserted = 0;
        int updated = 0;
        int skipped = 0;
        List<Media> batch = new ArrayList<>();
        for (YoutubeDataApiClient.ChannelVideo video : videos) {
            if (video == null || video.id() == null || EXCLUDED_VIDEO_IDS.contains(video.id())) {
                skipped++;
                continue;
            }
            if (video.liveOrUpcoming()) {
                skipped++;
                continue;
            }
            boolean fromOfficialPlaylist = officialIds.contains(video.id());
            boolean official = fromOfficialPlaylist
                    || MediaTabClassifier.isOfficialChannel(video.channelId(), video.channelTitle(), channelId);
            boolean fromHashtag = !official && hashtagUploads.contains(video.id());
            if (!official && (MediaHashtags.reviewedFanChannel(video.channelId())
                    || MediaHashtags.looksFanUpload(video.title(), video.description(), video.channelTitle(), null))) {
                skipped++;
                continue;
            }
            boolean fromNamu = containsId(namuIds, video.id());
            boolean fromNamuExternal = containsId(namuExternalIds, video.id());
            boolean isBroadcast = MediaTabClassifier.isMusicShow(video.title()) || MediaTabClassifier.isFancam(video.title());
            boolean missingNamuVideos = isBroadcast ? namuIds.isEmpty() : namuExternalIds.isEmpty();
            boolean fromFallback = missingNamuVideos && containsId(fallbackIds, video.id())
                    && isVerifiedPublisherVideo(video, allowedIds);
            boolean otherOfficialShorts = MediaHashtags.otherArtistOfficialShorts(
                    video.title(), video.description(), video.channelTitle(), memberNames);
            boolean unofficial = !official && !otherOfficialShorts && !fromNamuExternal
                    && dropUnofficial(video, allowedIds, handles, memberNames);
            if (!official && !fromHashtag && !otherOfficialShorts && !fromNamuExternal && ((!fromNamu && !fromFallback) || unofficial)) {
                skipped++;
                continue;
            }
            boolean musicShow = !official && MediaTabClassifier.isMusicShow(video.title())
                    && !MediaTabClassifier.isFancam(video.title());
            boolean platformShort = formats.shorts().contains(video.id());
            String targetTab = MediaTabClassifier.tabFor(video.title(), video.duration(), official, musicShow,
                    containsId(mvIds, video.id()), fromNamuExternal, platformShort);
            Media current = existing.get(video.id().toLowerCase(Locale.ROOT));
            if (current != null) {
                if (fromHashtag) {
                    skipped++;
                    continue;
                }
                boolean changed = fillExisting(current, video, artists, official);
                boolean wasShort = MediaTab.SHORTS.getKey().equals(current.getTab());
                boolean formatChanged = (!wasShort && platformShort)
                        || (wasShort && !platformShort && formats.regular().contains(video.id()));
                if (!current.isCuratedTab() && formatChanged) {
                    current.setTab(targetTab);
                    changed = true;
                }
                if (changed) updated++;
                else skipped++;
                continue;
            }
            Media created = newMedia(video, artists, official, fromHashtag ? MediaTab.EXTERNAL.getKey() : targetTab);
            if (fromHashtag) {
                created.setCuratedTab(true);
                Set<Artist> tagged = resolveTaggedMembers(video, artists);
                if (!tagged.isEmpty()) created.setArtists(tagged);
            }
            batch.add(created);
            existing.put(video.id().toLowerCase(Locale.ROOT), created);
            inserted++;
            if (batch.size() >= SAVE_BATCH) {
                mediaRepository.saveAll(batch);
                batch.clear();
            }
        }
        if (!batch.isEmpty()) mediaRepository.saveAll(batch);
        if (dropUnrelated) {
            removeUnknownSources(existing, videos, namuIds, namuExternalIds, allowedIds, handles, memberNames, hashtagUploads);
        }
        return new Result(scanned, inserted, updated, skipped);
    }

    private static Set<Artist> resolveTaggedMembers(YoutubeDataApiClient.ChannelVideo video, List<Artist> artists) {
        List<String> names = MediaHashtags.taggedTripleSMembers(video.title(), video.description());
        return artists.stream().filter(artist -> names.contains(artist.getName()))
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private static boolean isVerifiedPublisherVideo(YoutubeDataApiClient.ChannelVideo video, Set<String> allowedIds) {
        return video.channelId() != null && allowedIds.contains(video.channelId())
                && MediaHashtags.mentionsGroupOutsideTags(video.title());
    }

    private boolean dropUnofficial(
            YoutubeDataApiClient.ChannelVideo video,
            Set<String> allowedIds,
            List<String> handles,
            List<String> memberNames
    ) {
        if (MediaHashtags.looksFanMade(video.title(), video.description())) return true;
        if (MediaHashtags.otherArtistOfficialShorts(
                video.title(), video.description(), video.channelTitle(), memberNames)) {
            return false;
        }
        if (MediaHashtags.taggedOtherKpopAct(video.title(), video.description(), memberNames)) return true;
        if (MediaHashtags.unknownUploader(
                video.channelId(), video.channelTitle(), allowedIds, handles)) {
            return true;
        }
        return MediaHashtags.overlapsOtherKpopAct(video.title(), video.description());
    }

    private boolean keepExternal(
            YoutubeDataApiClient.ChannelVideo video,
            Set<String> allowedIds,
            List<String> handles
    ) {
        return MediaHashtags.keepKnownSource(
                video.title(), video.description(), video.channelId(), video.channelTitle(),
                allowedIds, handles);
    }

    private int removeUnknownSources(
            Map<String, Media> existing,
            List<YoutubeDataApiClient.ChannelVideo> alreadyFetched,
            Set<String> namuIds,
            Set<String> namuExternalIds,
            Set<String> allowedIds,
            List<String> handles,
            List<String> memberNames,
            Set<String> hashtagUploads
    ) {
        List<Media> candidates = existing.values().stream()
                .filter(media -> media.getYoutubeId() != null && !blank(media.getYoutubeId()))
                .filter(media -> MediaTab.EXTERNAL.getKey().equals(media.getTab())
                        || MediaTab.SHORTS.getKey().equals(media.getTab())
                        || (!media.isCuratedTab() && blank(media.getDescription())))
                .toList();
        if (candidates.isEmpty()) return 0;
        Map<String, YoutubeDataApiClient.ChannelVideo> live = new java.util.LinkedHashMap<>();
        if (alreadyFetched != null) {
            for (YoutubeDataApiClient.ChannelVideo video : alreadyFetched) {
                if (video != null && video.id() != null) live.put(video.id().toLowerCase(Locale.ROOT), video);
            }
        }
        List<String> missing = new ArrayList<>();
        for (Media media : candidates) {
            String key = media.getYoutubeId().toLowerCase(Locale.ROOT);
            if (!live.containsKey(key)) missing.add(media.getYoutubeId());
        }
        if (!missing.isEmpty()) {
            for (YoutubeDataApiClient.ChannelVideo video : youtube.fetchChannelVideos(missing)) {
                if (video != null && video.id() != null) live.put(video.id().toLowerCase(Locale.ROOT), video);
            }
        }
        List<Media> drop = new ArrayList<>();
        for (Media media : candidates) {
            YoutubeDataApiClient.ChannelVideo video = live.get(media.getYoutubeId().toLowerCase(Locale.ROOT));
            if (video == null) continue;
            boolean storedOfficial = !blank(media.getChannelId()) && media.getChannelId().equalsIgnoreCase(channelId);
            boolean liveOfficial = MediaTabClassifier.isOfficialChannel(
                    video.channelId(), video.channelTitle(), channelId);
            if (storedOfficial || liveOfficial) continue;
            if (MediaHashtags.reviewedFanChannel(video.channelId())
                    || MediaHashtags.looksFanUpload(video.title(), video.description(), video.channelTitle(), null)) {
                drop.add(media);
                continue;
            }
            if (media.isCuratedTab() || !blank(media.getDescription()) || hashtagUploads.contains(video.id())) continue;
            if (MediaHashtags.hasPublisherEvidence(video.channelId(), null, video.description())
                    && MediaHashtags.hasTripleSDiscoveryTag(video.title(), video.description(), video.channelTitle(), null)) {
                continue;
            }
            if (MediaHashtags.otherArtistOfficialShorts(
                    video.title(), video.description(), video.channelTitle(), memberNames)) {
                continue;
            }
            boolean fromNamuExternal = containsId(namuExternalIds, video.id());
            if (fromNamuExternal && !MediaHashtags.looksFanMade(video.title(), video.description())) continue;
            if (dropUnofficial(video, allowedIds, handles, memberNames)) {
                drop.add(media);
                continue;
            }
            if (MediaTab.EXTERNAL.getKey().equals(media.getTab())
                    && namuExternalIds != null && !namuExternalIds.isEmpty()
                    && !fromNamuExternal && !isVerifiedPublisherVideo(video, allowedIds)) {
                drop.add(media);
                continue;
            }
            if (containsId(namuIds, video.id())) continue;
            if (keepExternal(video, allowedIds, handles)) continue;
            if (namuIds == null || namuIds.isEmpty()) continue;
            drop.add(media);
        }
        if (!drop.isEmpty()) mediaRepository.deleteAll(drop);
        return drop.size();
    }

    private boolean fillExisting(Media media, YoutubeDataApiClient.ChannelVideo video, List<Artist> artists, boolean official) {
        boolean changed = false;
        if (blank(media.getChannelId()) && !blank(storedChannelId(video, official))) {
            media.setChannelId(storedChannelId(video, official));
            changed = true;
        }
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

    private Media newMedia(
            YoutubeDataApiClient.ChannelVideo video,
            List<Artist> artists,
            boolean official,
            String tab
    ) {
        LocalDate published = parseDate(video.publishedOn());
        String title = clipTitle(video.title());
        if (title.isBlank()) title = "제목 없음";
        Media media = Media.builder()
                .youtubeId(video.id())
                .channelId(storedChannelId(video, official))
                .tab(tab)
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

    private String storedChannelId(YoutubeDataApiClient.ChannelVideo video, boolean official) {
        if (video != null && !blank(video.channelId())) return video.channelId().trim();
        return official ? channelId : "";
    }

    static List<String> memberNames(List<Artist> artists) {
        List<String> names = new ArrayList<>();
        if (artists == null) return names;
        for (Artist artist : artists) {
            if (artist == null) continue;
            if (artist.getName() != null && !artist.getName().isBlank()) names.add(artist.getName().trim());
            if (artist.getEngName() != null && !artist.getEngName().isBlank()) names.add(artist.getEngName().trim());
        }
        return names;
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

    private static boolean containsId(Set<String> ids, String id) {
        if (ids == null || id == null || id.isBlank()) return false;
        if (ids.contains(id)) return true;
        return ids.contains(id.toLowerCase(Locale.ROOT));
    }

    private static List<String> splitCsv(String raw) {
        if (raw == null || raw.isBlank()) return List.of();
        List<String> values = new ArrayList<>();
        for (String part : raw.split(",")) {
            if (part != null && !part.isBlank()) values.add(part.trim());
        }
        return values;
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
