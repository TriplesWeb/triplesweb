package com.example.triplesweb.controller;

import com.example.triplesweb.dto.ChemistryDto;
import com.example.triplesweb.service.ChemistryService;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/chemistry")
@RequiredArgsConstructor
public class ChemistryApiController {

    public static final String SESSION_ADMIN = "CHEMISTRY_ADMIN";

    private final ChemistryService chemistryService;

    @Value("${chemistry.admin.password:}")
    private String adminPassword;

    @GetMapping
    public List<ChemistryDto> list() {
        return chemistryService.findAll();
    }

    @GetMapping("/admin/status")
    public Map<String, Object> adminStatus(HttpSession session) {
        boolean configured = StringUtils.hasText(adminPassword);
        boolean admin = Boolean.TRUE.equals(session.getAttribute(SESSION_ADMIN));
        return Map.of(
                "configured", configured,
                "admin", admin && configured
        );
    }

    @PostMapping("/admin/login")
    public Map<String, Object> login(@RequestBody Map<String, String> body, HttpSession session) {
        if (!StringUtils.hasText(adminPassword)) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "관리자 비밀번호가 설정되지 않았습니다.");
        }
        String password = body != null ? body.get("password") : null;
        if (!adminPassword.equals(password)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "비밀번호가 올바르지 않습니다.");
        }
        session.setAttribute(SESSION_ADMIN, true);
        return Map.of("admin", true);
    }

    @PostMapping("/admin/logout")
    public Map<String, Object> logout(HttpSession session) {
        session.removeAttribute(SESSION_ADMIN);
        return Map.of("admin", false);
    }

    @PostMapping
    public ChemistryDto upsert(
            @RequestParam String member1,
            @RequestParam String member2,
            @RequestParam String label,
            @RequestParam(required = false, defaultValue = "") String description,
            @RequestParam(required = false) MultipartFile image,
            @RequestParam(required = false, defaultValue = "false") boolean clearImage,
            @RequestParam(required = false, defaultValue = "") String linksJson,
            HttpSession session
    ) {
        requireAdmin(session);
        try {
            return chemistryService.upsert(member1, member2, label, description, image, clearImage, linksJson);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id, HttpSession session) {
        requireAdmin(session);
        chemistryService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping
    public ResponseEntity<Void> deleteByMembers(
            @RequestParam String member1,
            @RequestParam String member2,
            HttpSession session
    ) {
        requireAdmin(session);
        try {
            chemistryService.deleteByMembers(member1, member2);
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    private void requireAdmin(HttpSession session) {
        if (!StringUtils.hasText(adminPassword)
                || !Boolean.TRUE.equals(session.getAttribute(SESSION_ADMIN))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "관리자만 가능합니다.");
        }
    }
}
