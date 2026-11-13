package com.example.triplesweb.service;

import com.example.triplesweb.dto.ChemistryDto;
import com.example.triplesweb.dto.EventListCard;
import com.example.triplesweb.dto.EventSectionGroupDto;
import com.example.triplesweb.dto.MediaDto;
import com.example.triplesweb.dto.ShopListCard;
import com.example.triplesweb.entity.Album;
import com.example.triplesweb.entity.Archive;
import com.example.triplesweb.entity.Article;
import com.example.triplesweb.entity.Artist;
import com.example.triplesweb.entity.MediaType;
import com.example.triplesweb.entity.Schedule;
import com.example.triplesweb.entity.Unit;
import com.example.triplesweb.media.MediaTab;
import com.example.triplesweb.util.ObjektLinks;
import com.example.triplesweb.util.ObjektSeasonCodes;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 헤더 전역 검색. 멤버와 사이트 각 분류의 검색 결과를 한 페이지에 모은다.
 */
@Service
@RequiredArgsConstructor
public class SiteSearchService {

    public static final int LIMIT = 8;
    static final int OBJEKT_LIMIT = 5;
    static final int INFO_SCHEDULE_LIMIT = 5;
    static final List<String> TAB_IDS = List.of("cosmo", "media", "info", "schedule");
    private static final Pattern DATE = Pattern.compile("(?<!\\d)(\\d{4})[.\\-/](\\d{1,2})[.\\-/](\\d{1,2})(?!\\d)");

    private final ArtistSnapshotService artists;
    private final AlbumService albums;
    private final UnitService units;
    private final ChemistryService chemistry;
    private final ObjektService objekts;
    private final GravityService gravity;
    private final ArticleService articles;
    private final ArchiveService archives;
    private final MediaService media;
    private final EventService events;
    private final ShopService shop;
    private final ScheduleService schedules;

    public Page search(String keyword, String tab) {
        String query = keyword == null ? "" : keyword.trim();
        String active = normalizeTab(tab, query);
        if (query.isEmpty()) {
            return emptyPage(query, active);
        }
        List<Member> members = memberResults(query);
        CompletableFuture<List<Hit>> about = CompletableFuture.supplyAsync(() -> aboutHits(query));
        CompletableFuture<CosmoResults> cosmo = CompletableFuture.supplyAsync(() -> cosmoHits(query));
        CompletableFuture<List<Hit>> media = CompletableFuture.supplyAsync(() -> mediaHits(query));
        CompletableFuture<List<Hit>> info = CompletableFuture.supplyAsync(() -> infoHits(query));
        CompletableFuture<List<Hit>> schedule = CompletableFuture.supplyAsync(() -> scheduleHits(query));
        List<Hit> aboutResults = about.join();
        CosmoResults cosmoResults = cosmo.join();
        List<Tab> tabs = new ArrayList<>();
        if (!aboutResults.isEmpty()) {
            tabs.add(tabOf("about", "About 트리플에스", "", aboutResults, query));
        } else if ("about".equals(active)) {
            active = "cosmo";
        }
        tabs.add(tabOf("cosmo", "코스모", cosmoResults.hasMoreObjekts()
                ? "/cosmo/objekt?search=" + encode(query) : "", cosmoResults.hits(), query));
        tabs.add(tabOf("media", "미디어", "", media.join(), query));
        tabs.add(tabOf("info", "정보", "", info.join(), query));
        tabs.add(tabOf("schedule", "일정", "", schedule.join(), query));
        String activeTab = active;
        List<Tab> marked = tabs.stream().map(item -> item.withActive(item.id.equals(activeTab))).toList();
        return new Page(query, active, marked, members);
    }

    private Tab tabOf(String id, String label, String moreHref, List<Hit> hits, String query) {
        return new Tab(id, label, "/search?keyword=" + encode(query) + "&tab=" + id, moreHref, hits, false);
    }

    private Page emptyPage(String query, String active) {
        List<Tab> tabs = TAB_IDS.stream()
                .map(id -> new Tab(id, labelOf(id), "/search?tab=" + id, moreOf(id, query), List.of(), id.equals(active)))
                .toList();
        return new Page(query, active, tabs, List.of());
    }

