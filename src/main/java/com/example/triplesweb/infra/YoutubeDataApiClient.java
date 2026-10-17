package com.example.triplesweb.infra;

import com.example.triplesweb.util.YoutubeIdExtractor;
import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.net.URI;

/**
 * YouTube Data API v3로 동영상 snippet·재생시간·조회수를 조회한다(외부 API, DB 미사용).
 */
@Component
public class YoutubeDataApiClient {
    private final RestTemplate rest = new RestTemplate();

    @Value("${youtube.api.key:}")
    private String apiKey;

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