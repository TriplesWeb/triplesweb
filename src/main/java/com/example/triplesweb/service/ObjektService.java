package com.example.triplesweb.service;

import com.fasterxml.jackson.databind.JsonNode;
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
 * 외부 API를 웹 요청마다 호출하지 않도록 메모리 캐시를 사용한다.
 * 갱신 실패 시 기존 캐시를 유지해 외부 API 장애가 페이지 장애로 번지지 않게 한다.
 */
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
     * 캐시된 컬렉션을 반환한다. 최초 요청에만 외부 API를 호출한다.
     * 반환값은 호출자가 내부 캐시를 변경하지 못하도록 복사한다.
     */
    public List<Map<String, Object>> findCollections(String artist) {
        List<Map<String, Object>> snapshot = cachedCollections;
        if (snapshot == null) {
            refreshCache();
            snapshot = cachedCollections;
        }
        return snapshot == null ? List.of() : new ArrayList<>(snapshot);
    }

    /**
     * 캐시를 15분마다 갱신한다. 페이지 요청과 별도로 실행된다.
     * 외부 API 호출 실패 시 기존 캐시를 보존한다.
     */
    @Scheduled(fixedDelayString = "${objekt.cache.refresh-ms:900000}", initialDelay = 10000)
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
                return;
            }

            List<Map<String, Object>> result = new ArrayList<>();
            for (JsonNode node : response.path("collections")) {
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
                result.add(item);
            }

            // 한 번에 교체하여 요청 중인 스레드가 부분 갱신 데이터를 보지 않도록 한다.
            cachedCollections = List.copyOf(result);
            lastSuccessfulRefreshMillis = System.currentTimeMillis();
        } catch (RuntimeException ex) {
            // 갱신 실패 시 기존 캐시를 유지한다. 최초 적재 실패는 빈 목록으로 처리된다.
            if (cachedCollections == null) {
                throw ex;
            }
        }
    }

    public long getLastSuccessfulRefreshMillis() {
        return lastSuccessfulRefreshMillis;
    }

    private static void put(Map<String, Object> target, JsonNode source, String field) {
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
