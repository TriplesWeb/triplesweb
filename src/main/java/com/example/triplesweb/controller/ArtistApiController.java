package com.example.triplesweb.controller;

import com.example.triplesweb.dto.ArtistAdminDto;
import com.example.triplesweb.dto.GroupIntroDto;
import com.example.triplesweb.service.ArtistAdminService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/api/artist")
@RequiredArgsConstructor
public class ArtistApiController {

    public static final String SESSION_ADMIN = "ARTIST_ADMIN";
    public static final String HEADER_ADMIN_TOKEN = "X-Artist-Admin-Token";
    private static final Duration TOKEN_TTL = Duration.ofHours(12);

    private final ArtistAdminService artistAdminService;
    private final ConcurrentHashMap<String, Instant> adminTokens = new ConcurrentHashMap<>();

    /** 케미와 동일 비밀번호를 쓴다 (CHEMISTRY_ADMIN_PASSWORD). */
    @Value("${chemistry.admin.password:}")
    private String adminPassword;

    @GetMapping("/admin/status")
    public Map<String, Object> adminStatus(HttpServletRequest request, HttpSession session) {
        boolean configured = StringUtils.hasText(adminPassword);
        boolean admin = configured && isAdmin(request, session);
        return Map.of("configured", configured, "admin", admin);
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

    @GetMapping("/units")
    public List<ArtistAdminDto.UnitOption> units() {
        return artistAdminService.listUnitOptions();
    }

    @GetMapping("/group")
    public GroupIntroDto group() {
        return artistAdminService.getGroupIntro();
    }

    @PostMapping("/group")
    public GroupIntroDto updateGroup(
            @RequestParam(required = false, defaultValue = "") String title,
            @RequestParam(required = false, defaultValue = "") String body,
            @RequestParam(required = false) MultipartFile image,
            @RequestParam(required = false) MultipartFile tabImage,
            @RequestParam(required = false, defaultValue = "false") boolean clearImage,
            @RequestParam(required = false, defaultValue = "false") boolean clearTabImage,
            HttpServletRequest request,
            HttpSession session
    ) {
        requireAdmin(request, session);
        try {
            return artistAdminService.updateGroupIntro(title, body, image, tabImage, clearImage, clearTabImage);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    @GetMapping("/{id:[0-9]+}")
    public ArtistAdminDto get(@PathVariable long id) {
        try {
            return artistAdminService.getArtist(id);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        }
    }

    @PostMapping("/{id:[0-9]+}")
    public ArtistAdminDto update(
            @PathVariable long id,
            @RequestParam String name,
            @RequestParam String engName,
            @RequestParam String nationality,
            @RequestParam String birthDate,
            @RequestParam String birthPlace,
            @RequestParam String debutDate,
            @RequestParam String color,
            @RequestParam(required = false, defaultValue = "") String soloContent,
            @RequestParam(required = false, defaultValue = "") String broadcastContent,
            @RequestParam(required = false) List<Long> unitIds,
            @RequestParam(required = false) MultipartFile image,
            @RequestParam(required = false, defaultValue = "false") boolean clearImage,
            HttpServletRequest request,
            HttpSession session
    ) {
        requireAdmin(request, session);
        try {
            List<Long> ids = unitIds != null ? unitIds : new ArrayList<>();
            return artistAdminService.updateArtist(
                    id, name, engName, nationality, birthDate, birthPlace, debutDate, color,
                    soloContent, broadcastContent, ids, image, clearImage
            );
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
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
        if (exp == null) return false;
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
            if (e.getValue().isBefore(now)) it.remove();
        }
    }
}
