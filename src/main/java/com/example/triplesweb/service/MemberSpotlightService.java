package com.example.triplesweb.service;

import com.example.triplesweb.dto.MediaDto;
import com.example.triplesweb.entity.Archive;
import com.example.triplesweb.entity.Artist;
import com.example.triplesweb.entity.MediaType;
import com.example.triplesweb.entity.SavedMeta;
import com.example.triplesweb.entity.Schedule;
import com.example.triplesweb.repository.ArchiveRepository;
import com.example.triplesweb.repository.ScheduleRepository;
import com.example.triplesweb.util.MemberPortraitOptions;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 멤버 상세에 올릴 최신 5개와, 그 멤버만 고른 목록 주소.
 */
@Service
public class MemberSpotlightService {
    private static final int LIMIT = 5;

    private final ObjektService objektService;
    private final ArchiveRepository archiveRepository;
    private final MediaService mediaService;
    private final ScheduleRepository scheduleRepository;

    public MemberSpotlightService(ObjektService objektService,
                                  ArchiveRepository archiveRepository,
                                  MediaService mediaService,
                                  ScheduleRepository scheduleRepository) {
        this.objektService = objektService;
        this.archiveRepository = archiveRepository;
        this.mediaService = mediaService;
        this.scheduleRepository = scheduleRepository;
    }

    @Transactional(readOnly = true)
    public void attach(List<Artist> artists) {
        if (artists == null || artists.isEmpty()) return;
        List<Map<String, Object>> objekts = loadObjekts();
        List<Map<String, Object>> newestObjekts = objekts.stream()
                .sorted(Comparator.comparing(
                        (Map<String, Object> objekt) -> text(objekt.get("createdAt")),
                        Comparator.reverseOrder()))
                .toList();
        List<String> objektMembers = objekts.stream()
                .map(objekt -> text(objekt.get("member")))
                .filter(value -> !value.isBlank())
                .distinct()
                .toList();
        // 전역 최신 묶음을 멤버별로 한 번만 인덱싱해 24회 선형 스캔을 피한다.
        Map<String, List<Archive>> imageIndex = indexArchives(
                archiveRepository.findTop2000ByFile_MediaTypeOrderByIdDesc(MediaType.IMAGE));
        Map<String, List<Archive>> videoIndex = indexArchives(
                archiveRepository.findTop2000ByFile_MediaTypeOrderByIdDesc(MediaType.VIDEO));
        Map<Long, List<MediaDto>> media = mediaService.byArtistMap(LIMIT);
        Map<String, List<Schedule>> scheduleIndex = indexSchedules(scheduleRepository.findAllWithArtists());
        for (Artist artist : artists) {
            if (artist == null || artist.getName() == null || artist.getName().isBlank()) continue;
            String name = artist.getName().trim();
            String encoded = URLEncoder.encode(name, StandardCharsets.UTF_8);
            List<Map<String, Object>> feeds = new ArrayList<>();
            String objektMember = objektFilterValue(objektMembers, artist);
            String objektQuery = URLEncoder.encode(objektMember, StandardCharsets.UTF_8);
            Map<String, Object> objektFeed = feed(
                    "오브젝트",
                    "이 멤버만 고른 오브젝트 최신 5개입니다.",
                    "이 멤버의 오브젝트가 없습니다.",
                    "/cosmo/objekt?member=" + objektQuery,
                    objektCards(newestObjekts, name, objektQuery));
            objektFeed.put("objektFeed", true);
            feeds.add(objektFeed);
            feeds.add(archiveFeed("사진 아카이브", "등록된 사진 최신 5개입니다.", "이 멤버의 사진이 없습니다.",
                    imageIndex, MediaType.IMAGE, name, "/archive/image_archive?members=" + encoded));
            feeds.add(archiveFeed("영상 아카이브", "등록된 영상 최신 5개입니다.", "이 멤버의 영상이 없습니다.",
                    videoIndex, MediaType.VIDEO, name, "/archive/video_archive?members=" + encoded));
            feeds.add(feed(
                    "공식 미디어",
                    "공식 미디어에서 이 멤버가 선택된 최신 5개입니다.",
                    "연결된 공식 미디어가 없습니다.",
                    "/media?artist=" + encoded,
                    mediaCards(media.getOrDefault(artist.getArtistId(), List.of()))));
            feeds.add(feed(
                    "일정",
                    "이 멤버만 고른 일정 5개입니다.",
                    "이 멤버의 일정이 없습니다.",
                    "/schedule?artist=" + encoded,
                    scheduleCards(scheduleIndex.getOrDefault(normalizeMemberKey(name), List.of()), encoded)));
            artist.setMemberFeeds(feeds);
            artist.setMediaContents(media.getOrDefault(artist.getArtistId(), List.of()));
        }
    }

