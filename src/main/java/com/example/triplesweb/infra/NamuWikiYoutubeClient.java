package com.example.triplesweb.infra;

import com.example.triplesweb.util.YoutubeIdExtractor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 나무위키 tripleS 본문·하위문서에서 유튜브 영상 ID를 모은다.
 * 페이지를 못 읽으면 빈 목록을 돌려 기존 저장 데이터를 유지한다.
 */
@Slf4j
@Component
public class NamuWikiYoutubeClient {
    private static final Pattern YOUTUBE_URL = Pattern.compile(
            "(?:https?:)?//(?:www\\.|m\\.)?(?:youtube\\.com/(?:watch\\?[^\\s\"'<>]*v=|embed/|shorts/|live/|v/)|youtu\\.be/)([a-zA-Z0-9_-]{11})",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern YOUTUBE_MACRO = Pattern.compile(
            "\\[youtube\\(([a-zA-Z0-9_-]{11})\\)\\]",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern WIKI_HREF = Pattern.compile(
            "(?:href|src)=['\"](?:https://namu\\.wiki)?/w/([^'\"?#]+)['\"]",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern SUBPAGE_LINK = Pattern.compile(
            "(?:/w/|\\[\\[)(tripleS/[^\\]|#'\"?]+)",
            Pattern.CASE_INSENSITIVE);
    private static final int MAX_PAGES = 80;
    private static final Pattern H2_EXTERNAL = Pattern.compile(
            "(?is)(?:==\\s*외부 콘텐츠\\s*==|##\\s*3\\.\\s*외부 콘텐츠|id=[\"']s-3[\"'])");
    private static final Pattern H2_AFTER_EXTERNAL = Pattern.compile(
            "(?is)(?:==\\s*(?:리얼리티|라이브 방송)\\s*==|##\\s*4\\.|id=[\"']s-4[\"'])");
    private static final List<String> SEED_PAGES = List.of(
            "tripleS",
            "tripleS/음방",
            "tripleS/콘텐츠");
    private static final List<String> ALBUM_PAGES = List.of(
            "ACCESS", "ASSEMBLE", "AESTHETIC", "ASSEMBLE24", "ASSEMBLE25",
            "Inner Dance", "Performante", "Beyond Beauty", "Just Do It",
            "Structure of Sadness", "Andless", "I Want You To Know",
            "Touch+", "LOVElution", "EVOLution");

    private static final Duration FORBIDDEN_PAUSE = Duration.ofMinutes(30);

    private final RestTemplate rest;
    private final Map<String, Instant> routeRetryTimes = new ConcurrentHashMap<>();
    private volatile List<String> lastExternalIds = List.of();

    public NamuWikiYoutubeClient() {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(4_000);
        factory.setReadTimeout(8_000);
        this.rest = new RestTemplate(factory);
    }

    NamuWikiYoutubeClient(RestTemplate rest) {
        this.rest = rest;
    }

    /** tripleS 본문과 하위문서, 음방 음반 문서에 적힌 유튜브 ID. */
    public List<String> videoIds() {
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        ArrayList<String> queue = new ArrayList<>();
        for (String title : SEED_PAGES) enqueue(queue, seen, title);
        for (String title : ALBUM_PAGES) enqueue(queue, seen, title);
        LinkedHashSet<String> ids = new LinkedHashSet<>();
        int fetched = 0;
        for (int i = 0; i < queue.size() && fetched < MAX_PAGES; i++) {
            String title = queue.get(i);
            String html = fetchPage(title);
            if (html.isBlank()) continue;
            fetched++;
            ids.addAll(extract(html));
            if (title.equals("tripleS/콘텐츠") || title.toLowerCase(Locale.ROOT).contains("콘텐츠")) {
                lastExternalIds = extract(sliceExternalSection(html));
            }
            extractSubpages(html).forEach(sub -> enqueue(queue, seen, sub));
            if (title.toLowerCase(Locale.ROOT).contains("음방")) {
                extractAlbumTitles(html).forEach(album -> enqueue(queue, seen, album));
            }
        }
        return new ArrayList<>(ids);
    }

    /** 나무위키 tripleS/콘텐츠 3절(외부 콘텐츠)에 적힌 유튜브 ID. */
    public List<String> externalVideoIds() {
        List<String> cached = lastExternalIds;
        if (cached != null && !cached.isEmpty()) return new ArrayList<>(cached);
        String html = fetchPage("tripleS/콘텐츠");
        if (html.isBlank()) return List.of();
        List<String> ids = extract(sliceExternalSection(html));
        lastExternalIds = ids;
        return new ArrayList<>(ids);
    }

    static String sliceExternalSection(String body) {
        if (body == null || body.isBlank()) return "";
        Matcher start = H2_EXTERNAL.matcher(body);
        if (!start.find()) return "";
        int from = start.start();
        Matcher end = H2_AFTER_EXTERNAL.matcher(body);
        int to = body.length();
        if (end.find(from + 1)) to = end.start();
        return body.substring(from, to);
    }

    private static void enqueue(List<String> queue, LinkedHashSet<String> seen, String title) {
        if (title == null || title.isBlank() || seen.size() >= MAX_PAGES || !seen.add(title)) return;
        queue.add(title);
    }

    static List<String> extract(String html) {
        LinkedHashSet<String> ids = new LinkedHashSet<>();
        if (html == null || html.isBlank()) return List.of();
        Matcher matcher = YOUTUBE_URL.matcher(html);
        while (matcher.find()) {
            String id = matcher.group(1);
            if (id != null && id.matches("^[a-zA-Z0-9_-]{11}$")) ids.add(id);
        }
        Matcher macros = YOUTUBE_MACRO.matcher(html);
        while (macros.find()) {
            ids.add(macros.group(1));
        }
        for (String token : html.split("[\\s\"'<>\\[\\]()]+")) {
            String extracted = YoutubeIdExtractor.extract(token);
            if (extracted != null && extracted.matches("^[a-zA-Z0-9_-]{11}$")) ids.add(extracted);
        }
        return new ArrayList<>(ids);
    }

    static List<String> extractSubpages(String html) {
        LinkedHashSet<String> titles = new LinkedHashSet<>();
        if (html == null || html.isBlank()) return List.of();
        Matcher matcher = SUBPAGE_LINK.matcher(html);
        while (matcher.find()) {
            String raw = decode(matcher.group(1));
            if (raw == null || raw.isBlank()) continue;
            String title = raw.replace('_', ' ').trim();
            if (title.startsWith("tripleS/") && title.length() > "tripleS/".length()) titles.add(title);
        }
        return new ArrayList<>(titles);
    }

    static List<String> extractAlbumTitles(String html) {
        LinkedHashSet<String> titles = new LinkedHashSet<>();
        if (html == null || html.isBlank()) return List.of();
        Matcher matcher = WIKI_HREF.matcher(html);
        while (matcher.find()) {
            String raw = decode(matcher.group(1));
            if (raw == null || raw.isBlank() || raw.contains("/") || raw.contains(":")) continue;
            if (!raw.matches(".*[A-Za-z0-9].*")) continue;
            titles.add(raw);
        }
        return new ArrayList<>(titles);
    }

    private String fetchPage(String title) {
        if (title == null || title.isBlank()) return "";
        String encoded = URLEncoder.encode(title, StandardCharsets.UTF_8).replace("+", "%20");
        String raw = fetchUrl("raw", encoded);
        return raw.isBlank() ? fetchUrl("w", encoded) : raw;
    }

    private String fetchUrl(String route, String encoded) {
        if (Instant.now().isBefore(routeRetryTimes.getOrDefault(route, Instant.EPOCH))) return "";
        String url = "https://namu.wiki/" + route + "/" + encoded;
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set(HttpHeaders.USER_AGENT,
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
            headers.set(HttpHeaders.ACCEPT, "text/html,text/plain,application/xhtml+xml");
            var response = rest.exchange(url, HttpMethod.GET, new HttpEntity<>(headers), String.class);
            String body = response.getBody();
            return usable(body) ? body : "";
        } catch (RestClientResponseException error) {
            if (error.getStatusCode().value() == 403) {
                Instant retryAt = Instant.now().plus(FORBIDDEN_PAUSE);
                routeRetryTimes.put(route, retryAt);
                log.warn("NamuWiki /{} returned HTTP 403. Requests paused until {}.", route, retryAt);
            }
            return "";
        } catch (RestClientException ignored) {
            return "";
        }
    }

    private static boolean usable(String body) {
        if (body == null || body.isBlank()) return false;
        String lower = body.toLowerCase(Locale.ROOT);
        return !lower.contains("just a moment") && !lower.contains("cf-mitigated");
    }

    private static String decode(String raw) {
        try {
            return java.net.URLDecoder.decode(raw.replace("%20", "+"), StandardCharsets.UTF_8).trim();
        } catch (RuntimeException ignored) {
            return raw;
        }
    }
}
