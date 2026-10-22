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
    private final RestClient client;
    private final ObjectMapper mapper;
    private final CosmoTokenProvider tokens;
    private final String privyUrl;
    private final String appId;

    @org.springframework.beans.factory.annotation.Autowired
    public CosmoLoginService(RestClient.Builder builder, ObjectMapper mapper, CosmoTokenProvider tokens,
            @Value("${gravity.privy-base-url:https://auth.privy.io}") String privyUrl,
            @Value("${gravity.privy-app-id:cm7tt02g000a96m8uislrblbe}") String appId) {
        this(builder.clone().requestFactory(CosmoTokenProvider.requestFactory()).build(), mapper, tokens, privyUrl, appId);
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
                    .header("User-Agent", "triplesweb/1.0")
                    .accept(MediaType.APPLICATION_JSON).contentType(MediaType.APPLICATION_JSON).body(payload)
                    .exchange((request, reply) -> {
                        if (!reply.getStatusCode().is2xxSuccessful()) {
                            throw new IllegalStateException("이메일 인증 요청 실패 (HTTP " + reply.getStatusCode().value()
                                    + "). 인증번호 또는 잠시 후 재시도 여부를 확인해 주세요.");
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
}
