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
                .andExpect(content().json("{\"email\":\"owner@example.com\"}"))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://privy.test/api/v1/passwordless/authenticate"))
                .andExpect(header("privy-ca-id", "client-id"))
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