    private static String labelOf(String id) {
        return switch (id) {
            case "cosmo" -> "코스모";
            case "media" -> "미디어";
            case "info" -> "정보";
            case "schedule" -> "일정";
            default -> "코스모";
        };
    }

    private static String moreOf(String id, String query) {
        String encoded = encode(query);
        return switch (id) {
            case "cosmo" -> "/cosmo/objekt?search=" + encoded;
            default -> "";
        };
    }

    private List<Member> memberResults(String query) {
        List<Artist> matched = new ArrayList<>();
        if (query.codePointCount(0, query.length()) < 2) return List.of();
        for (Artist artist : safeList(() -> artists.findAll())) {
            if (contains(artist.getName(), query) || contains(artist.getEngName(), query)
                    || contains(artist.getRealName(), query)) {
                matched.add(artist);
            }
        }
        matched.sort((a, b) -> Boolean.compare(exactName(b, query), exactName(a, query)));
        List<Member> members = new ArrayList<>(matched.size());
        for (Artist artist : matched) {
            members.add(new Member(
                    blankTo(artist.getName(), artist.getEngName()),
                    blankTo(artist.getEngName(), ""),
                    artist.getDisplayProfileImage(),
                    "/triples/artist?tab=member-" + artist.getArtistId()));
        }
        return members;
    }

    private static boolean exactName(Artist artist, String query) {
        return query != null && (query.equalsIgnoreCase(blankTo(artist.getName(), ""))
                || query.equalsIgnoreCase(blankTo(artist.getEngName(), ""))
                || query.equalsIgnoreCase(blankTo(artist.getRealName(), "")));
    }

    /** 케미·앨범·유닛 이름만 대상으로 하여 멤버 이름만 일치할 때는 About을 표시하지 않는다. */
    private List<Hit> aboutHits(String query) {
        List<Hit> hits = new ArrayList<>();
        for (Album album : safeList(albums::listAll)) {
            if (contains(album.getAlbumName(), query)) {
                hits.add(hit(album.getAlbumName(), "/triples/album/" + album.getAlbumId(), "앨범",
                        blankTo(album.getReleaseDate(), "앨범")));
                if (hits.size() >= LIMIT) return hits;
            }
        }
        for (Unit unit : safeList(units::listVisible)) {
            if (contains(unit.getUnitName(), query) || contains(unit.getSmallName(), query)) {
                hits.add(hit(unit.getUnitName(), "/triples/unit?tab=unit-" + unit.getUnitId(), "유닛",
                        blankTo(unit.getSmallName(), "디멘션")));
                if (hits.size() >= LIMIT) return hits;
            }
        }
        for (ChemistryDto item : safeList(chemistry::findAll)) {
            if (contains(item.getLabel(), query) || contains(item.getLabelEn(), query)) {
                String pair = (blankTo(item.getMemberA(), "") + " · " + blankTo(item.getMemberB(), "")).trim();
                String href = item.getId() == null ? "/triples/chemistry" : "/triples/chemistry?pair=" + item.getId();
                hits.add(hit(blankTo(item.getLabel(), item.getLabelEn()), href, "케미", pair));
                if (hits.size() >= LIMIT) return hits;
            }
        }
        return hits;
    }

    private record CosmoResults(List<Hit> hits, boolean hasMoreObjekts) {}

