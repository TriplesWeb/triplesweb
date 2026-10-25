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
        when(artists.findAll()).thenReturn(List.of());
    }
    @Test @SuppressWarnings("unchecked") void duplicateCopiesDoNotInflateCompletionAndWelcomeIsExcluded() {
        given(List.of(card("a", "HaYeon", "Double"), card("a", "HaYeon", "Double"), card("welcome", "HaYeon", "Welcome")),
                List.of(Map.of("slug", "a", "member", "HaYeon", "class", "Double", "season", "Cream02"),
                        Map.of("slug", "b", "member", "HaYeon", "class", "First", "season", "Cream02"),
                        Map.of("slug", "welcome", "member", "HaYeon", "class", "Welcome", "season", "Cream02")));
        var view = profiles.profile("테스트", "");
        var rows = (List<Map<String, Object>>) view.get("memberProgress");
        assertThat(view).containsEntry("uniqueCount", 2L).containsEntry("ownedCount", 3);
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0)).containsEntry("owned", 1L).containsEntry("total", 2).containsEntry("percent", 50).containsEntry("copies", 2);
        var classStats = (List<Map<String, Object>>) view.get("classStats");
        assertThat(classStats).extracting(r -> r.get("label")).containsExactlyInAnyOrder("Double", "First");
        assertThat(classStats).filteredOn(r -> "Double".equals(r.get("label")))
                .first().satisfies(r -> assertThat(r).containsEntry("owned", 1).containsEntry("total", 1).containsEntry("percent", 100));
        assertThat(classStats).filteredOn(r -> "First".equals(r.get("label")))
                .first().satisfies(r -> assertThat(r).containsEntry("owned", 0).containsEntry("total", 1).containsEntry("percent", 0));
    }
    @Test @SuppressWarnings("unchecked") void missingCatalogDoesNotFabricateACompletionRate() {
        given(List.of(card("a", "HaYeon", "Double")), List.of());
        var view = profiles.profile("테스트", "");
        assertThat(((List<Map<String, Object>>) view.get("memberProgress")).get(0)).containsEntry("hasTotal", false);
    }
    @Test void emptyProfileHasNoInventedStatistics() {
        given(List.of(), List.of());
        assertThat(profiles.profile("테스트", "")).containsEntry("hasStats", false)
                .containsEntry("uniqueCount", 0L).containsEntry("memberCount", 0L).containsEntry("hasMemberProgress", false)
                .containsEntry("hasOtherProgress", false);
    }

    private static Map<String, Object> kind(String slug, String member, String season, String klass, String number) {
        return Map.of("slug", slug, "member", member, "season", season, "class", klass,
                "collectionNo", number, "collectionId", season + " " + member + " " + number,
                "thumbnailImage", "https://example.com/" + slug + ".webp");
    }

    @Test @SuppressWarnings("unchecked") void checklistIncludesMissingKindsAndSortsNumbersNumericallyWithinEachClass() {
        given(List.of(card("owned", "HaYeon", "First"), card("owned", "HaYeon", "First")), List.of(
                kind("n101", "HaYeon", "Cream02", "First", "101Z"),
                kind("owned", "HaYeon", "Cream02", "First", "20Z"),
                kind("n3", "HaYeon", "Cream02", "First", "003Z"),
                kind("n201", "HaYeon", "Cream02", "Special", "201Z"),
                kind("welcome", "HaYeon", "Cream02", "Welcome", "000Z"),
                kind("other", "YuBin", "Cream02", "First", "001Z")));
        var view = profiles.profile("테스트", "hayeon");
        assertThat(view).containsEntry("checklistOwned", 1).containsEntry("checklistTotal", 4)
                .containsEntry("checklistMissing", 3).containsEntry("hasProgressMember", true);
        var seasons = (List<Map<String, Object>>) view.get("checklistSeasons");
        assertThat(seasons).hasSize(1);
        var groups = (List<Map<String, Object>>) seasons.get(0).get("groups");
        assertThat(groups).extracting(row -> row.get("klass")).containsExactly("First", "Special");
        var cards = (List<Map<String, Object>>) groups.get(0).get("cards");
        assertThat(cards).extracting(row -> row.get("number")).containsExactly("003Z", "20Z", "101Z");
        assertThat(cards.get(0)).containsEntry("owned", false).containsEntry("copies", 0L);
        assertThat(cards.get(1)).containsEntry("owned", true).containsEntry("copies", 2L);
        assertThat(groups.get(0)).containsEntry("complete", false).containsEntry("missing", 2);
    }

    @Test @SuppressWarnings("unchecked") void membersWithNoHoldingsCanStillOpenTheirEntireCatalog() {
        given(List.of(), List.of(kind("a", "YuBin", "Atom01", "First", "101Z"),
                kind("b", "YuBin", "Cream02", "Special", "201Z")));
        var view = profiles.profile("테스트", "YuBin");
        assertThat(view).containsEntry("checklistOwned", 0).containsEntry("checklistTotal", 2)
                .containsEntry("selectedMember", "공유빈");
        assertThat((List<Map<String, Object>>) view.get("checklistSeasons")).extracting(row -> row.get("season"))
                .containsExactly("Cream02", "Atom01");
        var progress = (List<Map<String, Object>>) view.get("memberProgress");
        assertThat(progress).hasSize(1);
        assertThat(progress.get(0)).containsEntry("member", "공유빈").containsEntry("owned", 0L)
                .containsEntry("total", 2).containsEntry("percent", 0).containsEntry("isEmpty", true);
    }

    @Test @SuppressWarnings("unchecked") void seasonCompletionUsesDistinctCatalogKindsNotCopyCount() {
        var kind = kind("a", "HaYeon", "Cream02", "First", "101Z");
        given(List.of(card("a", "HaYeon", "First"), card("a", "HaYeon", "First")), List.of(kind, kind));
        var view = profiles.profile("테스트", "HaYeon");
        assertThat(view).containsEntry("checklistTotal", 1).containsEntry("checklistOwned", 1);
        assertThat(((List<Map<String, Object>>) view.get("checklistSeasons")).get(0))
                .containsEntry("complete", true).containsEntry("percent", 100);
    }

    @Test @SuppressWarnings("unchecked") void profileMergesAliasesAndLegacyMemberUrlsSelectTheUnifiedName() {
        given(List.of(card("a", "S21", "First"), card("b", "ChaeWon", "First")), List.of(
                kind("a", "S21", "Cream02", "First", "101Z"),
                kind("b", "ChaeWon", "Cream02", "First", "102Z"),
                kind("c", "김채원", "Cream02", "First", "103Z")));
        var view = profiles.profile("테스트", "S21");
        assertThat(view).containsEntry("selectedMember", "김채원").containsEntry("checklistTotal", 3)
                .containsEntry("checklistOwned", 2).containsEntry("memberCount", 1L);
        var progress = (List<Map<String, Object>>) view.get("memberProgress");
        assertThat(progress).hasSize(1);
        assertThat(progress.get(0)).containsEntry("member", "김채원").containsEntry("total", 3).containsEntry("owned", 2L)
                .containsEntry("progressUrl", "/cosmo/profile/%ED%85%8C%EC%8A%A4%ED%8A%B8?tab=members&member=%EA%B9%80%EC%B1%84%EC%9B%90");
    }

    @Test @SuppressWarnings("unchecked") void unitObjektsCountTowardEachSoloMemberAndNeverAsUnitRow() {
        given(List.of(card("unit", "S19 X S24", "Unit")), List.of(
                kind("solo19", "HaYeon", "Cream02", "First", "101Z"),
                kind("solo24", "Jiyeon", "Cream02", "First", "102Z"),
                kind("unit", "S19 X S24", "Cream02", "Unit", "201Z")));
        var view = profiles.profile("테스트", "");
        var progress = (List<Map<String, Object>>) view.get("memberProgress");
        assertThat(progress).extracting(r -> r.get("member")).containsExactlyInAnyOrder("정하연", "지연");
        assertThat(progress).noneMatch(r -> String.valueOf(r.get("member")).contains("S19"));
        assertThat(progress).filteredOn(r -> "정하연".equals(r.get("member"))).first()
                .satisfies(r -> assertThat(r).containsEntry("owned", 1L).containsEntry("total", 2).containsEntry("copies", 1));
        assertThat(progress).filteredOn(r -> "지연".equals(r.get("member"))).first()
                .satisfies(r -> assertThat(r).containsEntry("owned", 1L).containsEntry("total", 2).containsEntry("copies", 1));
        var checklist = profiles.profile("테스트", "정하연");
        assertThat(checklist).containsEntry("checklistTotal", 2).containsEntry("checklistOwned", 1);
    }

    @Test @SuppressWarnings("unchecked") void otherObjektsLikeZenithAreMergedIntoOtherProgress() {
        given(List.of(card("z1", "zenith", "Special")), List.of(
                kind("z1", "zenith", "Cream02", "Special", "001Z"),
                kind("z2", "zenith", "Cream02", "Special", "002Z"),
                kind("alpha", "Alpha", "Cream02", "Special", "003Z")));
        var view = profiles.profile("테스트", "");
        assertThat((List<?>) view.get("memberProgress")).isEmpty();
        assertThat(view).containsEntry("hasOtherProgress", true);
        var other = ((List<Map<String, Object>>) view.get("otherProgress")).get(0);
        assertThat(other).containsEntry("member", "기타 옵젝").containsEntry("owned", 1L).containsEntry("total", 3)
                .containsEntry("percent", 33).containsEntry("progressUrl",
                        "/cosmo/profile/%ED%85%8C%EC%8A%A4%ED%8A%B8?tab=members&group=other");
        var detail = profiles.profile("테스트", "", "other", "members");
        assertThat(detail).containsEntry("isOtherGroup", true).containsEntry("checklistTotal", 3)
                .containsEntry("checklistOwned", 1).containsEntry("checklistMissing", 2);
    }
}
