package com.example.triplesweb.service;

import com.example.triplesweb.dto.ObjektCatalogDto;
import com.example.triplesweb.entity.ObjektCatalog;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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

    private volatile List<Map<String, Object>> cachedCollections;
    private volatile long lastSuccessfulRefreshMillis;

    public ObjektService(
            @Value("${objekt.api.base-url:https://objekt.top/api/v1}")
            String baseUrl,
            ObjektCatalogStore catalogStore
    ) {
        this.catalogStore = catalogStore;
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .build();

        JdkClientHttpRequestFactory requestFactory =
                new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(6));

        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .build();
    }

    /**
     * 페이지 요청에서는 외부 API를 호출하지 않는다.
     * 최초 캐시 적재 전에는 빈 목록을 즉시 반환한다.
     */
    @PostConstruct
    void loadStoredCatalog() {
        cachedCollections = loadFromDatabase("triples");
    }

    public List<Map<String, Object>> findCollections(String artist) {
        List<Map<String, Object>> snapshot = cachedCollections;
        // 빈 결과도 정상적으로 적재된 캐시다. 빈 테이블일 때 페이지 요청마다
        // DB 전체 조회가 반복되지 않도록 null인 최초 상태에서만 다시 읽는다.
        if (snapshot == null) {
            snapshot = loadFromDatabase(artist);
            cachedCollections = snapshot;
        }
        return snapshot;
    }

    /**
     * 기본 15분 간격으로 캐시를 갱신한다.
     * 최초 적재 실패도 예외를 페이지 요청으로 전파하지 않고 다음 주기에 재시도한다.
     */
    @Scheduled(
            fixedDelayString = "${objekt.cache.refresh-ms:900000}",
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
            catalogStore.replace("triples", rows);
            cachedCollections = rows.stream().map(ObjektCatalogDto::toView).toList();
            lastSuccessfulRefreshMillis = System.currentTimeMillis();

            log.info("Objekt 카탈로그 저장 완료: {}개", cachedCollections.size());

        } catch (RuntimeException ex) {
            // 최초 적재 실패를 포함해 페이지 요청으로 예외를 전파하지 않는다.
            // 캐시가 이미 있다면 기존 데이터를 그대로 유지한다.
            log.warn(
                    "Objekt 캐시 갱신 실패. 기존 캐시를 유지하고 다음 주기에 재시도합니다: {}",
                    ex.getMessage()
            );
        }
    }

    public long getLastSuccessfulRefreshMillis() {
        return lastSuccessfulRefreshMillis;
    }

    private List<Map<String, Object>> loadFromDatabase(String artist) {
        String key = artist == null || artist.isBlank() ? "triples" : artist;
        return catalogStore.load(key).stream().map(ObjektCatalogDto::toView).toList();
    }
}

