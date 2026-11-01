package com.example.triplesweb.service;

import com.example.triplesweb.infra.GzipRestClients;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import java.time.Duration;
import java.util.LinkedHashMap;

/** Public index adapter. Historical index records are never proof of current ownership. */
@Service
public class ObjektIndexClient {
    private final RestClient client;
    // Short, bounded cache: owner lookup and history share one upstream response.
    // Never serve expired history on failure: an upstream visibility change must take effect.
    private final LinkedHashMap<String, Entry> serials = new LinkedHashMap<>();
    private final Object[] locks = java.util.stream.IntStream.range(0, 32).mapToObj(i -> new Object()).toArray();
    private record Entry(JsonNode body, long expiresAt) {}

    public ObjektIndexClient(@Value("${objekt.api.base-url:https://objekt.top/api/v1}") String baseUrl) {
        client = GzipRestClients.create(baseUrl, Duration.ofSeconds(3), Duration.ofSeconds(15));
    }

    public JsonNode serialHistory(String slug, long serial) {
        if (slug == null || slug.isBlank() || serial < 1)
            throw new IllegalArgumentException("컬렉션과 시리얼을 확인해 주세요.");
        String key = slug + ":" + serial;
        synchronized (locks[Math.floorMod(key.hashCode(), locks.length)]) {
            synchronized (serials) {
                long now = System.nanoTime();
                serials.entrySet().removeIf(e -> now - e.getValue().expiresAt() >= 0);
                Entry cached = serials.get(key);
                if (cached != null) return cached.body().deepCopy();
            }
            JsonNode body;
            try {
                body = client.get().uri("/collections/{slug}/serials/{serial}/transfers", slug, serial)
                        .retrieve().body(JsonNode.class);
            } catch (RestClientResponseException e) {
                if (e.getStatusCode().value() == 404) return null;
                throw e;
            }
            if (body == null || !body.isObject() || !body.path("transfers").isArray()
                    || body.path("transfers").size() > 10000)
                throw new IllegalStateException("전송 내역 응답이 올바르지 않습니다.");
            if (!body.path("hide").asBoolean() && body.path("transfers").size() <= 1000
                    && body.path("tokenId").asText("").matches("[0-9]{1,78}")) {
                synchronized (serials) {
                    if (serials.size() >= 256) serials.remove(serials.keySet().iterator().next());
                    serials.put(key, new Entry(body.deepCopy(), System.nanoTime() + Duration.ofSeconds(30).toNanos()));
                }
            }
            return body;
        }
    }

    public JsonNode accountHistory(String address, String timestamp, String id) {
        if (!address.matches("0x[0-9a-fA-F]{40}")) throw new IllegalArgumentException("잘못된 계정 주소입니다.");
        JsonNode body = client.get().uri(builder -> {
            builder.path("/transfers/{address}").queryParam("order", "desc").queryParam("type", "all");
            if (!timestamp.isBlank()) builder.queryParam("cursor[timestamp]", "{timestamp}").queryParam("cursor[id]", "{cursorId}");
            return builder.build(java.util.Map.of("address", address.toLowerCase(java.util.Locale.ROOT),
                    "timestamp", timestamp, "cursorId", id));
        }).retrieve().body(JsonNode.class);
        if (body == null || !body.isObject() || !body.path("results").isArray() || body.path("results").size() > 150)
            throw new IllegalStateException("계정 전송 내역 응답이 올바르지 않습니다.");
        return body;
    }
}
