package com.example.triplesweb.service;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ABOUT 트리플에스(케미/아티스트/앨범/유닛) 공통 관리자 인증.
 * 한 곳에서 로그인하면 세션·토큰이 공유된다.
 * 토큰은 비밀번호 기반 HMAC으로 서명되어 서버 재시작 후에도 TTL 동안 유효하다.
 */
@Service
public class AboutAdminAuth {

    public static final String SESSION_ADMIN = "ABOUT_ADMIN";
    public static final String HEADER_ADMIN_TOKEN = "X-About-Admin-Token";
    private static final Duration TOKEN_TTL = Duration.ofHours(12);
    private static final String TOKEN_PREFIX = "a1.";

    private static final String[] LEGACY_SESSION_KEYS = {
            "CHEMISTRY_ADMIN", "ARTIST_ADMIN", "ALBUM_ADMIN", "UNIT_ADMIN"
    };
    private static final String[] LEGACY_TOKEN_HEADERS = {
            "X-Chemistry-Admin-Token",
            "X-Artist-Admin-Token",
            "X-Album-Admin-Token",
            "X-Unit-Admin-Token"
    };

    /** 로그아웃으로 폐기한 서명 토큰 (재시작 시 비워짐 — 그때는 어차피 재로그인). */
    private final ConcurrentHashMap<String, Instant> revokedTokens = new ConcurrentHashMap<>();

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
        purgeRevoked();
        session.setAttribute(SESSION_ADMIN, true);
        for (String legacy : LEGACY_SESSION_KEYS) {
            session.setAttribute(legacy, true);
        }
        return Map.of("admin", true, "token", issueToken());
    }

    public Map<String, Object> logout(HttpServletRequest request, HttpSession session) {
        session.removeAttribute(SESSION_ADMIN);
        for (String legacy : LEGACY_SESSION_KEYS) {
            session.removeAttribute(legacy);
        }
        String token = extractToken(request);
        if (StringUtils.hasText(token)) {
            revokedTokens.put(token, Instant.now().plus(TOKEN_TTL));
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

    private String issueToken() {
        long exp = Instant.now().plus(TOKEN_TTL).getEpochSecond();
        String payload = Long.toString(exp);
        return TOKEN_PREFIX + payload + "." + sign(payload);
    }

    private boolean isValidToken(String token) {
        if (revokedTokens.containsKey(token)) {
            return false;
        }
        if (!token.startsWith(TOKEN_PREFIX)) {
            return false;
        }
        String body = token.substring(TOKEN_PREFIX.length());
        int dot = body.indexOf('.');
        if (dot <= 0 || dot == body.length() - 1) {
            return false;
        }
        String payload = body.substring(0, dot);
        String signature = body.substring(dot + 1);
        long exp;
        try {
            exp = Long.parseLong(payload);
        } catch (NumberFormatException e) {
            return false;
        }
        if (Instant.ofEpochSecond(exp).isBefore(Instant.now())) {
            return false;
        }
        String expected = sign(payload);
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                signature.getBytes(StandardCharsets.UTF_8)
        );
    }

    private String sign(String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(adminPassword.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] raw = mac.doFinal(("about-admin|" + payload).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(raw);
        } catch (Exception e) {
            throw new IllegalStateException("관리자 토큰 서명에 실패했습니다.", e);
        }
    }

    private void purgeRevoked() {
        Instant now = Instant.now();
        revokedTokens.entrySet().removeIf(e -> e.getValue().isBefore(now));
    }
}
