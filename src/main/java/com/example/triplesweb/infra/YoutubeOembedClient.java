package com.example.triplesweb.infra;

import lombok.Data;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
public class YoutubeOembedClient {
    private final RestTemplate rest = new RestTemplate();
    public OembedResponse fetch(String youtubeUrl){
        return rest.getForObject("https://www.youtube.com/oembed?format=json&url="+youtubeUrl,
                OembedResponse.class);
    }
    @Data
    public static class OembedResponse {
        private String title;
        private String author_name;
        private String thumbnail_url;
        private int thumbnail_width;
        private int thumbnail_height;
    }
}
