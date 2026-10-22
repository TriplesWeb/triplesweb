package com.example.triplesweb.service;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ABOUT 트리플에스(케미/아티스트/앨범/유닛) 공통 관리자 인증.
 * 한 곳에서 로그인하면 세션·토큰이 공유된다.
 */
@Service
public class AboutAdminAuth {

    public static final String SESSION_ADMIN = "ABOUT_ADMIN";
    public static final String HEADER_ADMIN_TOKEN = "X-About-Admin-Token";
    private static final Duration TOKEN_TTL = Duration.ofHours(12);

    private static final String[] LEGACY_SESSION_KEYS = {
            "CHEMISTRY_ADMIN", "ARTIST_ADMIN", "ALBUM_ADMIN", "UNIT_ADMIN"
    };
    private static final String[] LEGACY_TOKEN_HEADERS = {
            "X-Chemistry-Admin-Token",
            "X-Artist-Admin-Token",
            "X-Album-Admin-Token",
            "X-Unit-Admin-Token"
    };

    private final ConcurrentHashMap<String, Instant> adminTokens = new ConcurrentHashMap<>();

    @Value("${chemistry.admin.password:}")
    private String adminPassword;

    public boolean isConfigured() {
        return StringUtils.hasText(adminPassword);
    }

    public Map<String, Object> status(HttpServletRequest request, HttpSession session) {
        boolean configured = isConfigured();
        boolean admin = configured && isAdmin(request, session);
        return Map.of("configured", configured, "admin", admin);
    }

    public Map<String, Object> login(Map<String, String> body, HttpSession session) {
        if (!isConfigured()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "관리자 비밀번호가 설정되지 않았습니다.");
        }
        String password = body != null ? body.get("password") : null;
        if (!adminPassword.equals(password)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "비밀번호가 올바르지 않습니다.");
        }
        purgeExpiredTokens();
        session.setAttribute(SESSION_ADMIN, true);
        for (String legacy : LEGACY_SESSION_KEYS) {
            session.setAttribute(legacy, true);
        }
        String token = UUID.randomUUID().toString().replace("-", "");
        adminTokens.put(token, Instant.now().plus(TOKEN_TTL));
        return Map.of("admin", true, "token", token);
    }

    public Map<String, Object> logout(HttpServletRequest request, HttpSession session) {
        session.removeAttribute(SESSION_ADMIN);
        for (String legacy : LEGACY_SESSION_KEYS) {
            session.removeAttribute(legacy);
        }
        String token = extractToken(request);
        if (StringUtils.hasText(token)) {
            adminTokens.remove(token);
        }
        return Map.of("admin", false);
    }

    public void requireAdmin(HttpServletRequest request, HttpSession session) {
        if (!isConfigured() || !isAdmin(request, session)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "관리자만 가능합니다. 다시 로그인해 주세요.");
        }
    }

    public boolean isAdmin(HttpServletRequest request, HttpSession session) {
        if (Boolean.TRUE.equals(session.getAttribute(SESSION_ADMIN))) {
            return true;
        }
        for (String legacy : LEGACY_SESSION_KEYS) {
            if (Boolean.TRUE.equals(session.getAttribute(legacy))) {
                session.setAttribute(SESSION_ADMIN, true);
                return true;
            }
        }
        String token = extractToken(request);
        return StringUtils.hasText(token) && isValidToken(token);
    }

    private String extractToken(HttpServletRequest request) {
        String header = request.getHeader(HEADER_ADMIN_TOKEN);
        if (StringUtils.hasText(header)) {
            return header.trim();
        }
        for (String legacy : LEGACY_TOKEN_HEADERS) {
            String value = request.getHeader(legacy);
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
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
