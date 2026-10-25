package com.example.triplesweb.controller;

import com.example.triplesweb.service.AboutAdminAuth;
import com.example.triplesweb.service.CosmoObjektSyncService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
public class CosmoObjektSyncController {
    private final AboutAdminAuth admin;
    private final CosmoObjektSyncService sync;
    public CosmoObjektSyncController(AboutAdminAuth admin, CosmoObjektSyncService sync) { this.admin = admin; this.sync = sync; }
    @PostMapping("/api/cosmo/objekts/sync")
    public CosmoObjektSyncService.Result sync(HttpServletRequest request, HttpSession session) {
        admin.requireAdmin(request, session);
        return sync.sync();
    }
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<java.util.Map<String, String>> failed() {
        return ResponseEntity.status(503).body(java.util.Map.of("message", "COSMO 동기화에 실패했습니다. 기존 저장 데이터는 유지됩니다."));
    }
}
