package com.example.triplesweb.infra;

import com.example.triplesweb.util.YoutubeIdExtractor;
import lombok.Data;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * YouTube oEmbed HTTP API로 제목·썸네일 등 메타데이터를 조회한다(외부 API, DB 미사용).
 */
@Component
public class YoutubeOembedClient {
    private final RestTemplate rest = new RestTemplate();

    public OembedResponse fetch(String urlOrId) {
        if (urlOrId == null || urlOrId.isBlank()) {
            return createDefaultResponse("Unknown");
        }

        // 1. 철저하게 11자리 비디오 ID 추출
        String videoId = YoutubeIdExtractor.extract(urlOrId);

        if (videoId == null || videoId.isBlank() || !videoId.matches("^[a-zA-Z0-9_-]{11}$")) {
            videoId = urlOrId.trim();
            if (videoId.length() > 11) {
                videoId = videoId.substring(0, 11);
            }
        }

        try {
            String targetUrl = "https://www.youtube.com/watch?v=" + videoId;
            String encodedTargetUrl = URLEncoder.encode(targetUrl, StandardCharsets.UTF_8);

            String requestUrl = "https://www.youtube.com/oembed?format=json&url=" + encodedTargetUrl;


            URI uri = URI.create(requestUrl);
            OembedResponse response = rest.getForObject(uri, OembedResponse.class);


                if (response != null) {
                    if (response.getTitle() == null || response.getTitle().isBlank()) {
                        response.setTitle("YouTube Video (" + videoId + ")");
                    }
                    // 💡 [정확한 주소로 수정]: 깨진 주소 대신 올바른 이미지 도메인 주소 포맷을 매핑합니다.
                    if (response.getThumbnail_url() == null || response.getThumbnail_url().isBlank()) {
                        response.setThumbnail_url("https://img.youtube.com/vi/" + videoId + "/hqdefault.jpg");
                    }
                    return response;
                }
        } catch (Exception e) {
        }
        return createDefaultResponse(videoId);
    }

    private OembedResponse createDefaultResponse(String videoId) {
        OembedResponse res = new OembedResponse();
        res.setTitle("YouTube Video (" + videoId + ")");
        res.setThumbnail_url("https://img.youtube.com/vi/" + videoId + "/hqdefault.jpg");
        return res;
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