    private CosmoResults cosmoHits(String query) {
        List<Hit> hits = new ArrayList<>();
        // 멤버 상세의 오브젝트 목록과 동일하게 생성 시각이 최신인 항목부터 표시한다.
        List<Map<String, Object>> matchingObjekts = safeList(() -> objekts.findCollections("triples")).stream()
                .filter(objekt -> objektMatches(query, objekt))
                .sorted(Comparator.comparing((Map<String, Object> item) -> first(item, "createdAt"),
                        Comparator.reverseOrder()))
                .limit(OBJEKT_LIMIT)
                .toList();
        for (Map<String, Object> objekt : matchingObjekts) {
            String member = first(objekt, "member", "memberName");
            String number = first(objekt, "collectionNo", "collectionNumber");
            String season = first(objekt, "season", "seasonKey");
            String label = (member + " " + number).trim();
            if (label.isBlank()) label = first(objekt, "collectionId", "searchText", "slug");
            hits.add(new Hit(blankTo(label, "오브젝트"), ObjektLinks.detail(objekt), "오브젝트", season,
                    true, first(objekt, "thumbnailImage", "frontImage")));
        }
        boolean hasMoreObjekts = matchingObjekts.size() >= OBJEKT_LIMIT;
        for (Hit gravityHit : gravityHits(query)) {
            hits.add(gravityHit);
            if (hits.size() >= LIMIT) return new CosmoResults(hits, hasMoreObjekts);
        }
        for (String category : List.of("trade", "sell", "group-orders")) {
            String kind = switch (category) {
                case "sell" -> "거래";
                case "group-orders" -> "분철";
                default -> "교환";
            };
            for (Article article : articleHits(category, query)) {
                hits.add(hit(article.getTitle(), "/article/" + category + "/" + article.getId(), kind,
                        blankTo(article.getUserId(), kind)));
                if (hits.size() >= LIMIT) return new CosmoResults(hits, hasMoreObjekts);
            }
        }
        return new CosmoResults(hits, hasMoreObjekts);
    }

    private List<Hit> gravityHits(String query) {
        if (!gravity.hasCosmoToken()) return List.of();
        try {
            List<Hit> hits = new ArrayList<>();
            for (Map<String, Object> item : gravity.fetchGravityList("tripleS")) {
                String title = first(item, "title");
                if (!contains(title, query)) continue;
                String href = first(item, "detailUrl");
                if (href.isBlank() && item.get("id") != null) href = "/cosmo/gravity/" + item.get("id");
                hits.add(hit(title, href.isBlank() ? "/cosmo/gravity" : href, "그래비티",
                        first(item, "statusLabel")));
                if (hits.size() >= LIMIT) break;
            }
            return hits;
        } catch (RuntimeException ignored) {
            return List.of();
        }
    }

    private record DatedHit(Hit hit, LocalDateTime date) {}

