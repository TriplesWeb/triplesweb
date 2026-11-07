package com.example.triplesweb.service;

import com.example.triplesweb.infra.GzipRestClients;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Apollo {@code /api/objekts/metadata/{slug}} 컬렉션 설명·이벤트·시대.
 * 기본값은 비활성({@code apollo.descriptions.enabled=false}). 설명은 시드로 적재한
 * {@code objekt_description}만 쓰며, 런타임 모달은 Apollo 메타를 붙이지 않는다.
 */
@Service
public class ApolloObjektMetadataClient {
    private static final Pattern SLUG = Pattern.compile("[a-z0-9]+(?:-[a-z0-9]+)+");
    private static final Pattern UUID = Pattern.compile("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    private static final int CACHE_SIZE = 256;
    private static final long TTL_NANOS = Duration.ofMinutes(10).toNanos();
    private static final int TEXT_LIMIT = 4000;

    public record Story(String slug, String description, String eventName, String eventDescription,
                        String eventType, String eraName, String twitterUrl) {
        public Map<String, String> toMap() {
            Map<String, String> body = new LinkedHashMap<>();
            put(body, "slug", slug);
            put(body, "description", description);
            put(body, "eventName", eventName);
            put(body, "eventDescription", eventDescription);
            put(body, "eventType", eventType);
            put(body, "eraName", eraName);
            put(body, "twitterUrl", twitterUrl);
            return body;
        }

        private static void put(Map<String, String> body, String key, String value) {
            if (value != null && !value.isBlank()) body.put(key, value);
        }

        public boolean isEmpty() {
            return description.isBlank() && eventName.isBlank() && eventDescription.isBlank() && eraName.isBlank();
        }
    }

    private record Entry(Story story, long expiresAt) {}

    private final RestClient client;
    private final boolean enabled;
    private final LinkedHashMap<String, Entry> cache = new LinkedHashMap<>(16, .75f, true);

    @Autowired
    public ApolloObjektMetadataClient(
            @Value("${apollo.api.base-url:https://apollo.cafe}") String baseUrl,
            @Value("${apollo.descriptions.enabled:true}") boolean enabled) {
        this(GzipRestClients.create(baseUrl, Duration.ofSeconds(2), Duration.ofSeconds(4)), enabled);
    }

    ApolloObjektMetadataClient(RestClient client, boolean enabled) {
        this.client = client;
        this.enabled = enabled;
    }

    public Optional<Story> story(Map<String, Object> item) {
        String slug = slug(item);
        if (!enabled || slug.isBlank()) return Optional.empty();
        synchronized (cache) {
            cache.entrySet().removeIf(entry -> System.nanoTime() - entry.getValue().expiresAt() >= 0);
            Entry cached = cache.get(slug);
            if (cached != null) return Optional.ofNullable(cached.story());
        }
        Story story = fetch(slug);
        synchronized (cache) {
            while (cache.size() >= CACHE_SIZE) cache.remove(cache.keySet().iterator().next());
            cache.put(slug, new Entry(story, System.nanoTime() + TTL_NANOS));
        }
        return Optional.ofNullable(story);
    }

    static String slug(Map<String, Object> item) {
        if (item == null) return "";
        String slug = token(item.get("slug"), true);
        if (isCollectionSlug(slug)) return slug;
        String season = token(first(item, "season", "seasonKey"), true);
        String member = token(first(item, "member", "memberName"), false);
        String number = token(first(item, "collectionNo", "collectionNumber"), true);
        if (season.isBlank() || member.isBlank() || number.isBlank()) return "";
        String built = season + "-" + member + "-" + number;
        return isCollectionSlug(built) ? built : "";
    }

    static Story parse(String slug, JsonNode root) {
        if (root == null || !root.isObject()) return null;
        JsonNode data = root.path("data");
        JsonNode event = data.path("event");
        Story story = new Story(
                slug,
                clip(text(data, "description")),
                clip(text(event, "name")),
                clip(text(event, "description")),
                clip(text(event, "eventType")),
                clip(text(event.path("era"), "name")),
                twitter(text(event, "twitterUrl"))
        );
        return story.isEmpty() ? null : story;
    }

    /**
     * 컬렉션 설명만 읽는다. 모달 표시 여부와 무관하다.
     * 통신 실패는 빈 Optional, 설명이 없으면 빈 문자열이다.
     */
    public Optional<String> collectionDescription(String slug) {
        if (slug == null || slug.isBlank()) return Optional.of("");
        try {
            JsonNode body = client.get()
                    .uri("/api/objekts/metadata/{slug}", slug)
                    .header(HttpHeaders.ACCEPT, "application/json")
                    .header(HttpHeaders.REFERER, "https://apollo.cafe/?id=" + slug)
                    .header(HttpHeaders.USER_AGENT, "Mozilla/5.0 triplesweb")
                    .retrieve()
                    .body(JsonNode.class);
            if (body == null) return Optional.empty();
            return Optional.of(clip(text(body.path("data"), "description")));
        } catch (RestClientException ignored) {
            return Optional.empty();
        }
    }

    private Story fetch(String slug) {
        try {
            JsonNode body = client.get()
                    .uri("/api/objekts/metadata/{slug}", slug)
                    .header(HttpHeaders.ACCEPT, "application/json")
                    .header(HttpHeaders.REFERER, "https://apollo.cafe/?id=" + slug)
                    .header(HttpHeaders.USER_AGENT, "Mozilla/5.0 triplesweb")
                    .retrieve()
                    .body(JsonNode.class);
            return parse(slug, body);
        } catch (RestClientException ignored) {
            return null;
        }
    }

    private static boolean isCollectionSlug(String slug) {
        return slug != null && SLUG.matcher(slug).matches() && !UUID.matcher(slug).matches();
    }

    private static Object first(Map<String, Object> item, String... keys) {
        for (String key : keys) {
            Object value = item.get(key);
            if (value != null && !value.toString().isBlank()) return value;
        }
        return "";
    }

    private static String token(Object value, boolean keepHyphen) {
        if (value == null) return "";
        String raw = value.toString().trim().toLowerCase(Locale.ROOT);
        return keepHyphen ? raw.replaceAll("[^a-z0-9-]", "") : raw.replaceAll("[^a-z0-9]", "");
    }

    private static String text(JsonNode node, String field) {
        if (node == null || !node.isObject()) return "";
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? "" : value.asText("");
    }

    private static String clip(String value) {
        String trimmed = value == null ? "" : value.trim();
        return trimmed.length() <= TEXT_LIMIT ? trimmed : trimmed.substring(0, TEXT_LIMIT);
    }

    private static String twitter(String value) {
        String trimmed = clip(value);
        if (trimmed.isBlank()) return "";
        String lower = trimmed.toLowerCase(Locale.ROOT);
        if (!(lower.startsWith("https://x.com/") || lower.startsWith("https://twitter.com/"))) return "";
        return trimmed;
    }
}
