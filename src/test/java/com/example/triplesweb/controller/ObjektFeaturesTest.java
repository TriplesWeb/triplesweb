package com.example.triplesweb.controller;

import com.example.triplesweb.repository.*;
import com.example.triplesweb.service.*;
import com.example.triplesweb.config.MustacheConfig;
import com.example.triplesweb.util.ObjektLinks;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;

@WebMvcTest({CosmoController.class, ObjektFeatureController.class})
@AutoConfigureMockMvc(addFilters = false)
@Import({MustacheConfig.class})
class ObjektFeaturesTest {
    @org.junit.jupiter.api.BeforeEach void decorateVisibleCards() {
        when(market.withIssuedSupply(anyList())).thenAnswer(call -> call.getArgument(0));
    }
    @Autowired MockMvc mvc;
    @MockitoBean ObjektService catalog;
    @MockitoBean ArtistSnapshotService artists;
    @MockitoBean ObjektMarketDataService market;
    @MockitoBean EventService eventService;
    @MockitoBean ShopService shopService;
    @MockitoBean ObjektFavoriteService favorites;
    @MockitoBean ObjektFavoriteRepository favoriteRepository;
    @MockitoBean ObjektDescriptionRepository descriptions;
    @MockitoBean AboutAdminAuth admin;

    /** Mirrors ObjektService.findByKey indexing so detail routes resolve under the mock catalog. */
    private void stubCatalog(List<Map<String, Object>> items) {
        when(catalog.findCollections("triples")).thenReturn(items);
        when(catalog.filterFacets()).thenReturn(ObjektService.FilterFacets.from(items));
        Map<String, Map<String, Object>> index = new HashMap<>();
        for (Map<String, Object> item : items) {
            putIndex(index, string(item, "id"), item);
            putIndex(index, string(item, "collectionId"), item);
            putIndex(index, string(item, "slug"), item);
            String slugLower = string(item, "slug").toLowerCase(Locale.ROOT);
            if (!slugLower.isBlank()) index.putIfAbsent(slugLower, item);
            putIndex(index, ObjektLinks.key(item), item);
            putIndex(index, ObjektLinks.id(item), item);
        }
        when(catalog.findByKey(anyString())).thenAnswer(inv -> {
            String key = inv.getArgument(0);
            if (key == null || key.isBlank()) return null;
            Map<String, Object> found = index.get(key);
            return found != null ? found : index.get(key.toLowerCase(Locale.ROOT));
        });
    }

    private static void putIndex(Map<String, Map<String, Object>> index, String key, Map<String, Object> item) {
        if (key == null || key.isBlank()) return;
        index.putIfAbsent(key, item);
    }

    private static String string(Map<String, Object> item, String key) {
        Object value = item.get(key);
        return value == null ? "" : value.toString().trim();
    }

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
        stubCatalog(items);
        when(market.withMintCounts(anyList())).thenAnswer(call -> call.getArgument(0));
        when(market.mintCount(anyString())).thenAnswer(call -> {
            String slug = call.getArgument(0);
            return switch (slug) {
                case "many" -> 90L;
                case "few" -> 5L;
                case "zero" -> 0L;
                default -> null;
            };
        });
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

    @Test void memberSortingUsesDebutOrderAndKeepsUnknownLast() throws Exception {
        var items = List.<Map<String, Object>>of(Map.of("id","unknown","member","unknown"),
            Map.of("id","s24","member","SeoAh"), Map.of("id","s2","member","HyeRin"), Map.of("id","s1","member","SeoYeon"));
        stubCatalog(items);
        when(market.withMintCounts(anyList())).thenAnswer(call -> call.getArgument(0));
        mvc.perform(get("/cosmo/objekt/more").param("page","0").param("sort","first_member"))
            .andExpect(jsonPath("$.items[0].id").value("s1"))
            .andExpect(jsonPath("$.items[1].id").value("s2"))
            .andExpect(jsonPath("$.items[2].id").value("s24"))
            .andExpect(jsonPath("$.items[3].id").value("unknown"));
        mvc.perform(get("/cosmo/objekt/more").param("page","0").param("sort","last_member"))
            .andExpect(jsonPath("$.items[0].id").value("s24"))
            .andExpect(jsonPath("$.items[1].id").value("s2"))
            .andExpect(jsonPath("$.items[2].id").value("s1"))
            .andExpect(jsonPath("$.items[3].id").value("unknown"));
    }

