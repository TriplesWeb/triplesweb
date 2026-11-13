package com.example.triplesweb.service;

import com.example.triplesweb.dto.ObjektCatalogDto;
import com.example.triplesweb.entity.ObjektCatalog;
import com.example.triplesweb.infra.GzipRestClients;
import com.example.triplesweb.util.MemberPortraitOptions;
import com.example.triplesweb.util.ObjektFilterOrder;
import com.example.triplesweb.util.ObjektLinks;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Objekt 외부 API 어댑터.
 *
 * 페이지 요청과 외부 API 호출을 분리하고 메모리 캐시를 사용한다.
 * 갱신 실패 또는 잘못된 응답이 발생해도 기존 캐시를 유지한다.
 */
@Slf4j
@Service
public class ObjektService {

    private final RestClient restClient;
    private final ObjektCatalogStore catalogStore;
    private final ObjektLiveSettings live;

    public static final int DROP_ALERT_LIMIT = 8;

    private volatile List<Map<String, Object>> cachedCollections = List.of();
    private volatile Map<String, Map<String, Object>> byKey = Map.of();
    private volatile Map<String, Integer> groupCounts = Map.of();
    private volatile Map<String, List<Map<String, Object>>> groupsBySeasonNumber = Map.of();
    private volatile Map<String, List<String>> numbersBySeason = Map.of();
    private volatile FilterFacets facets = FilterFacets.empty();
    private volatile List<Map<String, Object>> newestDrops = List.of();
    private volatile long lastSuccessfulRefreshMillis;
    private volatile String catalogSignature = "0:0";
    private final AtomicBoolean refreshing = new AtomicBoolean(false);

    public ObjektService(
            @Value("${objekt.api.base-url:https://objekt.top/api/v1}")
            String baseUrl,
            @Value("${objekt.cache.read-timeout-ms:30000}")
            long readTimeoutMs,
            ObjektCatalogStore catalogStore,
            ObjektLiveSettings live
    ) {
        this.catalogStore = catalogStore;
        this.live = live;
        // 카탈로그는 gzip 기준 ~1MB·수 초가 걸린다. 비압축이면 10MB+/30초를 넘긴다.
        this.restClient = GzipRestClients.create(
                baseUrl,
                Duration.ofSeconds(3),
                Duration.ofMillis(Math.max(1_000, readTimeoutMs)));
    }

    /**
     * 페이지 요청에서는 외부 API를 호출하지 않는다.
     * 최초 캐시 적재 전에는 빈 목록을 즉시 반환한다.
     */
    @PostConstruct
    void loadStoredCatalog() {
        catalogStore.retainOnly("triples");
        install(loadFromDatabase("triples"));
    }

    public List<Map<String, Object>> findCollections(String artist) {
        List<Map<String, Object>> snapshot = cachedCollections;
        if (snapshot == null) {
            snapshot = loadFromDatabase(artist);
            install(snapshot);
        }
        return snapshot;
    }

    /** O(1) lookup by id, collectionId, slug, or ObjektLinks.key. */
    public Map<String, Object> findByKey(String key) {
        if (key == null || key.isBlank()) return null;
        Map<String, Map<String, Object>> index = byKey;
        Map<String, Object> found = index.get(key);
        if (found != null) return found;
        return index.get(key.toLowerCase(Locale.ROOT));
    }

    public FilterFacets filterFacets() {
        return facets;
    }

    public int catalogCount() {
        return cachedCollections.size();
    }

    /** Same season+number cards, from the in-memory catalog index. */
    public int groupCount(Map<String, Object> item) {
        if (item == null) return 1;
        String season = first(item, "season", "seasonKey").toLowerCase(Locale.ROOT);
        String number = first(item, "collectionNo", "collectionNumber").toLowerCase(Locale.ROOT);
        if (number.isBlank()) return 1;
        Integer count = groupCounts.get(season + "|" + number);
        return count == null || count < 1 ? 1 : count;
    }

    /** Same season+number cards. Empty when the pair is unknown. */
    public List<Map<String, Object>> groupCards(String season, String collectionNo) {
        if (season == null || collectionNo == null) return List.of();
        String key = season.trim().toLowerCase(Locale.ROOT) + "|" + collectionNo.trim().toLowerCase(Locale.ROOT);
        if (key.startsWith("|") || key.endsWith("|")) return List.of();
        return groupsBySeasonNumber.getOrDefault(key, List.of());
    }

    /** Distinct collection numbers in a season, case-insensitive sorted. */
    public List<String> seasonCollectionNumbers(String season) {
        if (season == null || season.isBlank()) return List.of();
        return numbersBySeason.getOrDefault(season.trim().toLowerCase(Locale.ROOT), List.of());
    }

