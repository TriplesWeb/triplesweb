package com.example.triplesweb.service;

import com.example.triplesweb.entity.ObjektDescription;
import com.example.triplesweb.entity.ObjektDescriptionScope;
import com.example.triplesweb.repository.ObjektDescriptionRepository;
import com.example.triplesweb.repository.ObjektDescriptionScopeRepository;
import com.example.triplesweb.util.ObjektDescriptionKeys;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ObjektDescriptionEditorTest {
    private final Map<String, ObjektDescription> stored = new HashMap<>();
    private final Map<ObjektDescriptionScope.Key, ObjektDescriptionScope> scopes = new HashMap<>();

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
    void viewWithoutGroupCountDoesNotScanCatalog() {
        Map<String, Object> seo = card("SeoYeon", "323");
        ObjektService objekts = mock(ObjektService.class);
        ObjektDescriptionEditor editor = editor(List.of(seo, card("YuBin", "323")), objekts);
        assertEquals(1, editor.view(seo, false).get("descriptionGroupCount"));
        assertEquals("group", editor.view(seo, false).get("descriptionScope"));
        verify(objekts, never()).findCollections(any());
        verify(objekts, never()).groupCount(any());
    }

    private ObjektDescriptionEditor editor(List<Map<String, Object>> catalog) {
        return editor(catalog, mock(ObjektService.class));
    }

    private ObjektDescriptionEditor editor(List<Map<String, Object>> catalog, ObjektService objekts) {
        when(objekts.findCollections("triples")).thenReturn(catalog);
        when(objekts.groupCount(any())).thenAnswer(call -> {
            Map<String, Object> item = call.getArgument(0);
            String season = String.valueOf(item.get("season"));
            String number = String.valueOf(item.get("collectionNo"));
            int count = 0;
            for (Map<String, Object> candidate : catalog) {
                if (season.equals(candidate.get("season")) && number.equals(candidate.get("collectionNo"))) count++;
            }
            return Math.max(1, count);
        });
        ObjektDescriptionRepository descriptions = mock(ObjektDescriptionRepository.class);
        ObjektDescriptionScopeRepository scopeRepo = mock(ObjektDescriptionScopeRepository.class);
        when(descriptions.findByObjektKey(any())).thenAnswer(call -> Optional.ofNullable(stored.get(call.getArgument(0))));
        when(descriptions.save(any())).thenAnswer(call -> {
            ObjektDescription value = call.getArgument(0);
            stored.put(value.getObjektKey(), value);
            return value;
        });
        when(scopeRepo.existsById(any())).thenAnswer(call -> scopes.containsKey(call.getArgument(0)));
        when(scopeRepo.save(any())).thenAnswer(call -> {
            ObjektDescriptionScope value = call.getArgument(0);
            scopes.put(value.getId(), value);
            return value;
        });
        return new ObjektDescriptionEditor(objekts, descriptions, scopeRepo);
    }

    private static Map<String, Object> card(String member, String number) {
        return Map.of(
                "id", member.toLowerCase(),
                "slug", "cream02-" + member.toLowerCase() + "-" + number.toLowerCase(),
                "season", "Cream02",
                "collectionNo", number,
                "member", member);
    }
}
