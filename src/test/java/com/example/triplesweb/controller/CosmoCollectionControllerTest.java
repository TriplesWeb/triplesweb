package com.example.triplesweb.controller;

import com.example.triplesweb.config.MustacheConfig;
import com.example.triplesweb.service.ArtistSnapshotService;
import com.example.triplesweb.service.CosmoCollectionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.*;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import java.util.List;
import java.util.Map;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.hamcrest.Matchers.*;

@WebMvcTest(CosmoCollectionController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(MustacheConfig.class)
class CosmoCollectionControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean CosmoCollectionService collections;
    @MockitoBean ArtistSnapshotService artists;

    @Test void initialPageAndEmptySubmittedNicknameHaveDifferentMessages() throws Exception {
        mvc.perform(get("/cosmo/collection")).andExpect(status().isOk())
                .andExpect(content().string(not(containsString("없는 닉네임 입니다."))))
                .andExpect(content().string(containsString("aria-label=\"연관닉\"")))
                .andExpect(content().string(containsString("collection-nickname-suggest.js?v=")));
        mvc.perform(get("/cosmo/collection").param("nickname", "")).andExpect(status().isOk())
                .andExpect(content().string(containsString("없는 닉네임 입니다.")));
        verifyNoInteractions(collections);
    }

    @Test void missingNicknameAndNetworkFailureHaveDifferentMessages() throws Exception {
        when(collections.search("없는사람", 0, "", List.of(), List.of(), "", "all", "all", "latest")).thenThrow(new IllegalArgumentException("없는 닉네임 입니다."));
        mvc.perform(get("/cosmo/collection").param("nickname", "없는사람"))
                .andExpect(status().isOk()).andExpect(content().string(containsString("없는 닉네임 입니다.")));
        when(collections.search("테스트", 0, "", List.of(), List.of(), "", "all", "all", "latest")).thenThrow(new IllegalStateException("network"));
        mvc.perform(get("/cosmo/collection").param("nickname", "테스트"))
                .andExpect(content().string(containsString("잠시 후 다시 검색")))
                .andExpect(content().string(not(containsString("없는 닉네임 입니다."))));
    }

    @Test void cardsRenderCollectionSerialAndMenuIsSecondOnBothLayouts() throws Exception {
        var view = new java.util.LinkedHashMap<String, Object>();
        view.put("nickname", "테스트");
        view.put("hasProfileImage", false);
        view.put("ownedCount", 1);
        view.put("filteredCount", 1);
        view.put("hasCards", true);
        view.put("page", 0);
        view.put("pageDisplay", 1);
        view.put("pageSize", 24);
        view.put("loadedCount", 1);
        view.put("totalPages", 1);
        view.put("hasPrev", false);
        view.put("hasNext", false);
        view.put("hasMore", false);
        view.put("nextPage", 1);
        view.put("nicknameEncoded", "%ED%85%8C%EC%8A%A4%ED%8A%B8");
        view.put("profileUrl", "/cosmo/profile/%ED%85%8C%EC%8A%A4%ED%8A%B8");
        view.put("selectedMember", "");
        view.put("selectedSeason", "");
        view.put("selectedClass", "");
        view.put("selectedGroup", "all");
        view.put("isAllGroup", true);
        view.put("isMembersGroup", false);
        view.put("isOtherGroup", false);
        view.put("searchQuery", "");
        view.put("filterQuery", "");
        view.put("hasActiveFilters", false);
        view.put("selectedKind", "all");
        view.put("selectedKindLabel", "전체");
        view.put("cards", List.of(Map.ofEntries(
                Map.entry("serial", "27"), Map.entry("hasSerial", true),
                Map.entry("collectionId", "Cream02 HaYeon 367Z"), Map.entry("cardTitle", "HaYeon 367Z"),
                Map.entry("member", "HaYeon"), Map.entry("season", "Cream02"), Map.entry("seasonColor", "#FF8389"),
                Map.entry("objektClass", "Double"), Map.entry("collectionNo", "367Z"),
                Map.entry("hasImage", true), Map.entry("thumbnailImage", "https://example.com/card.webp"),
                Map.entry("hasDetail", false))));
        view.put("filterMembers", List.of("HaYeon"));
        view.put("filterSeasons", List.of("Cream02"));
        view.put("filterClasses", List.of("Double"));
        view.put("kindByClass", Map.of("Double", List.of("367Z")));
        when(collections.search("테스트", 0, "", List.of(), List.of(), "", "all", "all", "latest")).thenReturn(view);
        when(artists.findAll()).thenReturn(List.of());
        mvc.perform(get("/cosmo/collection").param("nickname", "테스트")).andExpect(status().isOk())
                .andExpect(model().attributeDoesNotExist("allCards"))
                .andExpect(model().attributeDoesNotExist("kindByClass"))
                .andExpect(model().attribute("kindByClassJson", "{\"Double\":[\"367Z\"]}"))
                .andExpect(content().string(containsString("#27")))
                .andExpect(content().string(containsString("collection-card-serial")))
                .andExpect(content().string(containsString("collection-card-season")))
                .andExpect(content().string(containsString("collection-card-class")))
                .andExpect(content().string(containsString("card ar-panel")))
                .andExpect(content().string(containsString("badge badge-sm")))
                .andExpect(content().string(containsString("indicator-item")))
                .andExpect(content().string(containsString("HaYeon 367Z")))
                .andExpect(content().string(containsString("background:#FF8389")))
                .andExpect(content().string(containsString("collection-image")))
                .andExpect(content().string(containsString("코스모 아이디 검색")))
                .andExpect(content().string(containsString("프로필 보기")))
                .andExpect(content().string(containsString("?tab=overview")))
                .andExpect(content().string(containsString("기타 옵젝")))
                .andExpect(content().string(containsString("보유 오브젝트 필터")))
                .andExpect(content().string(containsString("collection-filter-workspace")))
                .andExpect(content().string(containsString("objekt-filter-bar")))
                .andExpect(content().string(containsString("data-objekt-grid")))
                .andExpect(content().string(containsString("setTimeout(loadMore, 400)")))
                .andExpect(content().string(containsString("firstPageKey")))
                .andExpect(content().string(containsString("id=\"collection-kind-select\"")))
                .andExpect(content().string(containsString("id=\"collection-kind-by-class\"")))
                .andExpect(content().string(containsString("\"Double\":[\"367Z\"]")))
                .andExpect(content().string(containsString("value=\"전체\"")))
                .andExpect(content().string(not(containsString("오브젝트 상세 보기"))))
                .andExpect(content().string(not(containsString("header-objekt-jump"))))
                .andExpect(content().string(containsString("collection-scroll-sentinel")))
                .andExpect(content().string(containsString("scheduleApplyFilters")))
                .andExpect(content().string(not(containsString("#30000001"))));
    }

    @Test void suggestReturnsCosmoNicknameMatches() throws Exception {
        when(collections.suggestNicknames("hay", 8)).thenReturn(List.of("hayeonfan", "hayoon"));
        mvc.perform(get("/cosmo/collection/suggest").param("q", "hay"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0]").value("hayeonfan"))
                .andExpect(jsonPath("$.items[1]").value("hayoon"));
    }

    @Test void suggestReturnsEmptyItemsWhenServiceHasNoMatches() throws Exception {
        when(collections.suggestNicknames("ha", 8)).thenReturn(List.of());
        mvc.perform(get("/cosmo/collection/suggest").param("q", "ha"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty());
    }

    @Test void moreEndpointReturnsJsonPagePayload() throws Exception {
        var view = new java.util.LinkedHashMap<String, Object>();
        view.put("cards", List.of(Map.of("serial", "28", "hasSerial", true, "collectionId", "Cream02 HaYeon 368Z",
                "member", "정하연", "season", "Cream02", "objektClass", "Double", "hasImage", false,
                "hasDetail", true, "detailUrl", "/cosmo/objekt/detail/sample")));
        view.put("page", 1);
        view.put("pageSize", 24);
        view.put("ownedCount", 30);
        view.put("filteredCount", 30);
        view.put("hasMore", true);
        view.put("loading", true);
        view.put("nextPage", 2);
        when(collections.search("테스트", 1, "", List.of(), List.of(), "", "all", "all", "latest", false)).thenReturn(view);
        mvc.perform(get("/cosmo/collection/more").param("nickname", "테스트").param("page", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].member").value("정하연"))
                .andExpect(jsonPath("$.hasMore").value(true))
                .andExpect(jsonPath("$.loading").value(true))
                .andExpect(jsonPath("$.nextPage").value(2))
                .andExpect(jsonPath("$.filteredCount").value(30));
    }
}
