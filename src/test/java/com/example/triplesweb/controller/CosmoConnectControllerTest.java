package com.example.triplesweb.controller;

import com.example.triplesweb.config.MustacheConfig;
import com.example.triplesweb.service.AboutAdminAuth;
import com.example.triplesweb.service.CosmoLoginService;
import com.example.triplesweb.service.CosmoTokenProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = CosmoConnectController.class, properties = "chemistry.admin.password=test-password")
@AutoConfigureMockMvc(addFilters = false)
@Import({AboutAdminAuth.class, MustacheConfig.class})
class CosmoConnectControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean CosmoLoginService login;
    @MockitoBean CosmoTokenProvider tokens;

    @Test void anonymousCannotSendCodesOrReplaceCredentials() throws Exception {
        mvc.perform(post("/api/gravity/connect/email").header("X-Gravity-Connect", "1")
                .contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"owner@example.com\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/gravity/connect/verify").header("X-Gravity-Connect", "1")
                .contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"123456\"}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(login);
    }

    @Test void loginSendAndVerifyReturnsOnlyConnectionStatus() throws Exception {
        var session = new MockHttpSession();
        mvc.perform(post("/api/gravity/connect/admin-login").session(session).header("X-Gravity-Connect", "1")
                .contentType(MediaType.APPLICATION_JSON).content("{\"password\":\"test-password\"}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/gravity/connect/email").session(session).header("X-Gravity-Connect", "1")
                .contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"owner@example.com\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.sent").value(true));
        mvc.perform(post("/api/gravity/connect/verify").session(session).header("X-Gravity-Connect", "1")
                .contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"123456\"}"))
                .andExpect(status().isOk()).andExpect(content().json("{\"connected\":true}", true));
        verify(login).sendCode(eq("owner@example.com"), anyString());
        verify(login).verifyCode(eq("owner@example.com"), eq("123456"), anyString());
        // One-time challenge cannot be replayed.
        mvc.perform(post("/api/gravity/connect/verify").session(session).header("X-Gravity-Connect", "1")
                .contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"123456\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test void sameOriginHeaderAndSessionChallengeAreRequired() throws Exception {
        var session = adminSession();
        mvc.perform(post("/api/gravity/connect/email").session(session)
                .contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"owner@example.com\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/gravity/connect/verify").session(session).header("X-Gravity-Connect", "1")
                .contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"123456\"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(login);
    }

    @Test void resendCooldownAndAttemptLimitAreEnforced() throws Exception {
        var session = adminSession();
        mvc.perform(post("/api/gravity/connect/email").session(session).header("X-Gravity-Connect", "1")
                .contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"owner@example.com\"}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/gravity/connect/email").session(session).header("X-Gravity-Connect", "1")
                .contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"owner@example.com\"}"))
                .andExpect(status().isTooManyRequests());
        doThrow(new IllegalStateException("인증 실패")).when(login).verifyCode(anyString(), anyString(), anyString());
        for (int attempt = 0; attempt < 5; attempt++) {
            mvc.perform(post("/api/gravity/connect/verify").session(session).header("X-Gravity-Connect", "1")
                    .contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"123456\"}"))
                    .andExpect(status().isServiceUnavailable());
        }
        mvc.perform(post("/api/gravity/connect/verify").session(session).header("X-Gravity-Connect", "1")
                .contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"123456\"}"))
                .andExpect(status().isTooManyRequests());
        verify(login, times(5)).verifyCode(anyString(), anyString(), anyString());
    }

    @Test void nullInputsAreRejectedWithoutCallingUpstream() throws Exception {
        var session = adminSession();
        mvc.perform(post("/api/gravity/connect/email").session(session).header("X-Gravity-Connect", "1")
                .contentType(MediaType.APPLICATION_JSON).content("{\"email\":null}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/gravity/connect/verify").session(session).header("X-Gravity-Connect", "1")
                .contentType(MediaType.APPLICATION_JSON).content("{\"code\":null}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(login);
    }

    @Test void rendersConnectionScreen() throws Exception {
        mvc.perform(get("/cosmo/gravity/connect")).andExpect(status().isOk())
                .andExpect(content().string(containsString("COSMO 계정 연결")));
    }

    private MockHttpSession adminSession() {
        var session = new MockHttpSession();
        session.setAttribute(AboutAdminAuth.SESSION_ADMIN, true);
        return session;
    }
}
