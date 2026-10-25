package com.example.triplesweb.controller;

import com.example.triplesweb.service.*;
import com.example.triplesweb.repository.ArtistRepository;
import com.example.triplesweb.config.MustacheConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.*;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(CosmoProfileController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({MustacheConfig.class, CosmoProfileService.class})
class CosmoProfileControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean CosmoCollectionService collections;
    @MockitoBean ObjektService catalog;
    @MockitoBean ArtistRepository artists;

    private void givenProfile() {
        Map<String, Object> card = new LinkedHashMap<>(Map.of("slug", "cream02-hayeon-367z", "member", "HaYeon",
                "objektClass", "Double", "season", "Cream02", "artist", "triples", "collectionId", "Cream02 HaYeon 367Z",
                "hasSerial", true, "serial", "1", "hasImage", false, "hasDetail", false));
        when(collections.search("Fan", 0)).thenReturn(Map.of("nickname", "Fan", "profileUrl", "/cosmo/profile/Fan",
                "ownedCount", 1, "hasProfileImage", false, "allCards", List.of(card)));
        when(catalog.findCollections("triples")).thenReturn(List.of(Map.of("slug", "cream02-hayeon-367z", "member", "HaYeon", "class", "Double")));
    }

    @Test void progressPageRendersActualCountsAndNoWalletIdentifiers() throws Exception {
        givenProfile();
        var result = mvc.perform(get("/cosmo/profile/Fan")).andExpect(status().isOk())
                .andExpect(content().string(containsString("Fan")))
                .andExpect(content().string(containsString("멤버별 수집 현황")))
                .andExpect(content().string(containsString("aria-valuenow=\"100\"")))
                .andExpect(content().string(containsString("1 / 1종")))
                .andExpect(content().string(not(containsString("토큰 ID")))).andReturn();
        java.nio.file.Files.writeString(java.nio.file.Path.of("build/profile-progress-preview.html"), result.getResponse().getContentAsString());
    }

    @Test void collectionTabShowsActualSerialAndDoesNotLoseMemberFilter() throws Exception {
        givenProfile();
        var result = mvc.perform(get("/cosmo/profile/Fan").param("tab", "collection").param("member", "HaYeon"))
                .andExpect(status().isOk()).andExpect(content().string(containsString("#1")))
                .andExpect(content().string(containsString("HaYeon · 보유 오브젝트 1개")))
                .andExpect(content().string(containsString("collection-image-wrap"))).andReturn();
        java.nio.file.Files.writeString(java.nio.file.Path.of("build/profile-collection-preview.html"), result.getResponse().getContentAsString());
    }

    @Test void missingAndUnavailableProfilesHaveUsefulErrors() throws Exception {
        when(collections.search("Missing", 0)).thenThrow(new IllegalArgumentException("없는 닉네임 입니다."));
        mvc.perform(get("/cosmo/profile/Missing")).andExpect(content().string(containsString("없는 닉네임 입니다.")));
        when(collections.search("Down", 0)).thenThrow(new IllegalStateException("private upstream detail"));
        mvc.perform(get("/cosmo/profile/Down")).andExpect(content().string(containsString("잠시 후 다시 확인")))
                .andExpect(content().string(not(containsString("private upstream detail"))));
    }
}
