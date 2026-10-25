package com.example.triplesweb.service;

import com.example.triplesweb.repository.ArtistRepository;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class CosmoProfileServiceTest {
    private final CosmoCollectionService collections = mock(CosmoCollectionService.class);
    private final ObjektService catalog = mock(ObjektService.class);
    private final ArtistRepository artists = mock(ArtistRepository.class);
    private final CosmoProfileService profiles = new CosmoProfileService(collections, catalog, artists);

    private static Map<String, Object> card(String slug, String member, String klass) {
        return Map.of("slug", slug, "member", member, "objektClass", klass, "season", "Cream02", "artist", "triples");
    }
    private void given(List<Map<String, Object>> held, List<Map<String, Object>> all) {
        when(collections.search("테스트", 0)).thenReturn(Map.of("allCards", held, "ownedCount", held.size(),
                "nickname", "테스트", "profileUrl", "/cosmo/profile/%ED%85%8C%EC%8A%A4%ED%8A%B8"));
        when(catalog.findCollections("triples")).thenReturn(all);
    }
    @Test @SuppressWarnings("unchecked") void duplicateCopiesDoNotInflateCompletionAndWelcomeIsExcluded() {
        given(List.of(card("a", "HaYeon", "Double"), card("a", "HaYeon", "Double"), card("welcome", "HaYeon", "Welcome")),
                List.of(Map.of("slug", "a", "member", "HaYeon", "class", "Double"),
                        Map.of("slug", "b", "member", "HaYeon", "class", "First"),
                        Map.of("slug", "welcome", "member", "HaYeon", "class", "Welcome")));
        var view = profiles.profile("테스트", "progress", "", 0);
        var rows = (List<Map<String, Object>>) view.get("memberProgress");
        assertThat(view).containsEntry("uniqueCount", 2L).containsEntry("ownedCount", 3);
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0)).containsEntry("owned", 1L).containsEntry("total", 2).containsEntry("percent", 50).containsEntry("copies", 2);
        assertThat((List<Map<String, Object>>) view.get("classStats")).extracting(r -> r.get("count")).containsExactly(2L, 1L);
    }
    @Test @SuppressWarnings("unchecked") void missingCatalogDoesNotFabricateACompletionRate() {
        given(List.of(card("a", "HaYeon", "Double")), List.of());
        var view = profiles.profile("테스트", "progress", "", 0);
        assertThat(((List<Map<String, Object>>) view.get("memberProgress")).get(0)).containsEntry("hasTotal", false);
    }
    @Test @SuppressWarnings("unchecked") void memberFilteredCollectionPaginatesWithoutReloadingEachCard() {
        List<Map<String, Object>> held = new ArrayList<>();
        for (int i = 0; i < 26; i++) held.add(card("a" + i, "HaYeon", "Double"));
        held.add(card("other", "Jiyeon", "First"));
        given(held, List.of());
        var view = profiles.profile("테스트", "collection", "HaYeon", Integer.MAX_VALUE);
        assertThat(view).containsEntry("filteredCount", 26).containsEntry("pageDisplay", 2).containsEntry("hasNext", false);
        assertThat((List<Map<String, Object>>) view.get("cards")).hasSize(2);
        assertThat(view.get("previousUrl").toString()).contains("member=HaYeon", "page=0");
        verify(collections).search("테스트", 0);
    }
    @Test void emptyProfileHasNoInventedStatistics() {
        given(List.of(), List.of());
        assertThat(profiles.profile("테스트", "progress", "", 0)).containsEntry("hasStats", false)
                .containsEntry("uniqueCount", 0L).containsEntry("memberCount", 0L).containsEntry("hasMemberProgress", false);
    }
}