    @Test void latestSortPutsLaterSeasonAheadOfBinary01WithoutCreatedAt() throws Exception {
        var items = List.<Map<String, Object>>of(
                Map.of("id", "binary", "season", "Binary01", "collectionNo", "201Z"),
                Map.of("id", "cream", "season", "Cream02", "collectionNo", "101Z"));
        stubCatalog(items);
        when(market.withMintCounts(anyList())).thenAnswer(call -> call.getArgument(0));
        mvc.perform(get("/cosmo/objekt/more").param("page", "0").param("sort", "latest")
                        .param("season", "Binary01").param("season", "Cream02"))
                .andExpect(jsonPath("$.items[0].id").value("cream"))
                .andExpect(jsonPath("$.items[1].id").value("binary"));
    }

    @Test void latestSortKeepsLaterSeasonFirstEvenIfBinary01CreatedAtIsNewer() throws Exception {
        var items = List.<Map<String, Object>>of(
                Map.of("id", "binary", "season", "Binary01", "createdAt", "2026-05-22T02:36:12.000Z", "collectionNo", "118A"),
                Map.of("id", "cream", "season", "Cream01", "createdAt", "2024-05-07T06:15:27.000Z", "collectionNo", "332Z"),
                Map.of("id", "divine", "season", "Divine01", "createdAt", "2024-11-08T03:00:16.000Z", "collectionNo", "343Z"));
        stubCatalog(items);
        when(market.withMintCounts(anyList())).thenAnswer(call -> call.getArgument(0));
        mvc.perform(get("/cosmo/objekt/more").param("page", "0").param("sort", "latest")
                        .param("season", "Binary01").param("season", "Cream01").param("season", "Divine01"))
                .andExpect(jsonPath("$.items[0].id").value("divine"))
                .andExpect(jsonPath("$.items[1].id").value("cream"))
                .andExpect(jsonPath("$.items[2].id").value("binary"));
    }

    @Test void latestSortKeepsBinary01ContiguousAcrossPages() throws Exception {
        List<Map<String, Object>> items = new ArrayList<>();
        Map<String, Object> titled = new LinkedHashMap<>();
        titled.put("id", "binary-title");
        titled.put("collectionId", "Binary01 SeoYeon 118A");
        titled.put("createdAt", "2026-11-01T00:00:00Z");
        titled.put("collectionNo", "118A");
        items.add(titled);
        items.add(Map.of("id", "binary-suffix", "season", "Binary01 First",
                "createdAt", "2026-10-01T00:00:00Z", "collectionNo", "1Z"));
        for (int i = 0; i < 25; i++) {
            items.add(Map.of("id", "cream-" + i, "season", "Cream02",
                    "createdAt", "2024-01-01T00:00:00Z", "collectionNo", i + "Z"));
        }
        items.add(Map.of("id", "binary", "season", "Binary01",
                "createdAt", "2024-02-01T00:00:00Z", "collectionNo", "201Z"));
        stubCatalog(items);
        when(market.withMintCounts(anyList())).thenAnswer(call -> call.getArgument(0));
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        List<String> ids = new ArrayList<>();
        for (int page = 0; page < 3; page++) {
            String json = mvc.perform(get("/cosmo/objekt/more").param("page", String.valueOf(page)).param("sort", "latest"))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();
            for (var item : mapper.readTree(json).path("items")) ids.add(item.path("id").asText());
        }
        org.assertj.core.api.Assertions.assertThat(ids.stream().filter(id -> id.startsWith("cream-")).toList()).hasSize(25);
        int lastCream = -1;
        int firstBinary = -1;
        for (int i = 0; i < ids.size(); i++) {
            if (ids.get(i).startsWith("cream-")) lastCream = i;
            if (ids.get(i).startsWith("binary") && firstBinary < 0) firstBinary = i;
        }
        org.assertj.core.api.Assertions.assertThat(firstBinary).isGreaterThan(lastCream);
        org.assertj.core.api.Assertions.assertThat(ids.subList(firstBinary, ids.size()))
                .containsExactly("binary-title", "binary-suffix", "binary");
    }

    @Test void firstMemberSortsSameMemberByNewestCreatedAt() throws Exception {
        var items = List.<Map<String, Object>>of(
                Map.of("id", "old", "member", "SeoYeon", "createdAt", "2024-01-01T00:00:00Z", "collectionNo", "1Z"),
                Map.of("id", "new", "member", "SeoYeon", "createdAt", "2026-10-01T00:00:00Z", "collectionNo", "3Z"),
                Map.of("id", "hyerin", "member", "HyeRin", "createdAt", "2026-11-01T00:00:00Z", "collectionNo", "8Z"));
        stubCatalog(items);
        when(market.withMintCounts(anyList())).thenAnswer(call -> call.getArgument(0));
        mvc.perform(get("/cosmo/objekt/more").param("page", "0").param("sort", "first_member"))
                .andExpect(jsonPath("$.items[0].id").value("new"))
                .andExpect(jsonPath("$.items[1].id").value("old"))
                .andExpect(jsonPath("$.items[2].id").value("hyerin"));
    }

