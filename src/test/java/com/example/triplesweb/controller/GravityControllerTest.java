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
    @Test void commonFiltersPreview() throws Exception {
        when(service.hasCosmoToken()).thenReturn(true);
        java.util.List<java.util.Map<String,Object>> items=new java.util.ArrayList<>();
        for(int i=1;i<=3;i++)items.add(java.util.Map.of("id",(long)i,"title","그래비티 "+i,"statusLabel",i==1?"진행 중":"종료","type",i==1?"유닛":"타이틀","detailUrl","/cosmo/gravity/"+i,"hasComo",true,"comoUsed",i==1?100L:i==2?300L:200L,"periodLabel","2026-10"));
        when(service.fetchGravityList("tripleS")).thenReturn(items);
        var result=mvc.perform(get("/cosmo/gravity")).andExpect(status().isOk()).andExpect(content().string(containsString("data-objekt-dropdown"))).andReturn();
        java.nio.file.Files.createDirectories(java.nio.file.Path.of("build/perf-pages"));
        java.nio.file.Files.writeString(java.nio.file.Path.of("build/perf-pages/gravity.html"),result.getResponse().getContentAsString());
    }
    @Autowired MockMvc mvc;
    @MockitoBean GravityService service;

    @Test void bothListRoutesRenderUnconfiguredStateWithoutFakeResults() throws Exception {
        for (String path : new String[]{"/gravity", "/cosmo/gravity"}) {
            mvc.perform(get(path)).andExpect(status().isOk())
                    .andExpect(content().string(containsString("계정 연결")))
                    .andExpect(content().string(not(containsString("1,842,590"))));
        }
    }

    @Test void listPageUsesLiveItemsWithoutDesignPlaceholders() throws Exception {
        when(service.hasCosmoToken()).thenReturn(true);
        when(service.fetchGravityList("tripleS")).thenReturn(java.util.List.of(java.util.Map.of(
                "id", 21L,
                "title", "Actual Gravity",
                "description", "API description",
                "statusLabel", "종료",
                "type", "grand-gravity",
                "detailUrl", "/cosmo/gravity/21",
                "hasComo", true,
                "comoUsed", 120L,
                "entireStartDate", "2026-10-01",
                "entireEndDate", "2026-10-02"
        )));
        mvc.perform(get("/cosmo/gravity")).andExpect(status().isOk())
                .andExpect(content().string(containsString("Actual Gravity")))
                .andExpect(content().string(containsString("API description")))
                .andExpect(content().string(containsString("120")))
                .andExpect(content().string(not(containsString("14.8M"))))
                .andExpect(content().string(not(containsString("Polygon Mainnet"))))
                .andExpect(content().string(not(containsString("Girls Never Die"))))
                .andExpect(content().string(not(containsString("cdn.tailwindcss.com"))));
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

    @Test void listAndRecentCardsHideIdsAndShowKoreanTimeWithStatusPills() throws Exception {
        when(service.hasCosmoToken()).thenReturn(true);
        when(service.fetchGravityList("tripleS")).thenReturn(java.util.List.of(java.util.Map.of(
                "id", 21L, "title", "한국 시간 그래비티", "statusLabel", "진행 중", "type", "grand-gravity",
                "detailUrl", "/cosmo/gravity/21", "hasComo", true, "comoUsed", 10L,
                "entireStartDate", "2026-07-28T01:00:00.000Z",
                "entireEndDate", "2026-07-28T13:00:00.000Z")));
        mvc.perform(get("/cosmo/gravity")).andExpect(status().isOk())
                .andExpect(content().string(containsString("gv-status--ongoing")))
                .andExpect(content().string(containsString("2026-07-28 10:00 ~ 22:00")))
                .andExpect(content().string(containsString("한국 시간")))
                .andExpect(content().string(containsString("href=\"/cosmo/gravity/21\"")))
                .andExpect(content().string(not(containsString(">#21</span>"))))
                .andExpect(content().string(not(containsString("2026-07-28T01:00:00.000Z"))));
    }

    @Test void detailUsesCosmoOfficialResultsWithoutExtraRequests() throws Exception {
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
        mvc.perform(get("/cosmo/gravity/21")).andExpect(status().isOk())
                .andExpect(content().string(containsString("Actual Gravity")))
                .andExpect(content().string(containsString("Winner")))
                .andExpect(content().string(containsString("data-como=\"120\"")))
                .andExpect(content().string(containsString("gravity-rank-track")))
                .andExpect(content().string(not(containsString("apollo.cafe"))))
                .andExpect(content().string(containsString("120")));
        verify(service, never()).fetchPollDetails(anyLong());
    }

    @Test void detailAndPollDatesUseKoreanTimeWithoutVisibleIds() throws Exception {
        var mapper = new ObjectMapper();
        when(service.fetchGravityDetails(88)).thenReturn(mapper.readTree("""
                {"id":88,"type":"grand-gravity","title":"한국 시간","description":"기간 표시",
                 "entireStartDate":"2026-07-28T14:00:00.000Z","entireEndDate":"2026-07-28T16:00:00.000Z",
                 "polls":[{"id":7,"title":"투표","startDate":"2026-07-28T01:00:00Z",
                  "endDate":"2026-07-28T13:00:00Z","revealDate":"2026-07-28T18:00:00Z"}]}
                """));
        when(service.toMap(any())).thenAnswer(call -> {
            com.fasterxml.jackson.databind.JsonNode node = call.getArgument(0);
            return node.isMissingNode() || node.isNull() ? null : mapper.convertValue(node, java.util.Map.class);
        });
        mvc.perform(get("/cosmo/gravity/88")).andExpect(status().isOk())
                .andExpect(content().string(containsString("2026-07-28 23:00 ~ 2026-07-29 01:00")))
                .andExpect(content().string(containsString("2026-07-28 10:00 ~ 22:00")))
                .andExpect(content().string(containsString("결과 공개 2026-07-29 03:00")))
                .andExpect(content().string(not(containsString(" · #88"))))
                .andExpect(content().string(not(containsString("2026-07-28T14:00:00.000Z"))));
    }

    @Test void legacyAggregationEndpointDoesNotContactThirdParty() throws Exception {
        mvc.perform(get("/api/gravity/polls/75/aggregated")).andExpect(status().isGone());
        verifyNoInteractions(service);
    }

    @Test void detailRejectsPollBelongingToDifferentGravity() throws Exception {
        var gravity = new ObjectMapper().readTree("{\"id\":21,\"polls\":[{\"id\":75}]}");
        when(service.fetchGravityDetails(21)).thenReturn(gravity);
        when(service.toMap(any())).thenReturn(java.util.Map.of());
        mvc.perform(get("/cosmo/gravity/21?pollId=76")).andExpect(status().isNotFound());
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
