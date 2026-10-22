package com.example.triplesweb.controller;

import com.example.triplesweb.service.GravityService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.context.annotation.Import;
import com.example.triplesweb.config.MustacheConfig;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.*;

@WebMvcTest({GravityController.class, CosmoGravityDetailController.class, GravityApiController.class})
@AutoConfigureMockMvc(addFilters = false)
@Import(MustacheConfig.class)
class GravityControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean GravityService service;

    @Test void bothListRoutesRenderUnconfiguredStateWithoutFakeResults() throws Exception {
        for (String path : new String[]{"/gravity", "/cosmo/gravity"}) {
            mvc.perform(get(path)).andExpect(status().isOk())
                    .andExpect(content().string(containsString("계정 연결")))
                    .andExpect(content().string(not(containsString("1,842,590"))));
        }
    }

    @Test void listUsesTripleSAndLoadsHeaderIconFont() throws Exception {
        when(service.hasCosmoToken()).thenReturn(true);
        when(service.fetchGravityList("tripleS")).thenReturn(java.util.List.of());
        mvc.perform(get("/cosmo/gravity?artistId=artms")).andExpect(status().isOk())
                .andExpect(content().string(containsString("/fonts/gravity-icons.ttf")))
                .andExpect(content().string(not(containsString("cdn.tailwindcss.com"))))
                .andExpect(content().string(containsString("gravityAdminLoginForm")))
                .andExpect(content().string(not(containsString("href=\"?artistId=artms\""))))
                .andExpect(content().string(not(containsString("href=\"?artistId=idntt\""))));
        verify(service).fetchGravityList("tripleS");
        verify(service, never()).fetchGravityList("artms");
    }

    @Test void detailRendersOfficialResultsWhenApolloIsUnavailable() throws Exception {
        var mapper = new ObjectMapper();
        when(service.fetchGravityDetails(21)).thenReturn(mapper.readTree("""
                {"id":21,"type":"grand-gravity","title":"Actual Gravity","description":"API description",
                 "entireStartDate":"2026-10-01","entireEndDate":"2026-10-02","polls":[
                  {"id":75,"title":"Actual Poll","startDate":"2026-10-01","endDate":"2026-10-02","revealDate":"2026-10-03","result":{"totalComoUsed":120,"voteResults":[
                    {"rank":1,"votedChoice":{"choiceName":"Winner","comoUsed":120}}
                  ]}}
                ]}
                """));
        when(service.toMap(any())).thenAnswer(call -> {
            com.fasterxml.jackson.databind.JsonNode node = call.getArgument(0);
            return node.isMissingNode() || node.isNull() ? null : mapper.convertValue(node, java.util.Map.class);
        });
        when(service.fetchAggregatedData(75)).thenThrow(new IllegalStateException("Apollo unavailable"));
        mvc.perform(get("/cosmo/gravity/21")).andExpect(status().isOk())
                .andExpect(content().string(containsString("Actual Gravity")))
                .andExpect(content().string(containsString("Winner")))
                .andExpect(content().string(containsString("120")));
    }

    @Test void detailRejectsPollBelongingToDifferentGravity() throws Exception {
        var gravity = new ObjectMapper().readTree("{\"id\":21,\"polls\":[{\"id\":75}]}");
        when(service.fetchGravityDetails(21)).thenReturn(gravity);
        when(service.toMap(any())).thenReturn(java.util.Map.of());
        mvc.perform(get("/cosmo/gravity/21?pollId=76")).andExpect(status().isNotFound());
        verify(service, never()).fetchAggregatedData(anyLong());
    }

    @Test void apiRejectsInvalidArtistAndReturnsSanitizedConfigurationError() throws Exception {
        mvc.perform(get("/api/gravity?artistId=invalid")).andExpect(status().isBadRequest());
        when(service.fetchGravityList("tripleS")).thenThrow(new IllegalStateException("COSMO 인증이 아직 연결되지 않았습니다."));
        mvc.perform(get("/api/gravity")).andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").value("COSMO 인증이 아직 연결되지 않았습니다."));
    }

    @Test void unavailableTokenRendersNoticeInsteadOfServerError() throws Exception {
        when(service.fetchGravityDetails(21)).thenThrow(new IllegalStateException("COSMO 인증이 필요합니다."));
        mvc.perform(get("/cosmo/gravity/21")).andExpect(status().isOk())
                .andExpect(content().string(containsString("COSMO 인증")));
    }
}
