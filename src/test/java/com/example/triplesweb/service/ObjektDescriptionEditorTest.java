package com.example.triplesweb.service;

import com.example.triplesweb.entity.ObjektDescription;
import com.example.triplesweb.entity.ObjektDescriptionScope;
import com.example.triplesweb.entity.ObjektDescriptionSharePref;
import com.example.triplesweb.repository.ObjektDescriptionRepository;
import com.example.triplesweb.repository.ObjektDescriptionScopeRepository;
import com.example.triplesweb.repository.ObjektDescriptionSharePrefRepository;
import com.example.triplesweb.util.ObjektDescriptionKeys;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ObjektDescriptionEditorTest {
    private final Map<String, ObjektDescription> stored = new HashMap<>();
    private final Map<ObjektDescriptionScope.Key, ObjektDescriptionScope> scopes = new HashMap<>();
    private final Map<ObjektDescriptionSharePref.Key, ObjektDescriptionSharePref> prefs = new HashMap<>();

    @AfterEach
    void resetRuntime() {
        ObjektDescriptionKeys.clearRuntime();
    }

    @Test
    void groupSaveKeepsOneSharedRow() {
        Map<String, Object> seo = card("SeoYeon", "323");
        Map<String, Object> yu = card("YuBin", "323");
        ObjektDescriptionEditor editor = editor(List.of(seo, yu));

        editor.save(seo, "공통", "group");

        assertEquals("공통", stored.get(ObjektDescriptionKeys.groupKey("Cream02", "323")).getDescription());
        assertFalse(ObjektDescriptionKeys.memberSpecific("Cream02", "323"));
        assertEquals("group", editor.view(seo).get("descriptionScope"));
        assertEquals(2, editor.view(seo).get("descriptionGroupCount"));
    }

    @Test
    void memberSaveSplitsWithoutChangingTheSibling() {
        Map<String, Object> seo = card("SeoYeon", "323");
        Map<String, Object> yu = card("YuBin", "323");
        ObjektDescriptionEditor editor = editor(List.of(seo, yu));
        editor.save(seo, "공통", "group");

        editor.save(seo, "서연만", "member");

        assertTrue(ObjektDescriptionKeys.memberSpecific("Cream02", "323"));
        assertEquals("서연만", stored.get(ObjektDescriptionKeys.memberKey("Cream02", "323", "SeoYeon")).getDescription());
        assertEquals("공통", stored.get(ObjektDescriptionKeys.memberKey("Cream02", "323", "YuBin")).getDescription());
        assertEquals("member", editor.view(yu).get("descriptionScope"));
    }

    @Test
    void selectedMembersShareAcrossSelectedNumbers() {
        Map<String, Object> seo323 = card("SeoYeon", "323");
        Map<String, Object> yu323 = card("YuBin", "323");
        Map<String, Object> ji323 = card("JiWoo", "323");
        Map<String, Object> seo324 = card("SeoYeon", "324");
        Map<String, Object> yu324 = card("YuBin", "324");
        Map<String, Object> ji324 = card("JiWoo", "324");
        ObjektDescriptionEditor editor = editor(List.of(seo323, yu323, ji323, seo324, yu324, ji324));
        editor.save(seo323, "공통323", "group");
        editor.save(seo324, "공통324", "group");

        editor.save(seo323, "서연·유빈 공유", null, List.of("seoyeon", "yubin"), List.of("323", "324"));

        assertTrue(ObjektDescriptionKeys.memberSpecific("Cream02", "323"));
        assertTrue(ObjektDescriptionKeys.memberSpecific("Cream02", "324"));
        assertEquals("서연·유빈 공유",
                stored.get(ObjektDescriptionKeys.memberKey("Cream02", "323", "SeoYeon")).getDescription());
        assertEquals("서연·유빈 공유",
                stored.get(ObjektDescriptionKeys.memberKey("Cream02", "323", "YuBin")).getDescription());
        assertEquals("공통323",
                stored.get(ObjektDescriptionKeys.memberKey("Cream02", "323", "JiWoo")).getDescription());
        assertEquals("서연·유빈 공유",
                stored.get(ObjektDescriptionKeys.memberKey("Cream02", "324", "SeoYeon")).getDescription());
        assertEquals("서연·유빈 공유",
                stored.get(ObjektDescriptionKeys.memberKey("Cream02", "324", "YuBin")).getDescription());
        assertEquals("공통324",
                stored.get(ObjektDescriptionKeys.memberKey("Cream02", "324", "JiWoo")).getDescription());
    }

    @Test
    void groupSaveOnAJsonExceptionUpdatesEveryMemberKey() {
        Map<String, Object> seo = card("SeoYeon", "335A");
        Map<String, Object> yu = card("YuBin", "335A");
        ObjektDescriptionEditor editor = editor(List.of(seo, yu));

        editor.save(seo, "일괄", "group");

        assertTrue(ObjektDescriptionKeys.jsonException("Cream02", "335A"));
        assertEquals("일괄", stored.get(ObjektDescriptionKeys.memberKey("Cream02", "335A", "SeoYeon")).getDescription());
        assertEquals("일괄", stored.get(ObjektDescriptionKeys.memberKey("Cream02", "335A", "YuBin")).getDescription());
        assertEquals("member", editor.view(seo).get("descriptionScope"));
    }

    @Test
    void viewWithoutShareOptionsDoesNotScanGroups() {
        Map<String, Object> seo = card("SeoYeon", "323");
        ObjektService objekts = mock(ObjektService.class);
        ObjektDescriptionEditor editor = editor(List.of(seo, card("YuBin", "323")), objekts);
        assertEquals(2, editor.view(seo, false).get("descriptionGroupCount"));
        assertEquals("group", editor.view(seo, false).get("descriptionScope"));
        assertEquals(List.of(), editor.view(seo, false).get("descriptionShareMembers"));
        assertEquals(List.of(), editor.view(seo, false).get("descriptionSeasonNumbers"));
        verify(objekts, never()).groupCards(any(), any());
        verify(objekts, never()).seasonCollectionNumbers(any());
        verify(objekts, never()).findCollections(any());
        verify(objekts, atLeastOnce()).groupCount(any());
    }

    @Test
    void saveRemembersSelectedMembersAndNumbersForNextEdit() {
        Map<String, Object> seo323 = card("SeoYeon", "323");
        Map<String, Object> yu323 = card("YuBin", "323");
        Map<String, Object> seo324 = card("SeoYeon", "324");
        ObjektDescriptionEditor editor = editor(List.of(seo323, yu323, seo324));

        editor.save(seo323, "공유", null, List.of("seoyeon", "yubin"), List.of("323", "324"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> members = (List<Map<String, Object>>) editor.view(seo323).get("descriptionShareMembers");
        assertEquals(List.of("323", "324"), editor.view(seo323).get("descriptionSelectedNumbers"));
        assertTrue(members.stream().anyMatch(row -> "seoyeon".equals(row.get("token")) && Boolean.TRUE.equals(row.get("selected"))));
        assertTrue(members.stream().anyMatch(row -> "yubin".equals(row.get("token")) && Boolean.TRUE.equals(row.get("selected"))));
    }

    private ObjektDescriptionEditor editor(List<Map<String, Object>> catalog) {
        return editor(catalog, mock(ObjektService.class));
    }

    private ObjektDescriptionEditor editor(List<Map<String, Object>> catalog, ObjektService objekts) {
        Map<String, List<Map<String, Object>>> groups = new LinkedHashMap<>();
        Map<String, LinkedHashSet<String>> seasonNumbers = new LinkedHashMap<>();
        for (Map<String, Object> item : catalog) {
            String season = String.valueOf(item.get("season"));
            String number = String.valueOf(item.get("collectionNo"));
            String pair = season.toLowerCase(Locale.ROOT) + "|" + number.toLowerCase(Locale.ROOT);
            groups.computeIfAbsent(pair, ignored -> new ArrayList<>()).add(item);
            seasonNumbers.computeIfAbsent(season.toLowerCase(Locale.ROOT), ignored -> new LinkedHashSet<>()).add(number);
        }
        when(objekts.groupCards(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString()))
                .thenAnswer(call -> {
                    String season = call.getArgument(0, String.class).toLowerCase(Locale.ROOT);
                    String number = call.getArgument(1, String.class).toLowerCase(Locale.ROOT);
                    return groups.getOrDefault(season + "|" + number, List.of());
                });
        when(objekts.seasonCollectionNumbers(org.mockito.ArgumentMatchers.anyString())).thenAnswer(call -> {
            String season = call.getArgument(0, String.class).toLowerCase(Locale.ROOT);
            return List.copyOf(seasonNumbers.getOrDefault(season, new LinkedHashSet<>()));
        });
        when(objekts.groupCount(any())).thenAnswer(call -> {
            Map<String, Object> item = call.getArgument(0);
            String season = String.valueOf(item.get("season"));
            String number = String.valueOf(item.get("collectionNo"));
            return Math.max(1, groups.getOrDefault(
                    season.toLowerCase(Locale.ROOT) + "|" + number.toLowerCase(Locale.ROOT), List.of()).size());
        });
        ObjektDescriptionRepository descriptions = mock(ObjektDescriptionRepository.class);
        ObjektDescriptionScopeRepository scopeRepo = mock(ObjektDescriptionScopeRepository.class);
        ObjektDescriptionSharePrefRepository prefRepo = mock(ObjektDescriptionSharePrefRepository.class);
        when(descriptions.findByObjektKey(any())).thenAnswer(call -> Optional.ofNullable(stored.get(call.getArgument(0))));
        when(descriptions.findByObjektKeyIn(org.mockito.ArgumentMatchers.anyCollection())).thenAnswer(call -> {
            Collection<?> keys = call.getArgument(0);
            List<ObjektDescription> rows = new ArrayList<>();
            for (Object key : keys) {
                ObjektDescription row = stored.get(String.valueOf(key));
                if (row != null) rows.add(row);
            }
            return rows;
        });
        when(descriptions.save(any())).thenAnswer(call -> {
            ObjektDescription value = call.getArgument(0);
            stored.put(value.getObjektKey(), value);
            return value;
        });
        when(descriptions.saveAll(org.mockito.ArgumentMatchers.anyCollection())).thenAnswer(call -> {
            Collection<ObjektDescription> values = call.getArgument(0);
            for (ObjektDescription value : values) stored.put(value.getObjektKey(), value);
            return List.copyOf(values);
        });
        when(scopeRepo.existsById(any())).thenAnswer(call -> scopes.containsKey(call.getArgument(0)));
        when(scopeRepo.findAllById(org.mockito.ArgumentMatchers.anyCollection())).thenAnswer(call -> {
            Collection<?> keys = call.getArgument(0);
            List<ObjektDescriptionScope> rows = new ArrayList<>();
            for (Object key : keys) {
                ObjektDescriptionScope row = scopes.get(key);
                if (row != null) rows.add(row);
            }
            return rows;
        });
        when(scopeRepo.save(any())).thenAnswer(call -> {
            ObjektDescriptionScope value = call.getArgument(0);
            scopes.put(value.getId(), value);
            return value;
        });
        when(scopeRepo.saveAll(org.mockito.ArgumentMatchers.anyCollection())).thenAnswer(call -> {
            Collection<ObjektDescriptionScope> values = call.getArgument(0);
            for (ObjektDescriptionScope value : values) scopes.put(value.getId(), value);
            return List.copyOf(values);
        });
        org.mockito.Mockito.doAnswer(call -> {
            Collection<ObjektDescriptionScope> values = call.getArgument(0);
            for (ObjektDescriptionScope value : values) scopes.remove(value.getId());
            return null;
        }).when(scopeRepo).deleteAllInBatch(org.mockito.ArgumentMatchers.anyCollection());
        when(prefRepo.findById(any())).thenAnswer(call -> Optional.ofNullable(prefs.get(call.getArgument(0))));
        when(prefRepo.findAllById(org.mockito.ArgumentMatchers.anyCollection())).thenAnswer(call -> {
            Collection<?> keys = call.getArgument(0);
            List<ObjektDescriptionSharePref> rows = new ArrayList<>();
            for (Object key : keys) {
                ObjektDescriptionSharePref row = prefs.get(key);
                if (row != null) rows.add(row);
            }
            return rows;
        });
        when(prefRepo.save(any())).thenAnswer(call -> {
            ObjektDescriptionSharePref value = call.getArgument(0);
            prefs.put(value.getId(), value);
            return value;
        });
        when(prefRepo.saveAll(org.mockito.ArgumentMatchers.anyCollection())).thenAnswer(call -> {
            Collection<ObjektDescriptionSharePref> values = call.getArgument(0);
            for (ObjektDescriptionSharePref value : values) prefs.put(value.getId(), value);
            return List.copyOf(values);
        });
        return new ObjektDescriptionEditor(objekts, descriptions, scopeRepo, prefRepo);
    }

    private static Map<String, Object> card(String member, String number) {
        return Map.of(
                "id", member.toLowerCase() + "-" + number.toLowerCase(),
                "slug", "cream02-" + member.toLowerCase() + "-" + number.toLowerCase(),
                "season", "Cream02",
                "collectionNo", number,
                "member", member);
    }
}
