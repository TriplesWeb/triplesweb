package com.example.triplesweb.controller;

import com.example.triplesweb.service.AboutAdminAuth;
import com.example.triplesweb.service.CosmoLoginService;
import com.example.triplesweb.service.CosmoTokenProvider;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Controller
public class CosmoConnectController {
    private static final String CHALLENGE = "COSMO_EMAIL_CHALLENGE";
    private final AboutAdminAuth admin;
    private final CosmoLoginService login;
    private final CosmoTokenProvider tokens;

    public CosmoConnectController(AboutAdminAuth admin, CosmoLoginService login, CosmoTokenProvider tokens) {
        this.admin = admin;
        this.login = login;
        this.tokens = tokens;
    }

    @GetMapping("/cosmo/gravity/connect")
    public String page() { return "cosmo/gravity-connect"; }

    @GetMapping("/api/gravity/connect/status")
    @ResponseBody
    public Map<String, Object> status(HttpServletRequest request, HttpSession session) {
        Map<String, Object> result = new LinkedHashMap<>(admin.status(request, session));
        if (Boolean.TRUE.equals(result.get("admin"))) result.put("connected", tokens.isConfigured());
        return result;
    }

    @PostMapping("/api/gravity/connect/admin-login")
    @ResponseBody
    public Map<String, Object> adminLogin(@RequestBody Map<String, String> body,
            @RequestHeader(name = "X-Gravity-Connect", defaultValue = "") String sameOrigin,
            HttpSession session) {
        requireSameOriginHeader(sameOrigin);
        return admin.login(body, session);
    }

    @PostMapping("/api/gravity/connect/admin-logout")
    @ResponseBody
    public Map<String, Object> adminLogout(
            @RequestHeader(name = "X-Gravity-Connect", defaultValue = "") String sameOrigin,
            HttpServletRequest request, HttpSession session) {
        requireSameOriginHeader(sameOrigin);
        session.removeAttribute(CHALLENGE);
        return admin.logout(request, session);
    }

    @PostMapping("/api/gravity/connect/email")
    @ResponseBody
    public Map<String, Object> email(@RequestBody Map<String, String> body,
            @RequestHeader(name = "X-Gravity-Connect", defaultValue = "") String sameOrigin,
            HttpServletRequest request, HttpSession session) {
        requireSameOriginHeader(sameOrigin);
        admin.requireAdmin(request, session);
        String email = java.util.Objects.toString(body.get("email"), "").trim();
        if (email.length() > 254 || !email.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "올바른 이메일 주소를 입력해 주세요.");
        }
        synchronized (session) {
            Challenge previous = (Challenge) session.getAttribute(CHALLENGE);
            if (previous != null && previous.sentAt().plusSeconds(60).isAfter(Instant.now())) {
                long seconds = Math.max(1, (java.time.Duration.between(Instant.now(), previous.sentAt().plusSeconds(60)).toMillis() + 999) / 1000);
                throw new EmailCooldownException(seconds);
            }
            Challenge challenge = new Challenge(email, UUID.randomUUID().toString(), Instant.now(), 0);
            login.sendCode(email, challenge.clientId());
            // A failed delivery must not create a usable challenge or a resend cooldown.
            session.setAttribute(CHALLENGE, new Challenge(email, challenge.clientId(), Instant.now(), 0));
        }
        return Map.of("sent", true, "expiresIn", 600);
    }

    @PostMapping("/api/gravity/connect/verify")
    @ResponseBody
    public Map<String, Object> verify(@RequestBody Map<String, String> body,
            @RequestHeader(name = "X-Gravity-Connect", defaultValue = "") String sameOrigin,
            HttpServletRequest request, HttpSession session) {
        requireSameOriginHeader(sameOrigin);
        admin.requireAdmin(request, session);
        String code = java.util.Objects.toString(body.get("code"), "").trim();
        if (!code.matches("[0-9]{6}")) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "6자리 인증번호를 입력해 주세요.");
        synchronized (session) {
            Challenge challenge = (Challenge) session.getAttribute(CHALLENGE);
            if (challenge == null || challenge.sentAt().plusSeconds(600).isBefore(Instant.now())) {
                session.removeAttribute(CHALLENGE);
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "이메일 인증번호를 다시 요청해 주세요.");
            }
            if (challenge.attempts() >= 5) throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "인증 시도 횟수를 초과했습니다. 인증번호를 다시 요청해 주세요.");
            session.setAttribute(CHALLENGE, new Challenge(challenge.email(), challenge.clientId(), challenge.sentAt(), challenge.attempts() + 1));
            login.verifyCode(challenge.email(), code, challenge.clientId());
            session.removeAttribute(CHALLENGE);
        }
        return Map.of("connected", true);
    }

    private static void requireSameOriginHeader(String value) {
        // JSON plus a custom header requires CORS preflight from a different origin.
        if (!"1".equals(value)) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
    }

    @ExceptionHandler(IllegalStateException.class)
    @ResponseBody
    public ResponseEntity<Map<String, String>> error(IllegalStateException error) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of("message", error.getMessage()));
    }

    @ExceptionHandler(EmailCooldownException.class)
    @ResponseBody
    public ResponseEntity<Map<String, Object>> cooldown(EmailCooldownException error) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header("Retry-After", Long.toString(error.seconds))
                .body(Map.of("message", "인증번호는 " + error.seconds + "초 후 다시 요청할 수 있습니다.",
                        "retryAfterSeconds", error.seconds));
    }

    private static class EmailCooldownException extends RuntimeException {
        private final long seconds;
        EmailCooldownException(long seconds) { this.seconds = seconds; }
    }

    @ExceptionHandler(ResponseStatusException.class)
    @ResponseBody
    public ResponseEntity<Map<String, String>> requestError(ResponseStatusException error) {
        String message = error.getReason();
        if (message == null) message = "요청을 처리할 수 없습니다.";
        return ResponseEntity.status(error.getStatusCode()).body(Map.of("message", message));
    }

    private record Challenge(String email, String clientId, Instant sentAt, int attempts) { }
}