    private static List<Hit> newest(List<DatedHit> hits, int limit) {
        return hits.stream()
                .sorted(Comparator.comparing(DatedHit::date, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(limit).map(DatedHit::hit).toList();
    }

    private List<Hit> mediaHits(String query) {
        List<DatedHit> hits = new ArrayList<>();
        for (Archive archive : archiveHits(MediaType.IMAGE, query)) {
            hits.add(new DatedHit(hit(archive.getTitle(), "/archive/image_archive?q=" + encode(archive.getTitle()), "사진",
                    blankTo(archive.getArtistNames(), "사진 아카이브")), dateTime(archive.getRegisteredOn())));
        }
        for (Archive archive : archiveHits(MediaType.VIDEO, query)) {
            hits.add(new DatedHit(hit(archive.getTitle(), "/archive/video_archive?q=" + encode(archive.getTitle()), "영상",
                    blankTo(archive.getArtistNames(), "영상 아카이브")), dateTime(archive.getRegisteredOn())));
        }
        for (MediaTab tab : MediaTab.values()) {
            for (MediaDto item : safeList(() -> media.allByTab(tab.getKey()))) {
                if (!contains(item.getTitle(), query) && !contains(item.getArtistNames(), query)) continue;
                hits.add(new DatedHit(hit(blankTo(item.getTitle(), item.getYoutubeId()),
                        "/media/" + item.normalizedTab() + "/" + item.getYoutubeId(),
                        tab.getKor(), blankTo(item.getArtistNames(), tab.getKor())), firstDate(item.getPublishedOn())));
            }
        }
        return newest(hits, LIMIT);
    }

    private List<Archive> archiveHits(MediaType type, String query) {
        try {
            return archives.searchLatestByType(type, query, LIMIT);
        } catch (RuntimeException ignored) {
            return List.of();
        }
    }

    private List<Hit> infoHits(String query) {
        List<DatedHit> hits = new ArrayList<>();
        for (EventSectionGroupDto group : safeList(events::listGroupedBySection)) {
            List<EventListCard> cards = group.getEvents() == null ? List.of() : group.getEvents();
            for (EventListCard event : cards) {
                if (!contains(event.getEventName(), query) && !contains(event.getRegionLabel(), query)) continue;
                hits.add(new DatedHit(hit(event.getEventName(), "/cosmo/event/" + event.getEventId(), "공연/이벤트",
                        blankTo(event.getEventDate(), group.getName())), firstDate(event.getEventDate())));
            }
        }
        for (ShopListCard card : safeList(shop::listCards)) {
            if (!contains(card.getEventName(), query)) continue;
            hits.add(new DatedHit(hit(card.getEventName(), "/shop/" + card.getId(), "굿즈",
                    blankTo(card.getSalesPeriod(), "굿즈")), firstDate(card.getSalesPeriod())));
        }
        for (Article article : latestArticleHits("info", query)) {
            hits.add(new DatedHit(hit(article.getTitle(), "/article/info/" + article.getId(), "기사/그외",
                    blankTo(article.getUserId(), "정보")), article.getFormattedCreatedAt()));
        }
        for (Article article : latestArticleHits("notice", query)) {
            hits.add(new DatedHit(hit(article.getTitle(), "/article/notice/" + article.getId(), "공지",
                    blankTo(article.getFormattedDate(), "공지")), article.getFormattedCreatedAt()));
        }
        return newest(hits, INFO_SCHEDULE_LIMIT);
    }

    private List<Hit> scheduleHits(String query) {
        List<DatedHit> hits = new ArrayList<>();
        for (Schedule schedule : safeList(schedules::findAll)) {
            String names = schedule.getArtistNames();
            if (!contains(schedule.getTitle(), query) && !contains(names, query)) continue;
            hits.add(new DatedHit(hit(blankTo(schedule.getTitle(), "일정"), "/schedule", "일정",
                    blankTo(names, dateRange(schedule))), dateTime(schedule.getStart())));
        }
        return newest(hits, INFO_SCHEDULE_LIMIT);
    }

    private List<Article> latestArticleHits(String category, String query) {
        return safeList(() -> articles.searchLatestByCategory(category, query, INFO_SCHEDULE_LIMIT));
    }

    private static LocalDateTime dateTime(LocalDate date) {
        return date == null ? null : date.atStartOfDay();
    }

    // 이벤트와 굿즈의 자유 형식 기간에서 시작일을 추출한다. 알 수 없는 날짜는 마지막에 둔다.
    private static LocalDateTime firstDate(String value) {
        if (value == null) return null;
        Matcher match = DATE.matcher(value);
        if (!match.find()) return null;
        try {
            return LocalDate.of(Integer.parseInt(match.group(1)), Integer.parseInt(match.group(2)),
                    Integer.parseInt(match.group(3))).atStartOfDay();
        } catch (DateTimeException ex) {
            return null;
        }
    }

    private List<Article> articleHits(String category, String query) {
        try {
            return articles.searchByCategory(category, "title", query, LIMIT);
        } catch (RuntimeException ignored) {
            return List.of();
        }
    }

    static boolean objektMatches(String query, Map<String, Object> objekt) {
        if (query == null || query.isBlank() || objekt == null) return false;
        String season = first(objekt, "season", "seasonKey");
        String collectionNo = first(objekt, "collectionNo", "collectionNumber");
        String base = String.join(" ",
                first(objekt, "searchText"), first(objekt, "collectionId"), first(objekt, "slug"),
                first(objekt, "season"), first(objekt, "seasonKey"), first(objekt, "member"),
                first(objekt, "memberName"), first(objekt, "artist"), first(objekt, "collectionNo"),
                first(objekt, "class"), first(objekt, "classKey"), first(objekt, "numberValue"))
                .toLowerCase(Locale.ROOT);
        return ObjektSeasonCodes.matches(query, base, season, collectionNo);
    }

    private static String normalizeTab(String tab, String query) {
        String requested = tab == null ? "" : tab.trim().toLowerCase(Locale.ROOT);
        if (("about".equals(requested) && query != null && !query.isBlank()) || TAB_IDS.contains(requested)) return requested;
        return "cosmo";
    }

    private static Hit hit(String label, String href, String kind, String snippet) {
        return new Hit(blankTo(label, kind), href == null ? "#" : href, kind, snippet == null ? "" : snippet);
    }

    private static String encode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static boolean contains(String value, String query) {
        return value != null && query != null && !query.isBlank()
                && value.toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT));
    }

