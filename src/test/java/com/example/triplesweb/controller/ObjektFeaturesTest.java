package com.example.triplesweb.controller;

import com.example.triplesweb.repository.*;
import com.example.triplesweb.service.*;
import com.example.triplesweb.config.MustacheConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import java.util.List;
import java.util.Map;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;

@WebMvcTest({CosmoController.class, ObjektFeatureController.class})
@AutoConfigureMockMvc(addFilters = false)
@Import(MustacheConfig.class)
class ObjektFeaturesTest {
    @org.junit.jupiter.api.BeforeEach void decorateVisibleCards() { when(market.withIssuedSupply(anyList())).thenAnswer(call -> call.getArgument(0)); }
    @Autowired MockMvc mvc;
    @MockitoBean ObjektService catalog;
    @MockitoBean ArtistRepository artists;
    @MockitoBean ObjektMarketDataService market;
    @MockitoBean ObjektFavoriteService favorites;
    @MockitoBean ObjektFavoriteRepository favoriteRepository;
    @MockitoBean ObjektDescriptionRepository descriptions;
    @MockitoBean AboutAdminAuth admin;

    @Test void favoriteListRemoveFormIncludesCsrfToken() throws Exception {
        var item = new com.example.triplesweb.entity.ObjektFavorite();
        item.setObjektKey("uuid");
        item.setCollectionId("sample");
        when(favoriteRepository.findByVisitorKeyOrderByCreatedAtDesc(anyString())).thenReturn(List.of(item));
        mvc.perform(get("/cosmo/objekt/favorites").flashAttr("_csrf",
                new org.springframework.security.web.csrf.DefaultCsrfToken("X-XSRF-TOKEN","_csrf","remove-form-token")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("name=\"_csrf\" value=\"remove-form-token\"")));
    }

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

    @Test void oldDetailPageOpensSharedCatalogModal() throws Exception {
        when(catalog.findCollections("triples")).thenReturn(List.of(Map.of("id","uuid","slug","sample")));
        mvc.perform(get("/cosmo/objekt/detail/sample").accept(MediaType.TEXT_HTML))
            .andExpect(redirectedUrl("/cosmo/objekt?objekt=sample"));
    }

