package com.example.triplesweb.controller;

import com.example.triplesweb.repository.*;
import com.example.triplesweb.service.*;
import com.example.triplesweb.config.MustacheConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import java.util.List;
import java.util.Map;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.containsString;

@WebMvcTest({CosmoController.class, ObjektFeatureController.class})
@AutoConfigureMockMvc(addFilters = false)
@Import(MustacheConfig.class)
class ObjektFeaturesTest {
    @Autowired MockMvc mvc;
    @MockitoBean ObjektService catalog;
    @MockitoBean ArtistRepository artists;
    @MockitoBean ObjektMarketDataService market;
    @MockitoBean ObjektFavoriteService favorites;
    @MockitoBean ObjektFavoriteRepository favoriteRepository;
    @MockitoBean ObjektDescriptionRepository descriptions;
    @MockitoBean AboutAdminAuth admin;

    @Test void mintSortingUsesCountsNotCollectionNumbersWithUnknownAlwaysLast() throws Exception {
        var items = List.<Map<String, Object>>of(
                Map.of("id", "unknown", "slug", "unknown", "numberValue", 1),
                Map.of("id", "many", "slug", "many", "numberValue", 2, "mintCount", 90L),
                Map.of("id", "few", "slug", "few", "numberValue", 999, "mintCount", 5L),
                Map.of("id", "zero", "slug", "zero", "mintCount", 0L));
        when(catalog.findCollections("triples")).thenReturn(items);
        when(market.withMintCounts(items)).thenReturn(items);
        mvc.perform(get("/cosmo/objekt/more").param("page", "0").param("sort", "mint_low"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].id").value("zero"))
                .andExpect(jsonPath("$.items[1].id").value("few"))
                .andExpect(jsonPath("$.items[2].id").value("many"))
                .andExpect(jsonPath("$.items[3].id").value("unknown"));
        mvc.perform(get("/cosmo/objekt/more").param("page", "0").param("sort", "mint_high"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].id").value("many"))
                .andExpect(jsonPath("$.items[1].id").value("few"))
                .andExpect(jsonPath("$.items[2].id").value("zero"))
                .andExpect(jsonPath("$.items[3].id").value("unknown"));
    }

    @Test void ownerEndpointValidatesSerialAndReturnsRecoverableFailure() throws Exception {
        Map<String, Object> item = Map.of("id", "uuid", "slug", "sample");
        when(catalog.findCollections("triples")).thenReturn(List.of(item));
        mvc.perform(get("/cosmo/objekt/detail/uuid/owner").param("serial", "0")).andExpect(status().isBadRequest());
        verifyNoInteractions(market);
        when(market.owner(item, 1)).thenThrow(new IllegalStateException("RPC unavailable"));
        mvc.perform(get("/cosmo/objekt/detail/uuid/owner").param("serial", "1"))
                .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.message").exists());
    }

    @Test void detailRendersOwnerFormAndExplicitFavoriteState() throws Exception {
        Map<String, Object> item = new java.util.LinkedHashMap<>(Map.of("id", "uuid", "slug", "sample", "collectionId", "Cream02 HaYeon 367Z", "member", "HaYeon"));
        for (String field : List.of("frontImage", "backImage", "thumbnailImage", "season", "class", "collectionNo")) item.put(field, "");
        when(catalog.findCollections("triples")).thenReturn(List.of(item));
        when(descriptions.findByObjektKey("uuid")).thenReturn(java.util.Optional.empty());
        mvc.perform(get("/cosmo/objekt/detail/uuid")).andExpect(status().isOk())
                .andExpect(content().string(containsString("data-objekt-owner")))
                .andExpect(content().string(containsString("name=\"favorite\" value=\"true\"")))
                .andExpect(content().string(containsString("/js/objekt-detail.js")));
    }

    @Test void ownerResponseDoesNotExposeInternalVerificationFields() throws Exception {
        Map<String, Object> item = Map.of("id", "uuid", "slug", "sample");
        when(catalog.findCollections("triples")).thenReturn(List.of(item));
        when(market.owner(item, 1)).thenReturn(Map.of("status", "found", "serial", 1, "nickname", "테스트",
                "address", "0xprivate", "tokenId", "30000001", "verifiedAt", "time", "explorerUrl", "https://abscan.org"));
        mvc.perform(get("/cosmo/objekt/detail/uuid/owner").param("serial", "1")).andExpect(status().isOk())
                .andExpect(jsonPath("$.nickname").value("테스트"))
                .andExpect(jsonPath("$.profileUrl").value("/cosmo/profile/%ED%85%8C%EC%8A%A4%ED%8A%B8"))
                .andExpect(jsonPath("$.address").doesNotExist()).andExpect(jsonPath("$.tokenId").doesNotExist())
                .andExpect(jsonPath("$.verifiedAt").doesNotExist()).andExpect(jsonPath("$.explorerUrl").doesNotExist());
    }
}
