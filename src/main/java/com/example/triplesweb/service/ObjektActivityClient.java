package com.example.triplesweb.service;

import com.example.triplesweb.infra.GzipRestClients;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

/** Unfiltered public activity across all accounts/artists. No per-account enumeration. */
@Service
public class ObjektActivityClient {
    public record Cursor(String timestamp, String id) {}
    public record Event(String id, String tokenId, String from, String to, String hash, long time,
                        String slug, String collection, String artist, Long serial) {}
    public record Page(List<Event> events, Cursor next) {}
    private final RestClient client;

    public ObjektActivityClient(@Value("${objekt.api.base-url:https://objekt.top/api/v1}") String url) {
        client = GzipRestClients.create(url, Duration.ofSeconds(3), Duration.ofSeconds(15));
    }

    public Page fetch(Cursor cursor) {
        JsonNode body = client.get().uri(builder -> {
            builder.path("/activity").queryParam("type", "all");
            if (cursor != null) builder.queryParam("cursor[timestamp]", "{time}").queryParam("cursor[id]", "{id}");
            return cursor == null ? builder.build() : builder.build(Map.of("time", cursor.timestamp(), "id", cursor.id()));
        }).retrieve().body(JsonNode.class);
        return parse(body, cursor);
    }

    static Page parse(JsonNode body, Cursor previous) {
        if (body == null || !body.isObject() || !body.path("items").isArray() || body.path("items").size() > 300) throw invalid();
        List<Event> events = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        long lastTime = previous == null ? Long.MAX_VALUE : time(previous.timestamp());
        String lastId = previous == null ? null : previous.id();
        for (JsonNode item : body.path("items")) {
            JsonNode tx = item.path("transfer"), card = item.path("objekt");
            String id = text(tx, "id", 200), token = text(card, "tokenId", 78);
            String from = text(tx, "from", 42).toLowerCase(Locale.ROOT), to = text(tx, "to", 42).toLowerCase(Locale.ROOT);
            String hash = text(tx, "hash", 66).toLowerCase(Locale.ROOT);
            long timestamp = time(text(tx, "timestamp", 40));
            if (!ids.add(id) || !token.matches("[0-9]{1,78}") || !from.matches("0x[0-9a-f]{40}")
                    || !to.matches("0x[0-9a-f]{40}") || !hash.matches("0x[0-9a-f]{64}") || timestamp > lastTime
                    || (timestamp == lastTime && lastId != null && id.compareTo(lastId) >= 0)) throw invalid();
            JsonNode serialValue = card.path("serial");
            if (!serialValue.isIntegralNumber() || !serialValue.canConvertToLong() || serialValue.asLong() < 0) throw invalid();
            long serial = serialValue.asLong();
            events.add(new Event(id, token, from, to, hash, timestamp, text(card, "slug", 200),
                    text(card, "collectionId", 200), text(card, "artist", 40), serial == 0 ? null : serial));
            lastTime = timestamp;
            lastId = id;
        }
        Cursor next = null;
        JsonNode cursor = body.path("nextCursor");
        if (!cursor.isMissingNode() && !cursor.isNull()) {
            if (events.isEmpty()) throw invalid();
            String timestamp = text(cursor, "timestamp", 40), id = text(cursor, "id", 200);
            Event last = events.get(events.size() - 1);
            if (time(timestamp) != last.time() || !id.equals(last.id())
                    || (previous != null && time(timestamp) == time(previous.timestamp()) && id.equals(previous.id()))) throw invalid();
            next = new Cursor(timestamp, id);
        }
        return new Page(List.copyOf(events), next);
    }
    private static String text(JsonNode node, String field, int max) {
        JsonNode value = node.path(field);
        if (!value.isTextual() || value.asText().isBlank() || value.asText().length() > max) throw invalid();
        return value.asText();
    }
    private static long time(String value) {
        try { long time = Instant.parse(value).toEpochMilli(); if (time < 0) throw invalid(); return time; }
        catch (RuntimeException e) { throw invalid(); }
    }
    private static IllegalStateException invalid() { return new IllegalStateException("공개 오브젝트 활동 응답을 검증하지 못했습니다."); }
}
