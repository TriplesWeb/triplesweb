package com.example.triplesweb.controller;

import com.example.triplesweb.service.AboutAdminAuth;
import com.example.triplesweb.service.MediaChannelSyncService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class MediaYoutubeSyncController {
    private final AboutAdminAuth admin;
    private final MediaChannelSyncService sync;

    public MediaYoutubeSyncController(AboutAdminAuth admin, MediaChannelSyncService sync) {
        this.admin = admin;
        this.sync = sync;
    }

    @GetMapping("/api/media/youtube/search-status")
    public Map<String, Boolean> searchStatus() {
        sync.ensureExternalSearch();
        return Map.of(
                "ready", sync.externalSearchReady(),
                "running", sync.externalSearchRunning());
    }

    @PostMapping("/api/media/youtube/sync")
    public MediaChannelSyncService.Result sync(HttpServletRequest request, HttpSession session) {
        admin.requireAdmin(request, session);
        return sync.syncFull();
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, String>> failed(IllegalStateException error) {
        String message = error.getMessage() == null || error.getMessage().isBlank()
                ? "유튜브 채널 동기화에 실패했습니다. 기존 저장 데이터는 유지됩니다."
                : error.getMessage();
        return ResponseEntity.status(503).body(Map.of("message", message));
    }
}