    public String catalogSignature() {
        return catalogSignature;
    }

    /** 시즌+번호 드롭 알림. install() 때 미리 계산한다. */
    public List<Map<String, Object>> newestDrops() {
        return newestDrops;
    }

    /**
     * 평소 1분, 드롭 창(10시–자정)에는 더 자주 외부 카탈로그를 본다.
     * 화면용 스냅샷이 같으면 DB를 다시 쓰지 않는다.
     */
    @Scheduled(
            fixedDelayString = "${objekt.live.tick-ms:15000}",
            initialDelayString = "${objekt.cache.initial-delay-ms:0}"
    )
    void refreshCacheIfDue() {
        if (live != null && !live.due(lastSuccessfulRefreshMillis)) return;
        refreshCache();
    }

    public void refreshCache() {
        if (!refreshing.compareAndSet(false, true)) return;
        try {
            JsonNode response = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/collections")
                            .queryParam("artist", "triples")
                            .build())
                    .header("Accept", "application/json")
                    .retrieve()
                    .body(JsonNode.class);

            if (response == null || !response.path("collections").isArray()) {
                log.warn("Objekt API 응답 형식이 올바르지 않아 기존 캐시를 유지합니다.");
                return;
            }

            JsonNode collections = response.path("collections");

            // 이미 정상 데이터가 존재하는데 빈 응답이 오면 기존 데이터를 보호한다.
            if (collections.isEmpty()
                    && cachedCollections != null
                    && !cachedCollections.isEmpty()) {
                log.warn("Objekt API가 빈 컬렉션을 반환했습니다. 기존 캐시를 유지합니다.");
                return;
            }

            Map<String, ObjektCatalog> unique = new LinkedHashMap<>();
            for (JsonNode node : collections) {
                ObjektCatalogDto dto = ObjektCatalogDto.from(node);
                if (dto.collectionId() == null || dto.collectionId().isBlank()
                        || unique.putIfAbsent(dto.collectionId(), dto.toEntity()) != null) {
                    log.warn("Objekt 카탈로그에 누락 또는 중복 식별자가 있어 기존 스냅샷을 유지합니다.");
                    return;
                }
            }
            List<ObjektCatalog> rows = new ArrayList<>(unique.values());
            List<Map<String, Object>> views = rows.stream().map(ObjektCatalogDto::toView).toList();
            synchronized (this) {
                if (views.equals(cachedCollections) && !cachedCollections.isEmpty()) {
                    lastSuccessfulRefreshMillis = System.currentTimeMillis();
                    return;
                }
                // DB는 다음 시작 시 복구할 스냅샷으로만 저장한다. 갱신 직후 다시
                // SELECT 하지 않고, 이미 받은 API 응답으로 메모리 캐시를 교체한다.
                catalogStore.replace("triples", rows);
                install(views);
                lastSuccessfulRefreshMillis = System.currentTimeMillis();
            }
            log.info("Objekt 카탈로그 저장 완료: {}개", cachedCollections.size());

        } catch (RuntimeException ex) {
            // 최초 적재 실패를 포함해 페이지 요청으로 예외를 전파하지 않는다.
            // 캐시가 이미 있다면 기존 데이터를 그대로 유지한다.
            log.warn(
                    "Objekt 캐시 갱신 실패. 기존 캐시를 유지하고 다음 주기에 재시도합니다: {}",
                    detail(ex)
            );
        } finally {
            refreshing.set(false);
        }
    }

    public long getLastSuccessfulRefreshMillis() {
        return lastSuccessfulRefreshMillis;
    }

    private void install(List<Map<String, Object>> views) {
        List<Map<String, Object>> snapshot = views == null ? List.of() : List.copyOf(views);
        cachedCollections = snapshot;
        byKey = buildIndex(snapshot);
        GroupIndex groupIndex = buildGroupIndex(snapshot);
        groupCounts = groupIndex.counts();
        groupsBySeasonNumber = groupIndex.cards();
        numbersBySeason = groupIndex.numbersBySeason();
        facets = FilterFacets.from(snapshot);
        catalogSignature = signature(snapshot);
        newestDrops = dropAlerts(snapshot);
    }

    static Map<String, Integer> buildGroupCounts(List<Map<String, Object>> views) {
        return buildGroupIndex(views).counts();
    }

    private record GroupIndex(
            Map<String, Integer> counts,
            Map<String, List<Map<String, Object>>> cards,
            Map<String, List<String>> numbersBySeason
    ) {}

    static GroupIndex buildGroupIndex(List<Map<String, Object>> views) {
        if (views == null || views.isEmpty()) {
            return new GroupIndex(Map.of(), Map.of(), Map.of());
        }
        Map<String, Integer> counts = new HashMap<>();
        Map<String, List<Map<String, Object>>> cards = new LinkedHashMap<>();
        Map<String, LinkedHashSet<String>> numbers = new LinkedHashMap<>();
        for (Map<String, Object> item : views) {
            String season = first(item, "season", "seasonKey");
            String number = first(item, "collectionNo", "collectionNumber");
            if (number.isBlank()) continue;
            String seasonKey = season.toLowerCase(Locale.ROOT);
            String numberKey = number.toLowerCase(Locale.ROOT);
            String pair = seasonKey + "|" + numberKey;
            counts.merge(pair, 1, Integer::sum);
            cards.computeIfAbsent(pair, ignored -> new ArrayList<>()).add(item);
            if (!seasonKey.isBlank()) {
                numbers.computeIfAbsent(seasonKey, ignored -> new LinkedHashSet<>()).add(number);
            }
        }
        Map<String, List<Map<String, Object>>> frozenCards = new HashMap<>();
        for (Map.Entry<String, List<Map<String, Object>>> entry : cards.entrySet()) {
            frozenCards.put(entry.getKey(), List.copyOf(entry.getValue()));
        }
        Map<String, List<String>> frozenNumbers = new HashMap<>();
        for (Map.Entry<String, LinkedHashSet<String>> entry : numbers.entrySet()) {
            List<String> sorted = new ArrayList<>(entry.getValue());
            sorted.sort(String.CASE_INSENSITIVE_ORDER);
            frozenNumbers.put(entry.getKey(), List.copyOf(sorted));
        }
        return new GroupIndex(Map.copyOf(counts), Map.copyOf(frozenCards), Map.copyOf(frozenNumbers));
    }

    /**
     * 같은 시즌+번호를 한 줄로 묶고, 첫 멤버 민팅 시각을 드롭 시각으로 쓴다.
     * 최신 그룹 최대 {@link #DROP_ALERT_LIMIT}개.
     */
    static List<Map<String, Object>> dropAlerts(List<Map<String, Object>> views) {
        if (views == null || views.isEmpty()) return List.of();
        Map<String, DropGroup> groups = new LinkedHashMap<>();
        for (Map<String, Object> item : views) {
            String season = first(item, "season", "seasonKey");
            String number = first(item, "collectionNo", "collectionNumber");
            if (number.isBlank()) continue;
            String key = season.toLowerCase(Locale.ROOT) + "|" + number.toLowerCase(Locale.ROOT);
            String at = createdAt(item);
            DropGroup existing = groups.get(key);
            if (existing == null || compareCreated(at, existing.createdAt) < 0) {
                groups.put(key, new DropGroup(season, number, at, item));
            }
        }
        return groups.values().stream()
                .sorted(Comparator.comparing((DropGroup group) -> group.createdAt,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(DROP_ALERT_LIMIT)
                .map(DropGroup::toRow)
                .toList();
    }

    private record DropGroup(String season, String number, String createdAt, Map<String, Object> item) {
        Map<String, Object> toRow() {
            Map<String, Object> row = new LinkedHashMap<>();
            String label = (season == null || season.isBlank() ? number : season + " " + number) + " 드롭";
            row.put("label", label);
            String seasonFilter = first(item, "seasonKey", "season");
            row.put("href", "/cosmo/objekt?"
                    + (seasonFilter.isBlank() ? "" : "season=" + encode(seasonFilter) + "&")
                    + "kind=" + encode(number));
            row.put("collectionId", first(item, "collectionId", "slug"));
            row.put("createdAt", createdAt == null ? "" : createdAt);
            return row;
        }
    }

    private static String createdAt(Map<String, Object> item) {
        for (String key : List.of("createdAt", "created_at", "date")) {
            Object value = item.get(key);
            if (value != null && !value.toString().isBlank() && !"null".equalsIgnoreCase(value.toString())) {
                return value.toString();
            }
        }
        return null;
    }

    private static int compareCreated(String left, String right) {
        if (left == null && right == null) return 0;
        if (left == null) return 1;
        if (right == null) return -1;
        return left.compareTo(right);
    }

    private static String first(Map<String, Object> item, String... keys) {
        if (item == null) return "";
        for (String key : keys) {
            Object value = item.get(key);
            if (value != null && !value.toString().isBlank() && !"null".equalsIgnoreCase(value.toString())) {
                return value.toString().trim();
            }
        }
        return "";
    }

    private static String encode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    static String signature(List<Map<String, Object>> views) {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
        updateDigest(digest, Integer.toString(views.size()));
        for (Map<String, Object> item : views) {
            // 맵의 반복 순서와 관계없이 화면에 제공하는 모든 필드의 변경을 반영한다.
            Map<String, Object> ordered = new TreeMap<>(item);
            updateDigest(digest, Integer.toString(ordered.size()));
            ordered.forEach((key, value) -> {
                updateDigest(digest, key);
                updateDigest(digest, String.valueOf(value));
            });
        }
        return views.size() + ":" + HexFormat.of().formatHex(digest.digest());
    }

    private static void updateDigest(MessageDigest digest, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        int length = bytes.length;
        digest.update((byte) (length >>> 24));
        digest.update((byte) (length >>> 16));
        digest.update((byte) (length >>> 8));
        digest.update((byte) length);
        digest.update(bytes);
    }

    private static Map<String, Map<String, Object>> buildIndex(List<Map<String, Object>> views) {
        Map<String, Map<String, Object>> index = new HashMap<>(Math.max(16, views.size() * 4));
        for (Map<String, Object> item : views) {
            putIndex(index, string(item, "id"), item);
            putIndex(index, string(item, "collectionId"), item);
            putIndex(index, string(item, "slug"), item);
            String slugLower = string(item, "slug").toLowerCase(Locale.ROOT);
            if (!slugLower.isBlank()) index.putIfAbsent(slugLower, item);
            putIndex(index, ObjektLinks.key(item), item);
            putIndex(index, ObjektLinks.id(item), item);
        }
        return Map.copyOf(index);
    }

    private static void putIndex(Map<String, Map<String, Object>> index, String key, Map<String, Object> item) {
        if (key == null || key.isBlank()) return;
        index.putIfAbsent(key, item);
    }

    private static String string(Map<String, Object> item, String key) {
        Object value = item.get(key);
        return value == null ? "" : value.toString().trim();
    }

    private List<Map<String, Object>> loadFromDatabase(String artist) {
        String key = artist == null || artist.isBlank() ? "triples" : artist;
        return catalogStore.load(key).stream().map(ObjektCatalogDto::toView).toList();
    }

    private static String detail(Throwable error) {
        Throwable root = error;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        if (root == error || root.getMessage() == null || root.getMessage().isBlank()) {
            return error.getMessage();
        }
        return error.getMessage() + " (" + root.getClass().getSimpleName() + ": " + root.getMessage() + ")";
    }

    public record FilterFacets(
            List<String> members,
            List<String> seasons,
            List<String> classes,
            List<String> collectionNumbers,
            Map<String, List<String>> numbersByClass
    ) {
        public static FilterFacets empty() {
            return new FilterFacets(List.of(), List.of(), List.of(), List.of(), Map.of());
        }

        public static FilterFacets from(List<Map<String, Object>> views) {
            Set<String> members = new LinkedHashSet<>();
            Set<String> seasons = new LinkedHashSet<>();
            Set<String> classes = new LinkedHashSet<>();
            Set<String> numbers = new LinkedHashSet<>();
            Map<String, Set<String>> grouped = new LinkedHashMap<>();
            for (Map<String, Object> item : views) {
                for (String member : MemberPortraitOptions.objektMembers(first(item, "member", "memberName"))) {
                    if (!member.isBlank()) members.add(member);
                }
                String season = first(item, "seasonKey", "season");
                if (!season.isBlank()) seasons.add(season.trim());
                String klass = first(item, "classKey", "class");
                if (!klass.isBlank()) classes.add(klass.trim());
                String number = first(item, "collectionNo", "collectionNumber");
                if (!number.isBlank()) numbers.add(number.trim());
                if (!klass.isBlank() && !number.isBlank()) {
                    grouped.computeIfAbsent(klass.trim(), key -> new TreeSet<>(String.CASE_INSENSITIVE_ORDER))
                            .add(number.trim());
                }
            }
            Map<String, List<String>> numbersByClass = new LinkedHashMap<>();
            grouped.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey(String.CASE_INSENSITIVE_ORDER))
                    .forEach(entry -> numbersByClass.put(entry.getKey(), List.copyOf(entry.getValue())));
            return new FilterFacets(
                    ObjektFilterOrder.sortedMembers(members),
                    ObjektFilterOrder.sortedSeasons(seasons),
                    classes.stream().sorted(String.CASE_INSENSITIVE_ORDER).toList(),
                    numbers.stream().sorted(String.CASE_INSENSITIVE_ORDER).toList(),
                    Map.copyOf(numbersByClass)
            );
        }

        private static String first(Map<String, Object> item, String... keys) {
            for (String key : keys) {
                Object value = item.get(key);
                if (value != null && !value.toString().isBlank() && !"null".equalsIgnoreCase(value.toString())) {
                    return value.toString();
                }
            }
            return "";
        }
    }
}
