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

    @Test void mainFiltersRenderSharedSOrderAndNewestSeasonsIncludingTextOnlyExtras() throws Exception {
        List<Map<String, Object>> items = new java.util.ArrayList<>();
        String[] members = {"S1 X S9", "Jiyeon", "Alpha", "HyeRin", "SeoYeon", "S21", "ChaeWon", "김채원"};
        String[] seasons = {"Atom01", "Binary01", "Cream01", "Divine01", "Ever01", "Atom02", "Binary02", "Cream02"};
        for (int i = 0; i < seasons.length; i++) {
            Map<String, Object> item = new java.util.LinkedHashMap<>();
            for (String field : List.of("seasonKey", "classKey", "artist", "collectionNo", "numberValue", "createdAt", "searchText",
                    "thumbnailImage", "frontImage", "backImage", "collectionId", "class")) item.put(field, "");
            item.put("id", "id" + i); item.put("member", members[i % members.length]);
            item.put("season", seasons[i]); item.put("seasonKey", seasons[i]); items.add(item);
        }
        when(catalog.findCollections("triples")).thenReturn(items);
        when(market.withMintCounts(items)).thenReturn(items);
        var result = mvc.perform(get("/cosmo/objekt")).andExpect(status().isOk())
                .andExpect(model().attribute("memberOptions", List.of("윤서연", "정혜린", "김채원", "지연", "Alpha", "S1 X S9")))
                .andExpect(model().attribute("seasonOptions", List.of("Cream02", "Binary02", "Atom02", "Ever01", "Divine01", "Cream01", "Binary01", "Atom01")))
                .andExpect(content().string(containsString("data-value=\"S1 X S9\"")))
                .andExpect(content().string(containsString("artist-tab-fallback"))).andReturn();
        mvc.perform(get("/cosmo/objekt/more").param("page", "0").param("member", "S1 X S9"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.resultCount").value(1));
        var legacy = mvc.perform(get("/cosmo/objekt").param("member", "S21"))
                .andExpect(status().isOk()).andExpect(model().attribute("objektCount", 3)).andReturn();
        java.nio.file.Files.writeString(java.nio.file.Path.of("build/objekt-filter-order-preview.html"), legacy.getResponse().getContentAsString());
    }
}
