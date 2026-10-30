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

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

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
    @Value("${objekt.cache.persist:true}")
    private boolean persistCatalog = true;

    private volatile List<Map<String, Object>> cachedCollections = List.of();
    private volatile Map<String, Map<String, Object>> byKey = Map.of();
    private volatile FilterFacets facets = FilterFacets.empty();
    private volatile long lastSuccessfulRefreshMillis;

    public ObjektService(
            @Value("${objekt.api.base-url:https://objekt.top/api/v1}")
            String baseUrl,
            @Value("${objekt.cache.read-timeout-ms:30000}")
            long readTimeoutMs,
            ObjektCatalogStore catalogStore
    ) {
        this.catalogStore = catalogStore;
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

    /**
     * 기본 1분 간격으로 캐시를 갱신한다.
     * 최초 적재 실패도 예외를 페이지 요청으로 전파하지 않고 다음 주기에 재시도한다.
     */
    @Scheduled(
            fixedDelayString = "${objekt.cache.refresh-ms:60000}",
            initialDelayString = "${objekt.cache.initial-delay-ms:0}"
    )
    public synchronized void refreshCache() {
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
            // DB는 다음 시작 시 복구할 스냅샷으로만 저장한다. 갱신 직후 다시
            // SELECT 하지 않고, 이미 받은 API 응답으로 메모리 캐시를 교체한다.
            if (persistCatalog) catalogStore.replace("triples", rows);
            install(rows.stream().map(ObjektCatalogDto::toView).toList());
            lastSuccessfulRefreshMillis = System.currentTimeMillis();

            log.info("Objekt 카탈로그 갱신 완료: {}개 (DB 저장: {})", cachedCollections.size(), persistCatalog);

        } catch (RuntimeException ex) {
            // 최초 적재 실패를 포함해 페이지 요청으로 예외를 전파하지 않는다.
            // 캐시가 이미 있다면 기존 데이터를 그대로 유지한다.
            log.warn(
                    "Objekt 캐시 갱신 실패. 기존 캐시를 유지하고 다음 주기에 재시도합니다: {}",
                    detail(ex)
            );
        }
    }

    public long getLastSuccessfulRefreshMillis() {
        return lastSuccessfulRefreshMillis;
    }

    private void install(List<Map<String, Object>> views) {
        List<Map<String, Object>> snapshot = views == null ? List.of() : List.copyOf(views);
        cachedCollections = snapshot;
        byKey = buildIndex(snapshot);
        facets = FilterFacets.from(snapshot);
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
            List<String> collectionNumbers
    ) {
        public static FilterFacets empty() {
            return new FilterFacets(List.of(), List.of(), List.of(), List.of());
        }

        public static FilterFacets from(List<Map<String, Object>> views) {
            Set<String> members = new LinkedHashSet<>();
            Set<String> seasons = new LinkedHashSet<>();
            Set<String> classes = new LinkedHashSet<>();
            Set<String> numbers = new LinkedHashSet<>();
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
            }
            return new FilterFacets(
                    ObjektFilterOrder.sortedMembers(members),
                    ObjektFilterOrder.sortedSeasons(seasons),
                    classes.stream().sorted(String.CASE_INSENSITIVE_ORDER).toList(),
                    numbers.stream().sorted(String.CASE_INSENSITIVE_ORDER).toList()
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