    private Map<String, Object> archiveFeed(String title, String description, String emptyText,
                                            Map<String, List<Archive>> index, MediaType type,
                                            String name, String moreUrl) {
        List<Archive> matched = takeFromIndex(index, name);
        if (matched.size() < LIMIT) {
            // 전역 최신 2000 밖에만 있는 멤버 자료를 보완 조회
            List<Archive> fallback = archiveRepository
                    .findTop40ByFile_MediaTypeAndArtistNamesContainingIgnoreCaseOrderByIdDesc(type, name);
            for (Archive archive : fallback) {
                if (matched.size() >= LIMIT) break;
                if (!archiveHasMember(archive.getArtistNames(), name)) continue;
                if (matched.stream().anyMatch(a -> a.getId() != null && a.getId().equals(archive.getId()))) {
                    continue;
                }
                matched.add(archive);
            }
        }
        List<Map<String, Object>> cards = new ArrayList<>();
        for (Archive archive : matched) {
            if (cards.size() >= LIMIT) break;
            SavedMeta file = archive.getFile();
            String url = file == null || file.getUrl() == null ? "" : file.getUrl();
            boolean image = type == MediaType.IMAGE && !url.isBlank();
            String href = url.isBlank() ? moreUrl : url;
            cards.add(card(archive.getTitle(), href, !url.isBlank(), image ? url : "", archive.getArtistNames(), image));
        }
        return feed(title, description, emptyText, moreUrl, cards);
    }

    private static List<Archive> takeFromIndex(Map<String, List<Archive>> index, String name) {
        List<Archive> fromIndex = index.getOrDefault(normalizeMemberKey(name), List.of());
        List<Archive> matched = new ArrayList<>();
        for (Archive archive : fromIndex) {
            if (matched.size() >= LIMIT) break;
            matched.add(archive);
        }
        return matched;
    }

    private static Map<String, List<Archive>> indexArchives(List<Archive> archives) {
        Map<String, List<Archive>> index = new HashMap<>();
        if (archives == null) return index;
        for (Archive archive : archives) {
            for (String member : split(archive.getArtistNames())) {
                index.computeIfAbsent(normalizeMemberKey(member), k -> new ArrayList<>()).add(archive);
            }
        }
        return index;
    }

    private static Map<String, List<Schedule>> indexSchedules(List<Schedule> schedules) {
        Map<String, List<Schedule>> index = new HashMap<>();
        if (schedules == null) return index;
        for (Schedule schedule : schedules) {
            for (String member : split(schedule.getArtistNames())) {
                index.computeIfAbsent(normalizeMemberKey(member), k -> new ArrayList<>()).add(schedule);
            }
        }
        return index;
    }

    private List<Map<String, Object>> objektCards(List<Map<String, Object>> objekts, String name, String objektQuery) {
        List<Map<String, Object>> cards = new ArrayList<>(LIMIT);
        for (Map<String, Object> objekt : objekts) {
            if (!MemberPortraitOptions.objektIncludesMember(objektText(objekt), name)) continue;
            String id = text(objekt.get("id"));
            String href = id.isBlank()
                    ? "/cosmo/objekt?member=" + objektQuery
                    : "/cosmo/objekt/detail/" + URLEncoder.encode(id, StandardCharsets.UTF_8);
            String image = firstNonBlank(text(objekt.get("thumbnailImage")), text(objekt.get("frontImage")));
            String meta = joinMeta(text(objekt.get("season")), text(objekt.get("class")));
            cards.add(card(text(objekt.get("collectionId")), href, !image.isBlank(), image, meta, !image.isBlank()));
            if (cards.size() == LIMIT) break;
        }
        return cards;
    }

