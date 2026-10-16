package com.example.triplesweb.controller;

import com.example.triplesweb.service.MediaService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/media")
public class MediaResolveController {

    private final MediaService mediaService;

    @GetMapping("/resolve")
    public ResponseEntity<?> resolveYoutube(@RequestParam(required = false) String urlOrId) {

        if (urlOrId == null || urlOrId.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "URL 또는 ID가 입력되지 않았습니다."));
        }
        Map<String, Object> result = mediaService.makeYoutubeInfo(urlOrId);

        return ResponseEntity.ok(result);
    }
}