package com.example.triplesweb.service;

import com.example.triplesweb.infra.GzipRestClients;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Apollo 오브젝트 설명 문장을 한국어로 바꾼다. 이벤트명·시대 등 고유명은 유지한다.
 * 외부 번역이 막히면 영어 원문을 그대로 둔다.
 */
@Service
public class ObjektStoryTranslateService {
    private static final Pattern HANGUL = Pattern.compile("[가-힣]");
    private static final Pattern LATIN = Pattern.compile("[A-Za-z]");
    private static final int CACHE_SIZE = 256;
    private static final int TEXT_LIMIT = 450;
    private static final List<String> KEEP = List.of(
            "tripleS", "byteS", "COSMO",
            "Atom01", "Atom02", "Binary01", "Binary02", "Cream01", "Cream02",
            "Divine01", "Ever01", "LOVE&amp;POP", "LOVE&POP", "Love&Pop"
    );

    private final RestClient client;
    private final boolean enabled;
    private final LinkedHashMap<String, String> cache = new LinkedHashMap<>(16, .75f, true);

    @Autowired
    public ObjektStoryTranslateService(
            @Value("${objekt.story.translate.enabled:true}") boolean enabled,
            @Value("${objekt.story.translate.base-url:https://api.mymemory.translated.net}") String baseUrl) {
        this(GzipRestClients.create(baseUrl, Duration.ofMillis(800), Duration.ofSeconds(2)), enabled);
    }

    ObjektStoryTranslateService(RestClient client, boolean enabled) {
        this.client = client;
        this.enabled = enabled;
    }

    public ApolloObjektMetadataClient.Story korean(ApolloObjektMetadataClient.Story story) {
        if (story == null) return null;
        return new ApolloObjektMetadataClient.Story(
                story.slug(),
                sentence(story.description()),
                story.eventName(),
                sentence(story.eventDescription()),
                story.eventType(),
                story.eraName(),
                story.twitterUrl()
        );
    }

    String sentence(String source) {
        String trimmed = source == null ? "" : source.trim();
        if (trimmed.isBlank() || looksKorean(trimmed) || !enabled) return trimmed;
        synchronized (cache) {
            String cached = cache.get(trimmed);
            if (cached != null) return cached;
        }
        String translated = fetch(trimmed);
        String result = translated == null || translated.isBlank() ? trimmed : translated;
        synchronized (cache) {
            while (cache.size() >= CACHE_SIZE) cache.remove(cache.keySet().iterator().next());
            cache.put(trimmed, result);
        }
        return result;
    }

    private String fetch(String source) {
        ProtectedText protectedText = protect(source);
        String query = protectedText.text();
        if (query.length() > TEXT_LIMIT) query = query.substring(0, TEXT_LIMIT);
        try {
            JsonNode body = client.get()
                    .uri("/get?q={q}&langpair={pair}", query, "en|ko")
                    .retrieve()
                    .body(JsonNode.class);
            String translated = text(body);
            if (translated.isBlank() || translated.equalsIgnoreCase(query)) return null;
            return restore(translated, protectedText.tokens());
        } catch (RestClientException | IllegalArgumentException ignored) {
            return null;
        }
    }

    private static String text(JsonNode body) {
        if (body == null || !body.isObject()) return "";
        JsonNode translated = body.path("responseData").path("translatedText");
        return translated.isMissingNode() || translated.isNull() ? "" : translated.asText("").trim();
    }

    static boolean looksKorean(String value) {
        int hangul = count(HANGUL, value);
        if (hangul == 0) return false;
        int latin = count(LATIN, value);
        return hangul >= latin;
    }

    private static int count(Pattern pattern, String value) {
        int n = 0;
        var matcher = pattern.matcher(value);
        while (matcher.find()) n++;
        return n;
    }

    private static ProtectedText protect(String source) {
        String text = source;
        List<String> tokens = new ArrayList<>();
        for (String keep : KEEP) {
            int index = 0;
            while (true) {
                int found = text.indexOf(keep, index);
                if (found < 0) break;
                String marker = "[[T" + tokens.size() + "]]";
                tokens.add(keep);
                text = text.substring(0, found) + marker + text.substring(found + keep.length());
                index = found + marker.length();
            }
        }
        return new ProtectedText(text, tokens);
    }

    private static String restore(String translated, List<String> tokens) {
        String text = translated;
        for (int i = 0; i < tokens.size(); i++) {
            text = text.replace("[[T" + i + "]]", tokens.get(i));
            text = text.replace("[[t" + i + "]]", tokens.get(i));
        }
        return text;
    }

    private record ProtectedText(String text, List<String> tokens) {}
}