    private List<Map<String, Object>> mediaCards(List<MediaDto> media) {
        List<Map<String, Object>> cards = new ArrayList<>();
        for (MediaDto item : media) {
            String image = item.getYoutubeId() == null || item.getYoutubeId().isBlank()
                    ? ""
                    : "https://img.youtube.com/vi/" + item.getYoutubeId() + "/hqdefault.jpg";
            String meta = joinMeta("공식 미디어", item.getTabLabel(), item.getViewCountFmt() == null ? "" : "조회수 " + item.getViewCountFmt());
            cards.add(card(item.getTitle(), "/media/" + item.normalizedTab() + "/" + item.getYoutubeId(),
                    !image.isBlank(), image, meta, !image.isBlank()));
        }
        return cards;
    }

    private List<Map<String, Object>> scheduleCards(List<Schedule> schedules, String encoded) {
        return schedules.stream()
                .sorted(Comparator.comparing(Schedule::getStart, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(LIMIT)
                .map(schedule -> {
                    String when = schedule.getStart() == null ? "" : schedule.getStart().toString();
                    if (schedule.getEnd() != null && schedule.getStart() != null && !schedule.getEnd().equals(schedule.getStart())) {
                        when = schedule.getStart() + " – " + schedule.getEnd();
                    }
                    String href = "/schedule?artist=" + encoded + "&focus=" + schedule.getId();
                    Map<String, Object> item = card(schedule.getTitle(), href, false, "", when, false);
                    item.put("icon", "calendar_month");
                    return item;
                })
                .toList();
    }

    private List<Map<String, Object>> loadObjekts() {
        try {
            List<Map<String, Object>> found = objektService.findCollections("triples");
            return found == null ? List.of() : found;
        } catch (RuntimeException ex) {
            return List.of();
        }
    }

    private static Map<String, Object> feed(String title, String description, String emptyText,
                                            String moreUrl, List<Map<String, Object>> items) {
        Map<String, Object> feed = new LinkedHashMap<>();
        feed.put("title", title);
        feed.put("description", description);
        feed.put("emptyText", emptyText);
        feed.put("moreUrl", moreUrl);
        feed.put("items", items);
        feed.put("hasItems", !items.isEmpty());
        return feed;
    }

    private static Map<String, Object> card(String title, String href, boolean external, String image, String meta, boolean hasImage) {
        Map<String, Object> card = new LinkedHashMap<>();
        card.put("title", title == null || title.isBlank() ? "제목 없음" : title);
        card.put("href", href);
        card.put("external", external);
        card.put("image", image == null ? "" : image);
        card.put("hasImage", hasImage);
        card.put("meta", meta == null ? "" : meta);
        card.put("hasMeta", meta != null && !meta.isBlank());
        card.put("icon", "play_arrow");
        return card;
    }

    private static String objektFilterValue(List<String> options, Artist artist) {
        String name = artist.getName() == null ? "" : artist.getName().trim();
        String eng = artist.getEngName() == null ? "" : artist.getEngName().trim();
        for (String option : options) {
            if (option.equals(name) || option.equalsIgnoreCase(eng)) return option;
        }
        for (String option : options) {
            if (MemberPortraitOptions.objektIncludesMember(option, name)) return option;
        }
        return name;
    }

    private static String objektText(Map<String, Object> objekt) {
        return String.join(" ",
                text(objekt.get("member")),
                text(objekt.get("memberName")),
                text(objekt.get("collectionId")),
                text(objekt.get("slug")),
                text(objekt.get("artist")),
                text(objekt.get("searchText")));
    }

    private static boolean archiveHasMember(String artistNames, String name) {
        String needle = "," + normalizeMemberKey(name) + ",";
        String normalized = (artistNames == null ? "" : artistNames).toLowerCase(Locale.ROOT).replace(" ", "");
        return ("," + normalized + ",").contains(needle);
    }

    private static String normalizeMemberKey(String name) {
        return (name == null ? "" : name).toLowerCase(Locale.ROOT).replace(" ", "");
    }

    private static List<String> split(String names) {
        if (names == null || names.isBlank()) return List.of();
        List<String> parts = new ArrayList<>();
        for (String part : names.split(",")) {
            String trimmed = part.trim();
            if (!trimmed.isBlank()) parts.add(trimmed);
        }
        return parts;
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private static String firstNonBlank(String first, String second) {
        return first == null || first.isBlank() ? (second == null ? "" : second) : first;
    }

    private static String joinMeta(String... parts) {
        List<String> kept = new ArrayList<>();
        for (String part : parts) {
            if (part != null && !part.isBlank()) kept.add(part);
        }
        return String.join(" · ", kept);
    }
}