    private static String first(Map<String, Object> item, String... keys) {
        if (item == null) return "";
        for (String key : keys) {
            Object value = item.get(key);
            if (value != null && !value.toString().isBlank() && !"null".equalsIgnoreCase(value.toString())) {
                return value.toString();
            }
        }
        return "";
    }

    private static String blankTo(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static String dateRange(Schedule schedule) {
        if (schedule.getStart() == null) return "";
        if (schedule.getEnd() == null || schedule.getStart().equals(schedule.getEnd())) {
            return schedule.getStart().toString();
        }
        return schedule.getStart() + " ~ " + schedule.getEnd();
    }

    private static <T> List<T> safeList(SupplierList<T> supplier) {
        try {
            List<T> value = supplier.get();
            return value == null ? List.of() : value;
        } catch (RuntimeException ignored) {
            return List.of();
        }
    }

    @FunctionalInterface
    private interface SupplierList<T> {
        List<T> get();
    }

    @Getter
    public static final class Page {
        private final String keyword;
        private final String activeTab;
        private final List<Tab> tabs;
        private final List<Member> members;

        public Page(String keyword, String activeTab, List<Tab> tabs) {
            this(keyword, activeTab, tabs, List.of());
        }

        public Page(String keyword, String activeTab, List<Tab> tabs, List<Member> members) {
            this.keyword = keyword;
            this.activeTab = activeTab;
            this.tabs = tabs;
            this.members = members == null ? List.of() : List.copyOf(members);
        }

        public boolean isHasAny() {
            return !members.isEmpty() || tabs.stream().anyMatch(Tab::isHasHits);
        }

        public boolean isHasMembers() {
            return !members.isEmpty();
        }
    }

    @Getter
    public static final class Tab {
        private final String id;
        private final String label;
        private final String href;
        private final String moreHref;
        private final List<Hit> hits;
        private final boolean active;

        public Tab(String id, String label, String href, String moreHref, List<Hit> hits, boolean active) {
            this.id = id;
            this.label = label;
            this.href = href;
            this.moreHref = moreHref;
            this.hits = hits == null ? List.of() : List.copyOf(hits);
            this.active = active;
        }

        public int getCount() {
            return hits.size();
        }

        public boolean isHasHits() {
            return !hits.isEmpty();
        }

        public boolean isActive() {
            return active;
        }

        public boolean isHasMore() {
            return moreHref != null && !moreHref.isBlank();
        }

        Tab withActive(boolean value) {
            return new Tab(id, label, href, moreHref, hits, value);
        }
    }

    @Getter
    public static final class Member {
        private final String name;
        private final String engName;
        private final String profileImage;
        private final String profileUrl;

        public Member(String name, String engName, String profileImage, String profileUrl) {
            this.name = name;
            this.engName = engName == null ? "" : engName;
            this.profileImage = profileImage == null ? "" : profileImage;
            this.profileUrl = profileUrl == null ? "#" : profileUrl;
        }
    }

    @Getter
    public static final class Hit {
        private final String label;
        private final String href;
        private final String kind;
        private final String snippet;
        private final boolean objekt;
        private final String imageUrl;

        public Hit(String label, String href, String kind, String snippet) {
            this(label, href, kind, snippet, false, "");
        }

        public Hit(String label, String href, String kind, String snippet, boolean objekt, String imageUrl) {
            this.label = label;
            this.href = href;
            this.kind = kind;
            this.snippet = snippet;
            this.objekt = objekt;
            this.imageUrl = imageUrl == null ? "" : imageUrl;
        }

        public boolean isHasImage() {
            return !imageUrl.isBlank();
        }
    }
}
