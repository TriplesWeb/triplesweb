package com.example.triplesweb.controller;

import com.example.triplesweb.dto.ChemistryDto;
import com.example.triplesweb.service.ChemistrySeedService;
import com.example.triplesweb.service.ChemistryService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/api/chemistry")
@RequiredArgsConstructor
public class ChemistryApiController {

    public static final String SESSION_ADMIN = "CHEMISTRY_ADMIN";
    public static final String HEADER_ADMIN_TOKEN = "X-Chemistry-Admin-Token";
    private static final Duration TOKEN_TTL = Duration.ofHours(12);

    private final ChemistryService chemistryService;
    private final ChemistrySeedService chemistrySeedService;
    private final ConcurrentHashMap<String, Instant> adminTokens = new ConcurrentHashMap<>();

    @Value("${chemistry.admin.password:}")
    private String adminPassword;

    @GetMapping
    public List<ChemistryDto> list() {
        return chemistryService.findAll();
    }

    @GetMapping("/admin/status")
    public Map<String, Object> adminStatus(HttpServletRequest request, HttpSession session) {
        boolean configured = StringUtils.hasText(adminPassword);
        boolean admin = configured && isAdmin(request, session);
        return Map.of(
                "configured", configured,
                "admin", admin
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
        purgeExpiredTokens();
        session.setAttribute(SESSION_ADMIN, true);
        String token = UUID.randomUUID().toString().replace("-", "");
        adminTokens.put(token, Instant.now().plus(TOKEN_TTL));
        // 쿠키가 프록시/HTTPS에서 누락돼도 동작하도록 토큰도 반환
        return Map.of("admin", true, "token", token);
    }

    @PostMapping("/admin/logout")
    public Map<String, Object> logout(HttpServletRequest request, HttpSession session) {
        session.removeAttribute(SESSION_ADMIN);
        String token = extractToken(request);
        if (StringUtils.hasText(token)) {
            adminTokens.remove(token);
        }
        return Map.of("admin", false);
    }

    @PostMapping
    public ChemistryDto upsert(
            @RequestParam String member1,
            @RequestParam String member2,
            @RequestParam String label,
            @RequestParam(required = false, defaultValue = "") String labelEn,
            @RequestParam(required = false, defaultValue = "") String description,
            @RequestParam(required = false, defaultValue = "") String descriptionEn,
            @RequestParam(required = false) MultipartFile image,
            @RequestParam(required = false, defaultValue = "false") boolean clearImage,
            @RequestParam(required = false, defaultValue = "") String linksJson,
            @RequestParam(required = false, defaultValue = "NONE") String labelTone,
            HttpServletRequest request,
            HttpSession session
    ) {
        requireAdmin(request, session);
        try {
            return chemistryService.upsert(
                    member1, member2, label, labelEn, description, descriptionEn,
                    image, clearImage, linksJson, labelTone);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id, HttpServletRequest request, HttpSession session) {
        requireAdmin(request, session);
        chemistryService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping
    public ResponseEntity<Void> deleteByMembers(
            @RequestParam String member1,
            @RequestParam String member2,
            HttpServletRequest request,
            HttpSession session
    ) {
        requireAdmin(request, session);
        try {
            chemistryService.deleteByMembers(member1, member2);
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    /** 시드 JSON의 누락 페어를 Artist FK로 재적재 (기존 행은 유지). */
    @PostMapping("/admin/reseed")
    public Map<String, Object> reseed(HttpServletRequest request, HttpSession session) {
        requireAdmin(request, session);
        int inserted = chemistrySeedService.reseedMissingPairs();
        int enFilled = chemistrySeedService.applyEnglishDescriptionsIfBlank();
        return Map.of(
                "inserted", inserted,
                "enFilled", enFilled,
                "total", chemistryService.findAll().size()
        );
    }

    private void requireAdmin(HttpServletRequest request, HttpSession session) {
        if (!StringUtils.hasText(adminPassword) || !isAdmin(request, session)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "관리자만 가능합니다. 다시 로그인해 주세요.");
        }
    }

    private boolean isAdmin(HttpServletRequest request, HttpSession session) {
        if (Boolean.TRUE.equals(session.getAttribute(SESSION_ADMIN))) {
            return true;
        }
        String token = extractToken(request);
        return StringUtils.hasText(token) && isValidToken(token);
    }

    private String extractToken(HttpServletRequest request) {
        String header = request.getHeader(HEADER_ADMIN_TOKEN);
        if (StringUtils.hasText(header)) {
            return header.trim();
        }
        String auth = request.getHeader("Authorization");
        if (StringUtils.hasText(auth) && auth.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return auth.substring(7).trim();
        }
        return null;
    }

    private boolean isValidToken(String token) {
        Instant exp = adminTokens.get(token);
        if (exp == null) {
            return false;
        }
        if (exp.isBefore(Instant.now())) {
            adminTokens.remove(token);
            return false;
        }
        return true;
    }

    private void purgeExpiredTokens() {
        Instant now = Instant.now();
        Iterator<Map.Entry<String, Instant>> it = adminTokens.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, Instant> e = it.next();
            if (e.getValue().isBefore(now)) {
                it.remove();
            }
        }
    }
}
