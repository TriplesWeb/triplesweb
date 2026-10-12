package com.example.triplesweb.infra;

import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;


@Component
public class YoutubeDataApiClient {
    private final RestTemplate rest = new RestTemplate();
    @Value("${youtube.api.key:}") private String apiKey;

    public VideosResponse fetchVideo(String id){
        if (apiKey==null || apiKey.isBlank()) return null;
        String url = "https://www.googleapis.com/youtube/v3/videos"
                + "?part=snippet,contentDetails,statistics&id="+id+"&key="+apiKey;
        return rest.getForObject(url, VideosResponse.class);
    }

    @Data public static class VideosResponse {
        private Item[] items;
        @Data public static class Item {
            private Snippet snippet; private ContentDetails contentDetails; private Statistics statistics;
        }
        @Data public static class Snippet { private String title; private String description; private String publishedAt; }
        @Data public static class ContentDetails { private String duration; } // PT3M59S
        @Data public static class Statistics { private String viewCount; }
    }
}
