package com.example.triplesweb.util;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ObjektDescriptionKeysTest {
    @org.junit.jupiter.api.AfterEach
    void resetRuntime() {
        ObjektDescriptionKeys.clearRuntime();
    }

    @Test
    void runtimeMemberSpecificCanBeClearedUnlikeJsonExceptions() {
        assertTrue(ObjektDescriptionKeys.jsonException("Cream02", "335A"));
        assertFalse(ObjektDescriptionKeys.jsonException("Cream02", "323"));
        ObjektDescriptionKeys.markMemberSpecific("Cream02", "323");
        assertTrue(ObjektDescriptionKeys.memberSpecific("Cream02", "323"));
        ObjektDescriptionKeys.clearMemberSpecific("Cream02", "323");
        assertFalse(ObjektDescriptionKeys.memberSpecific("Cream02", "323"));
        ObjektDescriptionKeys.clearMemberSpecific("Cream02", "335A");
        assertTrue(ObjektDescriptionKeys.memberSpecific("Cream02", "335A"));
    }

    @Test
    void keepsSharedKeyUnlessTheSeasonAndNumberAreMemberSpecific() {
        assertEquals(50, ObjektDescriptionKeys.members().stream()
                .map(row -> row.season() + "|" + row.collectionNo())
                .distinct()
                .count());
        assertTrue(ObjektDescriptionKeys.memberSpecific("Cream02", "335A"));
        assertFalse(ObjektDescriptionKeys.memberSpecific("Cream02", "367Z"));
        assertFalse(ObjektDescriptionKeys.memberSpecific("Cream01", "304Z"));

        Map<String, Object> shared = Map.of("id", "a", "season", "Cream02", "collectionNo", "367Z", "member", "SeoYeon");
        Map<String, Object> sibling = Map.of("id", "b", "season", "Cream02", "collectionNo", "367Z", "member", "YuBin");
        assertEquals(ObjektDescriptionKeys.storageKey(shared), ObjektDescriptionKeys.storageKey(sibling));
        assertTrue(ObjektDescriptionKeys.storageKey(shared).startsWith("group-"));
    }

    @Test
    void memberKeyIgnoresTheSharedGroupRow() {
        Map<String, Object> seo = Map.of(
                "id", "seo", "slug", "cream02-seoyeon-335a", "collectionId", "Cream02 SeoYeon 335A",
                "season", "Cream02", "collectionNo", "335A", "member", "SeoYeon");
        Map<String, Object> yu = Map.of(
                "id", "yu", "slug", "cream02-yubin-335a", "collectionId", "Cream02 YuBin 335A",
                "season", "Cream02", "collectionNo", "335A", "member", "YuBin");
        String seoKey = ObjektDescriptionKeys.storageKey(seo);
        String yuKey = ObjektDescriptionKeys.storageKey(yu);
        assertTrue(seoKey.startsWith("member-"));
        assertFalse(seoKey.equals(yuKey));
        assertEquals(seoKey, ObjektDescriptionKeys.memberKey("cream02", "335a", "SeoYeon"));

        List<String> keys = ObjektDescriptionKeys.readKeys(seo, "cream02-seoyeon-335a");
        assertEquals(List.of("member", "slug", "collection", "id"),
                List.of(kind(keys.get(0)), kind(keys.get(1)), kind(keys.get(2)), kind(keys.get(3))));
        assertFalse(keys.contains(ObjektDescriptionKeys.groupKey("Cream02", "335A")));
        assertEquals(seoKey, keys.get(0));
        assertEquals("cream02-seoyeon-335a", keys.get(1));
        assertEquals("Cream02 SeoYeon 335A", keys.get(2));
    }

    @Test
    void koreanMemberNameUsesTheSlugToken() {
        Map<String, Object> seo = Map.of(
                "id", "seo", "slug", "cream02-seoyeon-335a", "season", "Cream02",
                "collectionNo", "335A", "member", "서연");
        assertEquals(ObjektDescriptionKeys.memberKey("cream02", "335a", "seoyeon"),
                ObjektDescriptionKeys.storageKey(seo));
    }

    @Test
    void everyExceptionGroupHasAtLeastTwoDescriptions() {
        Map<String, Set<String>> texts = ObjektDescriptionKeys.members().stream()
                .collect(Collectors.groupingBy(
                        row -> row.season() + "|" + row.collectionNo(),
                        Collectors.mapping(ObjektDescriptionKeys.MemberText::description, Collectors.toSet())));
        assertEquals(50, texts.size());
        assertTrue(texts.values().stream().allMatch(values -> values.stream().filter(text -> !text.isBlank()).count() >= 2));
    }

    private static String kind(String key) {
        if (key.startsWith("member-")) return "member";
        if (key.startsWith("cream02-")) return "slug";
        if (key.startsWith("Cream02 ")) return "collection";
        return "id";
    }
}
