package com.example.triplesweb.controller;

import com.example.triplesweb.service.SiteAlertsService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SiteAlertsController.class)
@AutoConfigureMockMvc(addFilters = false)
class SiteAlertsControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean SiteAlertsService alerts;

    @Test
    void alertsJsonIncludesNoticesObjektsAndPulseSignals() throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("catalogSig", "sig-9");
        body.put("catalogCount", 3);
        body.put("dropWindow", true);
        body.put("notices", List.of(Map.of("id", 4L, "title", "서버 점검", "href", "/article/notice/4", "at", "2026-11-10")));
        body.put("objekts", List.of(Map.of("label", "HaYeon 323", "href", "/cosmo/objekt/detail/x",
                "collectionId", "Cream02 HaYeon 323", "createdAt", "2026-11-10")));
        when(alerts.alerts()).thenReturn(body);

        mvc.perform(get("/api/site/alerts"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("\"catalogSig\":\"sig-9\"")))
                .andExpect(content().string(containsString("\"dropWindow\":true")))
                .andExpect(content().string(containsString("서버 점검")))
                .andExpect(content().string(containsString("/article/notice/4")))
                .andExpect(content().string(containsString("HaYeon 323")));
    }
}
