package com.example.triplesweb.service;

import com.fasterxml.jackson.databind.JsonNode;
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

    private volatile List<Map<String, Object>> cachedCollections;
    private volatile long lastSuccessfulRefreshMillis;

    public ObjektService(
            @Value("${objekt.api.base-url:https://objekt.top/api/v1}")
            String baseUrl
    ) {
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
    public List<Map<String, Object>> findCollections(String artist) {
        List<Map<String, Object>> snapshot = cachedCollections;
        return snapshot == null ? List.of() : snapshot;
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

            List<Map<String, Object>> result = new ArrayList<>();

            for (JsonNode node : collections) {
                Map<String, Object> item = new LinkedHashMap<>();

                put(item, node, "id");
                put(item, node, "createdAt");
                put(item, node, "slug");
                put(item, node, "collectionId");
                put(item, node, "season");
                put(item, node, "member");
                put(item, node, "artist");
                put(item, node, "collectionNo");
                put(item, node, "class");
                put(item, node, "thumbnailImage");
                put(item, node, "frontImage");
                put(item, node, "backImage");
                put(item, node, "backgroundColor");
                put(item, node, "textColor");
                put(item, node, "onOffline");
                put(item, node, "hasAudio");
                put(item, node, "originalFrontImage");
                put(item, node, "originalBackImage");
                put(item, node, "frontMedia");
                put(item, node, "bandImageUrl");

                item.put("searchText", String.join(" ",
                        string(item.get("collectionId")),
                        string(item.get("slug")),
                        string(item.get("season")),
                        string(item.get("member")),
                        string(item.get("artist")),
                        string(item.get("collectionNo")),
                        string(item.get("class"))
                ));

                item.put("seasonKey", string(item.get("season")).toLowerCase());
                item.put("classKey", string(item.get("class")).toLowerCase());
                item.put("numberValue", extractNumber(string(item.get("collectionNo"))));

                // 항목 자체도 불변으로 만들어 호출자가 캐시를 변경하지 못하게 한다.
                result.add(Map.copyOf(item));
            }

            // 모든 데이터를 완성한 후 한 번에 교체한다.
            cachedCollections = List.copyOf(result);
            lastSuccessfulRefreshMillis = System.currentTimeMillis();

            log.info("Objekt 캐시 갱신 완료: {}개", cachedCollections.size());

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

    private static void put(
            Map<String, Object> target,
            JsonNode source,
            String field
    ) {
        JsonNode value = source.get(field);
        target.put(field, value == null || value.isNull() ? "" : value.asText());
    }

    private static String string(Object value) {
        return value == null ? "" : value.toString();
    }

    private static int extractNumber(String value) {
        String digits = value.replaceAll("[^0-9]", "");

        if (digits.isBlank()) {
            return Integer.MAX_VALUE;
        }

        try {
            return Integer.parseInt(digits);
        } catch (NumberFormatException ex) {
            return Integer.MAX_VALUE;
        }
    }
}
