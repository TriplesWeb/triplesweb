package com.example.triplesweb.controller;

import com.example.triplesweb.config.MustacheConfig;
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
import static org.hamcrest.Matchers.*;

@WebMvcTest(CosmoCollectionController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(MustacheConfig.class)
class CosmoCollectionControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean CosmoCollectionService collections;

    @Test void initialPageAndEmptySubmittedNicknameHaveDifferentMessages() throws Exception {
        mvc.perform(get("/cosmo/collection")).andExpect(status().isOk())
                .andExpect(content().string(not(containsString("없는 닉네임 입니다."))));
        mvc.perform(get("/cosmo/collection").param("nickname", "")).andExpect(status().isOk())
                .andExpect(content().string(containsString("없는 닉네임 입니다.")));
        verifyNoInteractions(collections);
    }

    @Test void missingNicknameAndNetworkFailureHaveDifferentMessages() throws Exception {
        when(collections.search("없는사람", 0)).thenThrow(new IllegalArgumentException("없는 닉네임 입니다."));
        mvc.perform(get("/cosmo/collection").param("nickname", "없는사람"))
                .andExpect(status().isOk()).andExpect(content().string(containsString("없는 닉네임 입니다.")));
        when(collections.search("테스트", 0)).thenThrow(new IllegalStateException("network"));
        mvc.perform(get("/cosmo/collection").param("nickname", "테스트"))
                .andExpect(content().string(containsString("잠시 후 다시 검색")))
                .andExpect(content().string(not(containsString("없는 닉네임 입니다."))));
    }

    @Test void cardsRenderCollectionSerialAndMenuIsSecondOnBothLayouts() throws Exception {
        var view = new java.util.LinkedHashMap<String, Object>(Map.of("nickname", "테스트", "hasProfileImage", false,
                "ownedCount", 1, "hasCards", true, "pageDisplay", 1, "totalPages", 1, "hasPrev", false, "hasNext", false));
        view.put("cards", List.of(Map.of("serial", "27", "hasSerial", true, "collectionId", "Cream02 HaYeon 367Z",
                "member", "HaYeon", "season", "Cream02", "objektClass", "Double", "hasImage", true,
                "thumbnailImage", "https://example.com/card.webp", "hasDetail", false)));
        when(collections.search("테스트", 0)).thenReturn(view);
        mvc.perform(get("/cosmo/collection").param("nickname", "테스트")).andExpect(status().isOk())
                .andExpect(content().string(containsString("#27")))
                .andExpect(content().string(containsString("collection-image")))
                .andExpect(content().string(containsString("내 오브젝트 검색")))
                .andExpect(content().string(not(containsString("#30000001"))));
    }
}
