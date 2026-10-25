package com.example.triplesweb.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AboutAdminAuthTest {

    private AboutAdminAuth auth;

    @BeforeEach
    void setUp() {
        auth = new AboutAdminAuth();
        ReflectionTestUtils.setField(auth, "adminPassword", "secret");
    }

    @Test
    void loginUsesSessionOnlyWithoutToken() {
        MockHttpSession session = new MockHttpSession();
        MockHttpServletRequest request = new MockHttpServletRequest();
        Map<String, Object> result = auth.login(Map.of("password", "secret"), request, session);
        assertThat(result.get("admin")).isEqualTo(true);
        assertThat(result.containsKey("token")).isFalse();
        assertThat(auth.isAdmin(request, session)).isTrue();
    }

    @Test
    void loginFailuresAreRateLimited() {
        MockHttpSession session = new MockHttpSession();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("203.0.113.10");
        for (int i = 0; i < 5; i++) {
            assertThatThrownBy(() -> auth.login(Map.of("password", "wrong"), request, session))
                    .isInstanceOf(ResponseStatusException.class)
                    .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                    .isEqualTo(HttpStatus.UNAUTHORIZED);
        }
        assertThatThrownBy(() -> auth.login(Map.of("password", "wrong"), request, session))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    }

    @Test
    void tokenHeaderAloneDoesNotGrantAdmin() {
        MockHttpSession session = new MockHttpSession();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-About-Admin-Token", "a1.ignored");
        assertThat(auth.isAdmin(request, session)).isFalse();
    }
}
