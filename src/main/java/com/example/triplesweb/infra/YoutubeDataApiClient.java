package com.example.triplesweb.infra;

import com.example.triplesweb.util.ISODuration;
import com.example.triplesweb.util.YoutubeIdExtractor;
import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * YouTube Data API v3로 동영상 snippet·재생시간·조회수를 조회한다(외부 API, DB 미사용).
 */
@Component
public class YoutubeDataApiClient {
    private final RestTemplate rest;

    @Value("${youtube.api.key:}")
    private String apiKey;

    public YoutubeDataApiClient() {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(2_000);
        factory.setReadTimeout(3_000);
        this.rest = new RestTemplate(factory);
    }

    YoutubeDataApiClient(RestTemplate rest) {
        this.rest = rest;
    }

/**
     * 조회수와 함께, 비어 있는 공개일·길이를 채울 수 있도록 snippet·contentDetails를 같은 요청에서 읽는다.
     * 한 요청에 최대 50개이며, 키가 없거나 호출이 실패하면 빈 맵을 반환한다.
     */
    public Map<String, LiveVideo> fetchLive(Collection<String> videoIds) {
        if (apiKey == null || apiKey.isBlank() || videoIds == null || videoIds.isEmpty()) {
            return Map.of();
        }
        List<String> ids = new ArrayList<>();
        for (String raw : new LinkedHashSet<>(videoIds)) {
            if (raw != null && raw.matches("^[a-zA-Z0-9_-]{11}$")) ids.add(raw);
        }
        Map<String, LiveVideo> live = new LinkedHashMap<>();
        for (int offset = 0; offset < ids.size(); offset += 50) {
            List<String> chunk = ids.subList(offset, Math.min(offset + 50, ids.size()));
            VideosResponse response = requestVideos(chunk);
            if (response == null || response.getItems() == null) continue;
            for (VideosResponse.Item item : response.getItems()) {
                if (item.getId() == null) continue;
                live.put(item.getId(), new LiveVideo(parseViewCount(item), publishedDay(item), clock(item)));
            }
        }
        return live;
    }

    /**
     * 영상 ID의 현재 조회수를 가져온다. 한 요청에 최대 50개이며, 키가 없거나 호출이 실패하면 빈 맵을 반환한다.
     */
    public Map<String, Long> fetchViewCounts(Collection<String> videoIds) {
        Map<String, Long> counts = new LinkedHashMap<>();
        fetchLive(videoIds).forEach((id, video) -> {
            if (video.viewCount() != null) counts.put(id, video.viewCount());
        });
        return counts;
    }

    private VideosResponse requestVideos(List<String> ids) {
        try {
            String url = "https://www.googleapis.com/youtube/v3/videos"
                    + "?part=snippet,contentDetails,statistics&id=" + String.join(",", ids) + "&key=" + apiKey;
            return rest.getForObject(URI.create(url), VideosResponse.class);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static String publishedDay(VideosResponse.Item item) {
        if (item == null || item.getSnippet() == null || item.getSnippet().getPublishedAt() == null) return null;
        String raw = item.getSnippet().getPublishedAt().trim();
        if (raw.length() < 10 || raw.charAt(4) != '-') return null;
        return raw.substring(0, 10);
    }

    private static String clock(VideosResponse.Item item) {
        if (item == null || item.getContentDetails() == null) return null;
        String clock = ISODuration.toClock(item.getContentDetails().getDuration());
        return clock == null || clock.isBlank() || "0:00".equals(clock) ? null : clock;
    }

    public record LiveVideo(Long viewCount, String publishedOn, String duration) {}

    private static Long parseViewCount(VideosResponse.Item item) {
        if (item == null || item.getStatistics() == null || item.getStatistics().getViewCount() == null) return null;
        try {
            return Long.parseLong(item.getStatistics().getViewCount().trim());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    public VideosResponse fetchVideo(String urlOrId) {

        System.out.println("==================================================");
        System.out.println(">>> [DEBUG] 전달된 id의 글자 수: " + (urlOrId != null ? urlOrId.length() : "null"));

        if (apiKey == null || apiKey.isBlank()) {
            return null;
        }
        if (urlOrId == null || urlOrId.isBlank()) {
            return null;
        }

        String videoId = YoutubeIdExtractor.extract(urlOrId);
        if (videoId == null || videoId.isBlank()) {
            videoId = urlOrId.trim();
        }

        try {
            // 2. 안전하게 구글 API URL 조합
            String url = "https://www.googleapis.com/youtube/v3/videos"
                    + "?part=snippet,contentDetails,statistics&id=" + videoId + "&key=" + apiKey;

            // 3. String 대신 URI 객체로 전달하여 RestTemplate 내부 템플릿 파서 에러(-16 에러) 원천 차단
            URI uri = URI.create(url);
            return rest.getForObject(uri, VideosResponse.class);

        } catch (Throwable t) {
            // 4. API 쿼터 초과, 네트워크 장애, 400 에러 등 어떤 문제가 발생해도 절대 크래시 나지 않도록 방어
            return null;
        }
    }

    @Data
    public static class VideosResponse {
        private Item[] items;

        @Data
        public static class Item {
            private String id;
            private Snippet snippet;
            private ContentDetails contentDetails;
            private Statistics statistics;
        }

        @Data
        public static class Snippet {
            private String title;
            private String description;
            private String publishedAt;
        }

        @Data
        public static class ContentDetails {
            private String duration; // PT3M59S
        }

        @Data
        public static class Statistics {
            private String viewCount;
        }
    }
}