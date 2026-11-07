package com.example.triplesweb.util;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ObjektCatalogSortTest {
    @Test void kindMatchIgnoresAllAndNonAlphanumeric() {
        Map<String, Object> item = Map.of("collectionNo", "101Z");
        assertThat(ObjektCatalogSort.matchesKind("all", item)).isTrue();
        assertThat(ObjektCatalogSort.matchesKind("101Z", item)).isTrue();
        assertThat(ObjektCatalogSort.matchesKind("101z", item)).isTrue();
        assertThat(ObjektCatalogSort.matchesKind("102Z", item)).isFalse();
        assertThat(ObjektCatalogSort.normalizeKind("전체")).isEmpty();
    }

    @Test void numberSortUsesDigitsInsideCollectionNo() {
        List<Map<String, Object>> items = new ArrayList<>(List.of(
                Map.of("id", "b", "collectionNo", "102Z", "member", "HyeRin"),
                Map.of("id", "a", "collectionNo", "8Z", "member", "SeoYeon"),
                Map.of("id", "c", "collectionNo", "101Z", "member", "Jiwoo")));
        items.sort(ObjektCatalogSort.comparator("number_low"));
        assertThat(items).extracting(row -> row.get("collectionNo")).containsExactly("8Z", "101Z", "102Z");
        items.sort(ObjektCatalogSort.comparator("first_member"));
        assertThat(items).extracting(row -> row.get("member")).containsExactly("SeoYeon", "HyeRin", "Jiwoo");
    }
}
