package com.example.triplesweb.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.util.Map;

/** Passwordless email -> Privy session -> COSMO account credentials. No wallet access. */
@Service
public class CosmoLoginService {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(CosmoLoginService.class);
    // Match cosmo-tui's email login client; the provider can filter generic client identifiers.
    private static final String LOGIN_USER_AGENT = "okhttp/4.9.2";
    private static final Map<String, String> PROVIDER_ERRORS = Map.of(
            "invalid_origin", "인증 요청 출처가 허용되지 않았습니다.",
            "origin_not_allowed", "인증 요청 출처가 허용되지 않았습니다.",
            "missing_or_invalid_privy_app_id", "인증 앱 설정을 확인해야 합니다.",
            "invalid_data", "이메일 또는 인증번호의 형식을 확인해 주세요.",
            "invalid_credentials", "이메일 또는 인증번호를 확인해 주세요.",
            "invalid_code", "인증번호가 올바르지 않거나 만료되었습니다.",
            "expired_code", "인증번호가 만료되었습니다. 다시 요청해 주세요.");
    private final RestClient client;
    private final ObjectMapper mapper;
    private final CosmoTokenProvider tokens;
    private final String privyUrl;
    private final String appId;

    @org.springframework.beans.factory.annotation.Autowired
    public CosmoLoginService(RestClient.Builder builder, ObjectMapper mapper, CosmoTokenProvider tokens,
            @Value("${gravity.privy-base-url:https://auth.privy.io}") String privyUrl,
            @Value("${gravity.privy-app-id:cm7tt02g000a96m8uislrblbe}") String appId) {
        this(builder.clone().requestFactory(loginRequestFactory()).build(), mapper, tokens, privyUrl, appId);
    }

    private static org.springframework.http.client.JdkClientHttpRequestFactory loginRequestFactory() {
        // HttpURLConnection silently drops Origin as a restricted header.
        var http = java.net.http.HttpClient.newBuilder()
                .connectTimeout(java.time.Duration.ofSeconds(5))
                .version(java.net.http.HttpClient.Version.HTTP_1_1)
                .build();
        var factory = new org.springframework.http.client.JdkClientHttpRequestFactory(http);
        factory.setReadTimeout(java.time.Duration.ofSeconds(10));
        return factory;
    }

    CosmoLoginService(RestClient client, ObjectMapper mapper, CosmoTokenProvider tokens, String privyUrl, String appId) {
        this.client = client;
        this.mapper = mapper;
        this.tokens = tokens;
        this.privyUrl = privyUrl.replaceAll("/+$", "");
        this.appId = appId;
    }

    public void sendCode(String email, String clientId) {
        post("/api/v1/passwordless/init", Map.of("email", email), clientId);
    }

    public void verifyCode(String email, String code, String clientId) {
        JsonNode response = post("/api/v1/passwordless/authenticate", Map.of(
                "email", email, "code", code, "mode", "login-or-sign-up"), clientId);
        String token = response.path("token").asText("");
        if (token.isBlank()) token = response.path("privy_access_token").asText("");
        if (token.isBlank()) throw new IllegalStateException("이메일 인증을 완료하지 못했습니다. 다시 시도해 주세요.");
        tokens.loginWithPrivy(token);
    }

    private JsonNode post(String path, Map<String, String> payload, String clientId) {
        try {
            return client.post().uri(privyUrl + path)
                    .header("privy-app-id", appId)
                    .header("privy-client", "@privy-io/js-sdk-core:0.60.7.0.1")
                    .header("privy-ca-id", clientId)
                    .header("Origin", privyUrl)
                    .header("User-Agent", LOGIN_USER_AGENT)
                    .accept(MediaType.APPLICATION_JSON).contentType(MediaType.APPLICATION_JSON).body(payload)
                    .exchange((request, reply) -> {
                        if (!reply.getStatusCode().is2xxSuccessful()) {
                            // Never expose/log provider messages, emails, OTPs, or session tokens.
                            String body = new String(reply.getBody().readNBytes(16_384), StandardCharsets.UTF_8);
                            throw providerFailure(path, reply.getStatusCode().value(),
                                    reply.getHeaders().getFirst("Content-Type"), body);
                        }
                        String body = new String(reply.getBody().readAllBytes(), StandardCharsets.UTF_8);
                        return body.isBlank() ? mapper.createObjectNode() : mapper.readTree(body);
                    });
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("이메일 인증 서버에 연결하지 못했습니다. 잠시 후 다시 시도해 주세요.");
        }
    }
    private IllegalStateException providerFailure(String path, int status, String contentType, String body) {
        String kind = "other", code = "unknown";
        try {
            JsonNode error = mapper.readTree(body);
            if (error != null && error.isObject()) {
                kind = "json";
                String candidate = error.path("code").asText("");
                if (candidate.isBlank()) candidate = error.path("error").path("code").asText("");
                if (PROVIDER_ERRORS.containsKey(candidate)) code = candidate;
            }
        } catch (Exception ignored) { /* HTML/text responses are handled below. */ }
        if (!"json".equals(kind) && ((contentType != null && contentType.toLowerCase(java.util.Locale.ROOT).contains("text/html"))
                || body.stripLeading().startsWith("<"))) kind = "html";
        log.warn("COSMO email authentication failed: endpoint={}, status={}, responseType={}, code={}",
                path, status, kind, code);
        String detail = PROVIDER_ERRORS.get(code);
        if (detail == null) detail = status == 429 ? "인증 서버의 요청 제한에 걸렸습니다. 잠시 후 다시 시도해 주세요."
                : status == 403 && "html".equals(kind) ? "인증 서버가 접근을 거부했습니다. HTML 차단 페이지가 반환되었습니다."
                : status == 403 ? "인증 서버가 접근을 거부했습니다. 인증 서버의 거부 사유 확인이 필요합니다."
                : "인증 요청을 완료하지 못했습니다. 잠시 후 다시 시도해 주세요.";
        String label = "json".equals(kind) ? "JSON" : "html".equals(kind) ? "HTML" : "기타 응답";
        return new IllegalStateException("이메일 인증 요청 실패 (HTTP " + status + ", " + label
                + ("unknown".equals(code) ? "" : ", " + code) + "). " + detail);
    }

}
