package com.example.triplesweb.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 관리자 세션이 Spring Session JDBC 테이블에 기록되는지 확인한다.
 * Blue-Green 슬롯 전환 후에도 같은 JSESSIONID로 권한을 이어 쓰기 위한 전제.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "chemistry.admin.password=session-jdbc-test",
        "spring.datasource.url=jdbc:h2:mem:admin_session_jdbc;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password="
})
class AboutAdminSessionJdbcTest {

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    @Test
    void adminLoginPersistsSessionRowsInJdbc() throws Exception {
        var login = mvc.perform(post("/api/chemistry/admin/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"session-jdbc-test\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.admin").value(true))
                .andReturn();

        Integer sessions = jdbc.queryForObject("SELECT COUNT(*) FROM SPRING_SESSION", Integer.class);
        assertThat(sessions).isNotNull().isGreaterThan(0);
        Integer attrs = jdbc.queryForObject("SELECT COUNT(*) FROM SPRING_SESSION_ATTRIBUTES", Integer.class);
        assertThat(attrs).isNotNull().isGreaterThan(0);

        assertThat(login.getResponse().getCookie("JSESSIONID")).isNotNull();
        assertThat(login.getResponse().getCookie("JSESSIONID").getValue()).isNotBlank();

        mvc.perform(get("/api/chemistry/admin/status").cookie(login.getResponse().getCookies()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.admin").value(true));
    }
}
