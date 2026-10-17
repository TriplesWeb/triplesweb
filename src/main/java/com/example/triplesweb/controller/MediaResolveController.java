package com.example.triplesweb.controller;

import com.example.triplesweb.service.MediaService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 유튜브 URL/ID로 제목·썸네일·재생시간 등 메타데이터 조회 API.
 * URL: {@code GET /api/media/resolve?urlOrId=}
 * 레이어: Optional — OEmbed·Data API 클라이언트 직접 조합, Facade/Service 분리 검토.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/media")
public class MediaResolveController {

    private final MediaService mediaService;

    /** OEmbed 우선, Data API로 제목·길이·조회수 등 보강(실패 시 부분 응답) */
    @GetMapping("/resolve")
    public ResponseEntity<?> resolveYoutube(@RequestParam(required = false) String urlOrId) {

        if (urlOrId == null || urlOrId.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "URL 또는 ID가 입력되지 않았습니다."));
        }
        Map<String, Object> result = mediaService.makeYoutubeInfo(urlOrId);

        return ResponseEntity.ok(result);
    }
}