    @Test void readableUrlsModalActionsAndLegacyMemberRouteAreConnected() throws Exception {
        Map<String, Object> item = Map.of("id", "uuid", "slug", "hayeoncc367z", "collectionId", "Cream02 HaYeon 367Z", "member", "HaYeon");
        when(catalog.findCollections("triples")).thenReturn(List.of(item));
        mvc.perform(get("/cosmo/objekt/detail/uuid").accept(MediaType.TEXT_HTML))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/cosmo/objekt?objekt=hayeoncc367z"));
        mvc.perform(get("/cosmo/objekt/detail/uuid").accept(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.objektKey").value("uuid"))
                .andExpect(jsonPath("$.detailUrl").value("/cosmo/objekt/detail/hayeoncc367z"))
                .andExpect(jsonPath("$.memberUrl").value("/cosmo/objekt?group=members&member=HaYeon"))
                .andExpect(jsonPath("$.tradeUrl").value("/article/trade/new?selectedObjektIds=uuid"))
                .andExpect(jsonPath("$.sellUrl").value("/article/sell/new?selectedObjektIds=uuid"));
        mvc.perform(get("/cosmo/objekt/member").param("name", "HaYeon"))
                .andExpect(redirectedUrl("/cosmo/objekt?group=members&member=HaYeon"));
    }

    @Test void realIndexSlugResolvesShortPublicAliasAndPreservesUpstreamAlias() throws Exception {
        var item = Map.<String,Object>of("id","uuid","slug","cream02-hayeon-367z","season","Cream02","collectionNo","367Z","member","HaYeon");
        when(catalog.findCollections("triples")).thenReturn(List.of(item));
        mvc.perform(get("/cosmo/objekt/detail/hayeoncc367z").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk()).andExpect(jsonPath("$.detailUrl").value("/cosmo/objekt/detail/hayeoncc367z"));
        mvc.perform(get("/cosmo/objekt/detail/cream02-hayeon-367z").accept(MediaType.TEXT_HTML))
                .andExpect(redirectedUrl("/cosmo/objekt?objekt=hayeoncc367z"));
    }

    @Test void modalDescriptionWritesKeepStableKeyAndRequireAdmin() throws Exception {
        var item = Map.<String,Object>of("id", "uuid", "slug", "hayeoncc367z");
        when(catalog.findCollections("triples")).thenReturn(List.of(item));
        mvc.perform(post("/cosmo/objekt/detail/hayeoncc367z/description").accept(MediaType.APPLICATION_JSON).param("description", " 새 설명 "))
                .andExpect(status().isOk()).andExpect(jsonPath("$.description").value("새 설명"));
        verify(admin).requireAdmin(any(), any());
        var captor = org.mockito.ArgumentCaptor.forClass(com.example.triplesweb.entity.ObjektDescription.class);
        verify(descriptions).save(captor.capture());
        org.assertj.core.api.Assertions.assertThat(captor.getValue().getObjektKey()).isEqualTo("uuid");
        mvc.perform(post("/cosmo/objekt/detail/hayeoncc367z/description").accept(MediaType.APPLICATION_JSON).param("description", ""))
                .andExpect(status().isOk()).andExpect(jsonPath("$.description").value(""));
        doThrow(new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN)).when(admin).requireAdmin(any(), any());
        mvc.perform(post("/cosmo/objekt/detail/hayeoncc367z/description").accept(MediaType.APPLICATION_JSON).param("description", "bad"))
                .andExpect(status().isForbidden());
    }

    @Test void combinedLookupUsesIndexAndNotLiveRpc() throws Exception {
        var item = Map.<String,Object>of("id", "uuid", "slug", "hayeoncc367z");
        when(catalog.findCollections("triples")).thenReturn(List.of(item));
        when(market.serialLookup(item, 1, 0)).thenReturn(Map.of("status","available","owner",Map.of("status","indexed","nickname","A"),"transfers",List.of()));
        mvc.perform(get("/cosmo/objekt/detail/hayeoncc367z/lookup").param("serial","1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.owner.nickname").value("A"));
        verify(market, never()).owner(anyMap(), anyLong());
    }

    @Test void modalPreview() throws Exception {
        var item = new java.util.LinkedHashMap<String,Object>();
        for (String field : List.of("frontImage","backImage","thumbnailImage","seasonKey","classKey","artist","createdAt","searchText","backgroundColor","textColor")) item.put(field, "");
        item.putAll(Map.of("id","uuid","slug","hayeoncc367z","collectionId","Cream02 HaYeon 367Z","member","HaYeon","season","Cream02","class","Special","collectionNo","367Z","numberValue",367));
        when(catalog.findCollections("triples")).thenReturn(List.of(item));
        when(market.withMintCounts(anyList())).thenAnswer(call -> call.getArgument(0));
        var result = mvc.perform(get("/cosmo/objekt")).andExpect(status().isOk()).andReturn();
        java.nio.file.Files.writeString(java.nio.file.Path.of("build/objekt-modal-preview.html"), result.getResponse().getContentAsString());
        mvc.perform(get("/cosmo/objekt/detail/hayeoncc367z").accept(MediaType.TEXT_HTML))
            .andExpect(redirectedUrl("/cosmo/objekt?objekt=hayeoncc367z"));
        org.assertj.core.api.Assertions.assertThat(result.getResponse().getContentAsString())
            .contains("data-serial-panel", "objektIssuedRanges").doesNotContain("id=\"objektDetailFullLink\"");
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

    @Test void historyEndpointDoesNotRequireLiveOwnerVerification() throws Exception {
        Map<String, Object> item = Map.of("id", "uuid", "slug", "sample");
        when(catalog.findCollections("triples")).thenReturn(List.of(item));
        when(market.transfers(item, 1, 0)).thenReturn(Map.of("status", "available", "transfers", List.of()));
        mvc.perform(get("/cosmo/objekt/detail/uuid/transfers").param("serial", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("available"))
                .andExpect(header().string("Cache-Control", "no-store"));
        verify(market, never()).owner(anyMap(), anyLong());
        mvc.perform(get("/cosmo/objekt/detail/uuid/transfers").param("serial", "1").param("page", "-1"))
                .andExpect(status().isBadRequest());
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
                .andExpect(model().attribute("memberOptions", List.of("윤서연", "정혜린", "카에데", "김채원", "지연")))
                .andExpect(model().attribute("seasonOptions", List.of("Cream02", "Binary02", "Atom02", "Ever01", "Divine01", "Cream01", "Binary01", "Atom01")))
                .andExpect(content().string(not(containsString("data-value=\"S1 X S9\""))))
                .andExpect(content().string(not(containsString("data-value=\"Alpha\""))))
                .andExpect(content().string(containsString("data-objekt-group=\"other\"")))
                .andExpect(content().string(containsString("artist-tab-fallback"))).andReturn();
        java.nio.file.Files.writeString(java.nio.file.Path.of("build/objekt-all-preview.html"), result.getResponse().getContentAsString());
        mvc.perform(get("/cosmo/objekt/more").param("page", "0").param("member", "S1 X S9"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.resultCount").value(2));
        var legacy = mvc.perform(get("/cosmo/objekt").param("member", "S21"))
                .andExpect(status().isOk()).andExpect(model().attribute("objektCount", 3)).andReturn();
        java.nio.file.Files.writeString(java.nio.file.Path.of("build/objekt-filter-order-preview.html"), legacy.getResponse().getContentAsString());
        var other = mvc.perform(get("/cosmo/objekt").param("group", "other"))
                .andExpect(status().isOk()).andExpect(model().attribute("objektCount", 1)).andReturn();
        java.nio.file.Files.writeString(java.nio.file.Path.of("build/objekt-other-preview.html"), other.getResponse().getContentAsString());
        var membersPage = mvc.perform(get("/cosmo/objekt").param("group", "members"))
                .andExpect(status().isOk()).andExpect(model().attribute("objektCount", 7)).andReturn();
        java.nio.file.Files.writeString(java.nio.file.Path.of("build/objekt-members-preview.html"), membersPage.getResponse().getContentAsString());
    }

    @Test void unitObjectsMatchEitherSelectedMemberAndOthersPaginateSeparately() throws Exception {
        List<Map<String, Object>> items = new java.util.ArrayList<>(List.of(
                Map.of("id", "duo", "member", "S5 X S20"), Map.of("id", "five", "member", "Yooyeon"),
                Map.of("id", "twenty", "member", "Shion"), Map.of("id", "one", "member", "S1")));
        for (int i = 0; i < 25; i++) items.add(Map.of("id", "other" + i, "member", "Other" + i));
        when(catalog.findCollections("triples")).thenReturn(items);
        when(market.withMintCounts(items)).thenReturn(items);
        for (String member : List.of("김유연", "박시온")) mvc.perform(get("/cosmo/objekt/more").param("page", "0").param("member", member))
                .andExpect(status().isOk()).andExpect(jsonPath("$.resultCount").value(2))
                .andExpect(jsonPath("$.items[0].id").value("duo"));
        mvc.perform(get("/cosmo/objekt/more").param("page", "0").param("member", "김유연", "박시온"))
                .andExpect(jsonPath("$.resultCount").value(3));
        mvc.perform(get("/cosmo/objekt/more").param("page", "0").param("member", "정혜린"))
                .andExpect(jsonPath("$.resultCount").value(0));
        mvc.perform(get("/cosmo/objekt/more").param("page", "0").param("group", "members"))
                .andExpect(jsonPath("$.resultCount").value(4));
        mvc.perform(get("/cosmo/objekt/more").param("page", "0").param("group", "other").param("member", "김유연"))
                .andExpect(jsonPath("$.resultCount").value(25)).andExpect(jsonPath("$.loaded").value(20))
                .andExpect(jsonPath("$.hasMore").value(true)).andExpect(jsonPath("$.items[0].id").value("other0"));
        mvc.perform(get("/cosmo/objekt/more").param("page", "1").param("group", "other"))
                .andExpect(jsonPath("$.loaded").value(5)).andExpect(jsonPath("$.hasMore").value(false))
                .andExpect(jsonPath("$.items[0].id").value("other20"));
    }
}

