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
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ABOUT 트리플에스(케미/아티스트/앨범/유닛) 공통 관리자 인증.
 * HttpOnly 세션 쿠키만 사용한다. JS에 노출되는 토큰은 발급하지 않는다.
 */
@Service
public class AboutAdminAuth {

    public static final String SESSION_ADMIN = "ABOUT_ADMIN";

    private static final String[] LEGACY_SESSION_KEYS = {
            "CHEMISTRY_ADMIN", "ARTIST_ADMIN", "ALBUM_ADMIN", "UNIT_ADMIN"
    };

    private static final int MAX_LOGIN_ATTEMPTS = 5;
    private static final Duration LOGIN_WINDOW = Duration.ofMinutes(15);

    private final ConcurrentHashMap<String, LoginWindow> loginAttempts = new ConcurrentHashMap<>();

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

    public Map<String, Object> login(Map<String, String> body, HttpServletRequest request, HttpSession session) {
        if (!isConfigured()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "관리자 비밀번호가 설정되지 않았습니다.");
        }
        String ip = clientIp(request);
        assertNotRateLimited(ip);
        String password = body != null ? body.get("password") : null;
        if (!adminPassword.equals(password)) {
            recordFailure(ip);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "비밀번호가 올바르지 않습니다.");
        }
        clearFailures(ip);
        session.setAttribute(SESSION_ADMIN, true);
        for (String legacy : LEGACY_SESSION_KEYS) {
            session.setAttribute(legacy, true);
        }
        return Map.of("admin", true);
    }

    public Map<String, Object> logout(HttpServletRequest request, HttpSession session) {
        session.removeAttribute(SESSION_ADMIN);
        for (String legacy : LEGACY_SESSION_KEYS) {
            session.removeAttribute(legacy);
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
        return false;
    }

    private void assertNotRateLimited(String ip) {
        LoginWindow window = loginAttempts.get(ip);
        if (window == null) {
            return;
        }
        if (window.expiresAt().isBefore(Instant.now())) {
            loginAttempts.remove(ip, window);
            return;
        }
        if (window.failures() >= MAX_LOGIN_ATTEMPTS) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "로그인 시도가 너무 많습니다. 잠시 후 다시 시도해 주세요.");
        }
    }

    private void recordFailure(String ip) {
        loginAttempts.compute(ip, (key, current) -> {
            Instant now = Instant.now();
            if (current == null || current.expiresAt().isBefore(now)) {
                return new LoginWindow(1, now.plus(LOGIN_WINDOW));
            }
            return new LoginWindow(current.failures() + 1, current.expiresAt());
        });
    }

    private void clearFailures(String ip) {
        loginAttempts.remove(ip);
    }

    private static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(forwarded)) {
            int comma = forwarded.indexOf(',');
            return (comma > 0 ? forwarded.substring(0, comma) : forwarded).trim();
        }
        String remote = request.getRemoteAddr();
        return StringUtils.hasText(remote) ? remote : "unknown";
    }

    private record LoginWindow(int failures, Instant expiresAt) {}
}
