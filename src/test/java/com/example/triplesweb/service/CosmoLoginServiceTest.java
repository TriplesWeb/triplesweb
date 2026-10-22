package com.example.triplesweb.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class CosmoLoginServiceTest {
    private final CosmoTokenProvider tokens = mock(CosmoTokenProvider.class);
    private final ObjectMapper mapper = new ObjectMapper();

    @Test void emailOtpExchangesPrivySessionForCosmoCredentials() {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://privy.test/api/v1/passwordless/init"))
                .andExpect(header("privy-app-id", "app-id"))
                .andExpect(header("privy-ca-id", "client-id"))
                .andExpect(header("User-Agent", "okhttp/4.9.2"))
                .andExpect(header("Origin", "https://privy.test"))
                .andExpect(header("privy-client", "@privy-io/js-sdk-core:0.60.7.0.1"))
                .andExpect(content().json("{\"email\":\"owner@example.com\"}"))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://privy.test/api/v1/passwordless/authenticate"))
                .andExpect(header("privy-ca-id", "client-id"))
                .andExpect(header("User-Agent", "okhttp/4.9.2"))
                .andExpect(header("Origin", "https://privy.test"))
                .andExpect(header("privy-client", "@privy-io/js-sdk-core:0.60.7.0.1"))
                .andExpect(content().json("{\"email\":\"owner@example.com\",\"code\":\"123456\",\"mode\":\"login-or-sign-up\"}"))
                .andRespond(withSuccess("{\"token\":\"privy-session\"}", MediaType.APPLICATION_JSON));
        var service = new CosmoLoginService(builder.build(), mapper, tokens, "https://privy.test", "app-id");
        service.sendCode("owner@example.com", "client-id");
        service.verifyCode("owner@example.com", "123456", "client-id");
        verify(tokens).loginWithPrivy("privy-session");
        server.verify();
    }

    @Test void rejectsProviderFailureWithoutExposingOtpOrResponseBody() {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://privy.test/api/v1/passwordless/authenticate"))
                .andRespond(withBadRequest().body("secret-session-body"));
        var service = new CosmoLoginService(builder.build(), mapper, tokens, "https://privy.test", "app-id");
        assertThatThrownBy(() -> service.verifyCode("owner@example.com", "123456", "client-id"))
                .hasMessageContaining("400").hasMessageNotContaining("secret-session-body")
                .hasMessageNotContaining("123456").hasMessageNotContaining("owner@example.com");
        verifyNoInteractions(tokens);
        server.verify();
    }

    @Test void forbiddenIsReportedAsIntegrationFailureWithoutLeakingProviderBody() {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://privy.test/api/v1/passwordless/init"))
                .andRespond(withStatus(org.springframework.http.HttpStatus.FORBIDDEN).body("private-provider-body"));
        var service = new CosmoLoginService(builder.build(), mapper, tokens, "https://privy.test", "app-id");
        assertThatThrownBy(() -> service.sendCode("owner@example.com", "client-id"))
                .hasMessageContaining("접근을 거부").hasMessageContaining("403")
                .hasMessageNotContaining("private-provider-body").hasMessageNotContaining("owner@example.com");
        verifyNoInteractions(tokens);
        server.verify();
    }

    @Test void distinguishesHtmlBlockFromJsonProviderRejection() {
        for (boolean html : new boolean[]{true, false}) {
            var builder = RestClient.builder();
            var server = MockRestServiceServer.bindTo(builder).build();
            server.expect(requestTo("https://privy.test/api/v1/passwordless/init"))
                    .andRespond(withStatus(org.springframework.http.HttpStatus.FORBIDDEN)
                            .contentType(html ? MediaType.TEXT_HTML : MediaType.APPLICATION_JSON)
                            .body(html ? "<html>private-block-body</html>"
                                    : "{\"code\":\"invalid_origin\",\"error\":\"private-provider-body\"}"));
            var service = new CosmoLoginService(builder.build(), mapper, tokens, "https://privy.test", "app-id");
            assertThatThrownBy(() -> service.sendCode("owner@example.com", "client-id"))
                    .hasMessageContaining(html ? "HTML 차단 페이지" : "invalid_origin")
                    .hasMessageNotContaining("private-provider-body").hasMessageNotContaining("private-block-body");
            server.verify();
        }
    }

    @Test void unknownProviderCodeIsNotExposed() {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://privy.test/api/v1/passwordless/init"))
                .andRespond(withStatus(org.springframework.http.HttpStatus.FORBIDDEN)
                        .contentType(MediaType.APPLICATION_JSON).body("{\"code\":\"secret-token\"}"));
        var service = new CosmoLoginService(builder.build(), mapper, tokens, "https://privy.test", "app-id");
        assertThatThrownBy(() -> service.sendCode("owner@example.com", "client-id"))
                .hasMessageContaining("JSON").hasMessageNotContaining("secret-token");
        server.verify();
    }

    @Test void realTransportSendsOriginAndClientHeaders() throws Exception {
        var received = new java.util.concurrent.atomic.AtomicReference<com.sun.net.httpserver.Headers>();
        var server = com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/v1/passwordless/init", exchange -> {
            received.set(exchange.getRequestHeaders());
            exchange.getRequestBody().readAllBytes();
            byte[] response = "{}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            try (var output = exchange.getResponseBody()) { output.write(response); }
        });
        server.start();
        try {
            String origin = "http://127.0.0.1:" + server.getAddress().getPort();
            // Use the production constructor/factory, not MockRestServiceServer.
            var service = new CosmoLoginService(RestClient.builder(), mapper, tokens, origin, "app-id");
            service.sendCode("owner@example.com", "client-id");
            assertThat(received.get().getFirst("Origin")).isEqualTo(origin);
            assertThat(received.get().getFirst("User-Agent")).isEqualTo("okhttp/4.9.2");
            assertThat(received.get().getFirst("privy-app-id")).isEqualTo("app-id");
            assertThat(received.get().getFirst("privy-ca-id")).isEqualTo("client-id");
        } finally { server.stop(0); }
    }

    @Test void supportsPrivyAccessTokenField() {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://privy.test/api/v1/passwordless/authenticate"))
                .andRespond(withSuccess("{\"privy_access_token\":\"session\"}", MediaType.APPLICATION_JSON));
        new CosmoLoginService(builder.build(), mapper, tokens, "https://privy.test", "app-id")
                .verifyCode("owner@example.com", "123456", "client-id");
        verify(tokens).loginWithPrivy("session");
        server.verify();
    }
}
