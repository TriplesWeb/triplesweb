
package com.example.triplesweb.service;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ObjektService {

    private final RestClient restClient;

    public ObjektService(
            @Value("${objekt.api.base-url:https://objekt.top/api/v1}")
            String baseUrl
    ) {
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .build();
    }

    /**
     * tripleS의 Objekt 컬렉션만 조회한다.
     */
    public List<Map<String, Object>> findCollections(String artist) {

        JsonNode response = restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/collections")
                        .queryParam("artist", "triples")
                        .build())
                .header("Accept", "application/json")
                .retrieve()
                .body(JsonNode.class);

        List<Map<String, Object>> result = new ArrayList<>();

        if (response == null
                || !response.path("collections").isArray()) {
            return result;
        }

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

            item.put("seasonKey",
                    string(item.get("season")).toLowerCase());

            item.put("classKey",
                    string(item.get("class")).toLowerCase());

            item.put("numberValue",
                    extractNumber(string(item.get("collectionNo"))));

            result.add(item);
        }

        return result;
    }

    private static void put(
            Map<String, Object> target,
            JsonNode source,
            String field
    ) {
        JsonNode value = source.get(field);

        target.put(
                field,
                value == null || value.isNull()
                        ? ""
                        : value.asText()
        );
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