    @Test void ownerEndpointValidatesSerialAndReturnsRecoverableFailure() throws Exception {
        Map<String, Object> item = Map.of("id", "uuid", "slug", "sample");
        stubCatalog(List.of(item));
        mvc.perform(get("/cosmo/objekt/detail/uuid/owner").param("serial", "0")).andExpect(status().isBadRequest());
        verifyNoInteractions(market);
        when(market.owner(item, 1)).thenThrow(new IllegalStateException("RPC unavailable"));
        mvc.perform(get("/cosmo/objekt/detail/uuid/owner").param("serial", "1"))
                .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.message").exists());
    }

    @Test void oldDetailPageOpensSharedCatalogModal() throws Exception {
        stubCatalog(List.of(Map.of("id","uuid","slug","sample")));
        mvc.perform(get("/cosmo/objekt/detail/sample").accept(MediaType.TEXT_HTML))
            .andExpect(redirectedUrl("/cosmo/objekt?objekt=sample"));
    }

    @Test void readableUrlsModalActionsAndLegacyMemberRouteAreConnected() throws Exception {
        Map<String, Object> item = Map.of("id", "uuid", "slug", "hayeoncc367z", "collectionId", "Cream02 HaYeon 367Z", "member", "HaYeon");
        stubCatalog(List.of(item));
        var hayeon = new com.example.triplesweb.entity.Artist();
        hayeon.setName("하연");
        hayeon.setEngName("HaYeon");
        hayeon.setColor("#ff6bb5");
        when(artists.findAll()).thenReturn(List.of(hayeon));
        mvc.perform(get("/cosmo/objekt/detail/uuid").accept(MediaType.TEXT_HTML))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/cosmo/objekt?objekt=hayeoncc367z"));
        mvc.perform(get("/cosmo/objekt/detail/uuid").accept(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.objektKey").value("hayeoncc367z"))
                .andExpect(jsonPath("$.detailUrl").value("/cosmo/objekt/detail/hayeoncc367z"))
                .andExpect(jsonPath("$.memberUrl").value("/cosmo/objekt?group=members&member=HaYeon"))
                .andExpect(jsonPath("$.memberColor").value("#ff6bb5"))
                .andExpect(jsonPath("$.tradeUrl").value("/article/trade/new?selectedObjektIds=uuid"))
                .andExpect(jsonPath("$.sellUrl").value("/article/sell/new?selectedObjektIds=uuid"));
        mvc.perform(get("/cosmo/objekt/member").param("name", "HaYeon"))
                .andExpect(redirectedUrl("/cosmo/objekt?group=members&member=HaYeon"));
    }

    @Test void realIndexSlugResolvesShortPublicAliasAndPreservesUpstreamAlias() throws Exception {
        var item = Map.<String,Object>of("id","uuid","slug","cream02-hayeon-367z","season","Cream02","collectionNo","367Z","member","HaYeon");
        stubCatalog(List.of(item));
        mvc.perform(get("/cosmo/objekt/detail/hayeoncc367z").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk()).andExpect(jsonPath("$.detailUrl").value("/cosmo/objekt/detail/hayeoncc367z"));
        mvc.perform(get("/cosmo/objekt/detail/cream02-hayeon-367z").accept(MediaType.TEXT_HTML))
                .andExpect(redirectedUrl("/cosmo/objekt?objekt=hayeoncc367z"));
    }

    @Test void uuidCatalogIdOpensReadableModalAddress() throws Exception {
        String uuid = "01a17092-abe1-72c0-925d-d7058df81a1f";
        var item = Map.<String, Object>of("id", uuid, "slug", uuid, "member", "HaYeon",
                "season", "Cream02", "collectionNo", "368Z");
        stubCatalog(List.of(item));
        mvc.perform(get("/cosmo/objekt/detail/" + uuid).accept(MediaType.TEXT_HTML))
                .andExpect(redirectedUrl("/cosmo/objekt?objekt=hayeoncc368z"));
        mvc.perform(get("/cosmo/objekt/detail/" + uuid).accept(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.objektKey").value("hayeoncc368z"))
                .andExpect(jsonPath("$.detailUrl").value("/cosmo/objekt/detail/hayeoncc368z"));
    }

    @Test void modalDescriptionWritesKeepStableKeyAndRequireAdmin() throws Exception {
        var item = Map.<String,Object>of("id", "uuid", "slug", "hayeoncc367z");
        stubCatalog(List.of(item));
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

    @Test void descriptionIsSharedBySeasonAndNumberWithoutOverwritingOtherGroups() throws Exception {
        var a = Map.<String,Object>of("id","a","slug","a","season","Cream02","collectionNo","367Z");
        var b = Map.<String,Object>of("id","b","slug","b","season","Cream02","collectionNo","367Z");
        var c = Map.<String,Object>of("id","c","slug","c","season","Cream02","collectionNo","368Z");
        stubCatalog(List.of(a,b,c));
        var stored = new java.util.HashMap<String,com.example.triplesweb.entity.ObjektDescription>();
        when(descriptions.findByObjektKey(anyString())).thenAnswer(call -> java.util.Optional.ofNullable(stored.get(call.getArgument(0))));
        when(descriptions.save(any())).thenAnswer(call -> {
            com.example.triplesweb.entity.ObjektDescription value=call.getArgument(0);
            stored.put(value.getObjektKey(),value); return value;
        });
        mvc.perform(post("/cosmo/objekt/detail/a/description").accept(MediaType.APPLICATION_JSON).param("description","공통 설명"))
                .andExpect(status().isOk());
        mvc.perform(get("/cosmo/objekt/detail/b").accept(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.description").value("공통 설명"));
        mvc.perform(get("/cosmo/objekt/detail/c").accept(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.description").value(""))
                .andExpect(jsonPath("$.apollo").doesNotExist());
        mvc.perform(post("/cosmo/objekt/detail/b/description").accept(MediaType.APPLICATION_JSON).param("description",""))
                .andExpect(status().isOk());
        mvc.perform(get("/cosmo/objekt/detail/a").accept(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.description").value(""));
    }

    @Test void memberSpecificNumbersKeepSeparateDescriptions() throws Exception {
        var seo = Map.<String, Object>of("id", "seo", "slug", "cream02-seoyeon-335a",
                "collectionId", "Cream02 SeoYeon 335A", "season", "Cream02", "collectionNo", "335A", "member", "SeoYeon");
        var yu = Map.<String, Object>of("id", "yu", "slug", "cream02-yubin-335a",
                "collectionId", "Cream02 YuBin 335A", "season", "Cream02", "collectionNo", "335A", "member", "YuBin");
        stubCatalog(List.of(seo, yu));
        var stored = new java.util.HashMap<String, com.example.triplesweb.entity.ObjektDescription>();
        var shared = new com.example.triplesweb.entity.ObjektDescription();
        shared.setObjektKey(com.example.triplesweb.util.ObjektDescriptionKeys.groupKey("Cream02", "335A"));
        shared.setDescription("공통으로 덮인 설명");
        stored.put(shared.getObjektKey(), shared);
        when(descriptions.findByObjektKey(anyString())).thenAnswer(call -> java.util.Optional.ofNullable(stored.get(call.getArgument(0))));
        when(descriptions.save(any())).thenAnswer(call -> {
            com.example.triplesweb.entity.ObjektDescription value = call.getArgument(0);
            stored.put(value.getObjektKey(), value);
            return value;
        });
        mvc.perform(get("/cosmo/objekt/detail/seo").accept(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.description").value(""));
        mvc.perform(post("/cosmo/objekt/detail/seo/description").accept(MediaType.APPLICATION_JSON).param("description", "넵튠"))
                .andExpect(status().isOk());
        mvc.perform(post("/cosmo/objekt/detail/yu/description").accept(MediaType.APPLICATION_JSON).param("description", "제니스"))
                .andExpect(status().isOk());
        mvc.perform(get("/cosmo/objekt/detail/seo").accept(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.description").value("넵튠"));
        mvc.perform(post("/cosmo/objekt/detail/seo/description").accept(MediaType.APPLICATION_JSON).param("description", ""))
                .andExpect(status().isOk());
        mvc.perform(get("/cosmo/objekt/detail/yu").accept(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.description").value("제니스"));
        mvc.perform(get("/cosmo/objekt/detail/seo").accept(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.description").value(""));
    }

    @Test void modalShowsLocalDescriptionOnlyWithoutApolloPayload() throws Exception {
        var item = Map.<String,Object>of("id","uuid","slug","cream02-kotone-356a","season","Cream02","collectionNo","356A","member","Kotone");
        stubCatalog(List.of(item));
        mvc.perform(get("/cosmo/objekt/detail/cream02-kotone-356a").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.description").value(""))
                .andExpect(jsonPath("$.apollo").doesNotExist());
        var stored = new com.example.triplesweb.entity.ObjektDescription();
        stored.setObjektKey("group-key");
        stored.setDescription("관리자 설명");
        when(descriptions.findByObjektKey(anyString())).thenReturn(java.util.Optional.of(stored));
        mvc.perform(get("/cosmo/objekt/detail/cream02-kotone-356a").accept(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.description").value("관리자 설명"))
                .andExpect(jsonPath("$.apollo").doesNotExist());
    }

    @Test void combinedLookupUsesIndexAndNotLiveRpc() throws Exception {
        var item = Map.<String,Object>of("id", "uuid", "slug", "hayeoncc367z");
        stubCatalog(List.of(item));
        when(market.serialLookup(item, 1, 0)).thenReturn(Map.of("status","available","owner",Map.of("status","indexed","nickname","A"),"transfers",List.of()));
        mvc.perform(get("/cosmo/objekt/detail/hayeoncc367z/lookup").param("serial","1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.owner.nickname").value("A"));
        verify(market, never()).owner(anyMap(), anyLong());
    }

    @Test void modalPreview() throws Exception {
        var item = new java.util.LinkedHashMap<String,Object>();
        for (String field : List.of("frontImage","backImage","thumbnailImage","seasonKey","classKey","artist","createdAt","searchText","backgroundColor","textColor")) item.put(field, "");
        item.putAll(Map.of("id","uuid","slug","hayeoncc367z","collectionId","Cream02 HaYeon 367Z","member","HaYeon","season","Cream02","class","Special","collectionNo","367Z","numberValue",367));
        stubCatalog(List.of(item));
        when(market.withMintCounts(anyList())).thenAnswer(call -> call.getArgument(0));
        var result = mvc.perform(get("/cosmo/objekt")).andExpect(status().isOk()).andReturn();
        java.nio.file.Files.writeString(java.nio.file.Path.of("build/objekt-modal-preview.html"), result.getResponse().getContentAsString());
        mvc.perform(get("/cosmo/objekt/detail/hayeoncc367z").accept(MediaType.TEXT_HTML))
            .andExpect(redirectedUrl("/cosmo/objekt?objekt=hayeoncc367z"));
        String html = result.getResponse().getContentAsString();
        org.assertj.core.api.Assertions.assertThat(html.indexOf("id=\"class-dropdown\""))
            .isGreaterThan(html.indexOf("id=\"season-dropdown\""))
            .isLessThan(html.indexOf("id=\"kind-dropdown\""));
        org.assertj.core.api.Assertions.assertThat(html.indexOf("id=\"kind-dropdown\""))
            .isLessThan(html.indexOf("id=\"sort-dropdown\""));
        org.assertj.core.api.Assertions.assertThat(html)
            .contains("data-serial-panel", "objektIssuedCount")
            .contains("objekt-member-logo")
            .contains("/images/objekt-member-logos/s19.png")
            .contains("id=\"objektDescriptionEdit\"")
            .contains("수정하기")
            .contains("id=\"objekt-kind-by-class\"")
            .contains("\"Special\":[\"367Z\"]")
            .doesNotContain("관리자 · 상세 설명 편집")
            .doesNotContain("objekt-head-mints")
            .doesNotContain("발행량 7,193,056개")
            .doesNotContain("objektIssuedRanges", "objektIssuedTime").doesNotContain("id=\"objektDetailFullLink\"");
    }

    @Test void cardSupplyDoesNotShowPolygonPageTotal() throws Exception {
        var item = new java.util.LinkedHashMap<String, Object>();
        for (String field : List.of("frontImage", "backImage", "thumbnailImage", "seasonKey", "classKey", "artist",
                "createdAt", "searchText", "backgroundColor", "textColor", "seasonColor", "publicKey", "memberHref")) {
            item.put(field, "");
        }
        item.putAll(Map.of("id", "uuid", "slug", "few", "collectionId", "Cream02 SeoYeon 367Z", "member", "SeoYeon",
                "season", "Cream02", "class", "Special", "collectionNo", "367Z", "numberValue", 367));
        item.put("mintCount", 90L);
        item.put("mintCountKnown", true);
        stubCatalog(List.of(item));
        var seoyeon = new com.example.triplesweb.entity.Artist();
        seoyeon.setName("서연");
        seoyeon.setEngName("SeoYeon");
        seoyeon.setColor("#7B61FF");
        when(artists.findAll()).thenReturn(List.of(seoyeon));
        when(market.withMintCounts(anyList())).thenAnswer(call -> call.getArgument(0));
        mvc.perform(get("/cosmo/objekt")).andExpect(status().isOk())
                .andExpect(content().string(containsString("data-scroll-top")))
                .andExpect(content().string(containsString("header-objekt-jump")))
                .andExpect(content().string(not(containsString("objekt-head-mints"))))
                .andExpect(content().string(not(containsString("발행량 7,193,056개"))))
                .andExpect(content().string(containsString("objekt-card-supply")))
                .andExpect(content().string(containsString("발행량 90개")))
                .andExpect(content().string(containsString("objekt-member-chip")))
                .andExpect(content().string(containsString("objekt-member-logo")))
                .andExpect(content().string(containsString("objekt-member-name")))
                .andExpect(content().string(containsString("--artist-color:#7B61FF")))
                .andExpect(content().string(containsString("/images/objekt-member-logos/s01.png")));
        mvc.perform(get("/cosmo/objekt/more").param("page", "0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].memberColor").value("#7B61FF"))
                .andExpect(jsonPath("$.items[0].memberLogo").value("/images/objekt-member-logos/s01.png"))
                .andExpect(jsonPath("$.items[0].hasMemberLogo").value(true));
    }

    @Test void ownerResponseDoesNotExposeInternalVerificationFields() throws Exception {
        Map<String, Object> item = Map.of("id", "uuid", "slug", "sample");
        stubCatalog(List.of(item));
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
        stubCatalog(List.of(item));
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
        stubCatalog(items);
        when(market.withMintCounts(anyList())).thenAnswer(call -> call.getArgument(0));
        var result = mvc.perform(get("/cosmo/objekt")).andExpect(status().isOk())
                .andExpect(model().attribute("memberOptions", List.of("윤서연", "정혜린", "카에데", "김채원", "지연")))
                .andExpect(model().attribute("seasonOptions", List.of("Cream02", "Binary02", "Atom02", "Ever01", "Divine01", "Cream01", "Binary01", "Atom01")))
                .andExpect(content().string(not(containsString("data-value=\"S1 X S9\""))))
                .andExpect(content().string(not(containsString("data-value=\"Alpha\""))))
                .andExpect(content().string(containsString("data-objekt-group=\"other\"")))
                .andExpect(content().string(containsString("artist-tab-fallback"))).andReturn();
        java.nio.file.Files.writeString(java.nio.file.Path.of("build/objekt-all-preview.html"), result.getResponse().getContentAsString());
        org.assertj.core.api.Assertions.assertThat(result.getResponse().getContentAsString())
                .contains("background:#FF8389").contains("background:#83FF00")
                .doesNotContain("bg-[#00e8cb]")
                .contains("id=\"objekt-kind-input\"").contains("value=\"전체\"")
                .doesNotContain("value=\"ALL\"");
        mvc.perform(get("/cosmo/objekt/more").param("page", "0").param("season", "Cream02"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].seasonColor").value("#FF8389"));
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
        stubCatalog(items);
        when(market.withMintCounts(anyList())).thenAnswer(call -> call.getArgument(0));
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

    @Test void mintCountsCopyOnlyTheVisiblePage() throws Exception {
        List<Map<String, Object>> items = new java.util.ArrayList<>();
        for (int i = 0; i < 45; i++) {
            items.add(Map.of("id", "id" + i, "slug", "slug" + i, "member", "SeoYeon"));
        }
        stubCatalog(items);
        when(market.withMintCounts(anyList())).thenAnswer(call -> call.getArgument(0));
        mvc.perform(get("/cosmo/objekt/more").param("page", "0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.loaded").value(20));
        org.mockito.ArgumentCaptor<List<Map<String, Object>>> copied =
                org.mockito.ArgumentCaptor.forClass(List.class);
        verify(market, atLeastOnce()).withMintCounts(copied.capture());
        for (List<Map<String, Object>> slice : copied.getAllValues()) {
            org.assertj.core.api.Assertions.assertThat(slice).hasSizeLessThanOrEqualTo(20);
        }
        verify(market, never()).withMintCounts(argThat(list -> list != null && list.size() > 20));
    }
}

