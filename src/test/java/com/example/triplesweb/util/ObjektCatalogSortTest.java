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

    @Test void latestSortPutsLaterSeasonsFirstWhenDatesAreMissing() {
        List<Map<String, Object>> items = new ArrayList<>(List.of(
                Map.of("id", "binary", "season", "Binary01", "collectionNo", "201Z"),
                Map.of("id", "cream", "season", "Cream02", "collectionNo", "101Z"),
                Map.of("id", "atom", "season", "Atom01", "collectionNo", "001Z")));
        items.sort(ObjektCatalogSort.comparator("latest"));
        assertThat(items).extracting(row -> row.get("id")).containsExactly("cream", "binary", "atom");
        items.sort(ObjektCatalogSort.comparator("oldest"));
        assertThat(items).extracting(row -> row.get("id")).containsExactly("atom", "binary", "cream");
    }

    @Test void latestSortPrefersSeasonOverLaterCreatedAt() {
        List<Map<String, Object>> items = new ArrayList<>(List.of(
                Map.of("id", "binary", "season", "Binary01", "createdAt", "2026-05-22T02:36:12.000Z", "collectionNo", "118A"),
                Map.of("id", "cream", "season", "Cream01", "createdAt", "2024-05-07T06:15:27.000Z", "collectionNo", "332Z"),
                Map.of("id", "divine", "season", "Divine01", "createdAt", "2024-11-08T03:00:16.000Z", "collectionNo", "343Z")));
        items.sort(ObjektCatalogSort.comparator("latest"));
        assertThat(items).extracting(row -> row.get("id")).containsExactly("divine", "cream", "binary");
        items.sort(ObjektCatalogSort.comparator("oldest"));
        assertThat(items).extracting(row -> row.get("id")).containsExactly("binary", "cream", "divine");
    }

    @Test void latestSortKeepsMislabeledBinary01InsideBinaryGroup() {
        List<Map<String, Object>> items = new ArrayList<>(List.of(
                Map.of("id", "binary-suffix", "season", "Binary01 First", "createdAt", "2026-10-01T00:00:00Z", "collectionNo", "1Z"),
                Map.of("id", "binary-title", "collectionId", "Binary01 SeoYeon 118A", "createdAt", "2026-11-01T00:00:00Z", "collectionNo", "118A"),
                Map.of("id", "cream", "season", "Cream02", "createdAt", "2024-01-01T00:00:00Z", "collectionNo", "101Z"),
                Map.of("id", "binary", "season", "Binary01", "createdAt", "2024-02-01T00:00:00Z", "collectionNo", "201Z"),
                Map.of("id", "unknown", "season", "Love&Pop", "createdAt", "2026-12-01T00:00:00Z", "collectionNo", "9Z")));
        items.sort(ObjektCatalogSort.comparator("latest"));
        assertThat(items).extracting(row -> row.get("id"))
                .containsExactly("cream", "binary-title", "binary-suffix", "binary", "unknown");
    }

    @Test void collectionLatestSortsBySeasonThenReceivedIndex() {
        List<Map<String, Object>> items = new ArrayList<>(List.of(
                Map.of("id", "binary", "season", "Binary01", "receivedIndex", 0, "member", "SeoYeon", "collectionNo", "1Z"),
                Map.of("id", "cream-old", "season", "Cream01", "receivedIndex", 1, "member", "HyeRin", "collectionNo", "2Z"),
                Map.of("id", "divine", "season", "Divine01", "receivedIndex", 2, "member", "JiYeon", "collectionNo", "3Z"),
                Map.of("id", "cream-new", "season", "Cream01", "receivedIndex", 3, "member", "HyeRin", "collectionNo", "4Z")));
        items.sort(ObjektCatalogSort.collectionComparator("latest"));
        assertThat(items).extracting(row -> row.get("id"))
                .containsExactly("divine", "cream-old", "cream-new", "binary");
        items.sort(ObjektCatalogSort.collectionComparator("oldest"));
        assertThat(items).extracting(row -> row.get("id"))
                .containsExactly("binary", "cream-new", "cream-old", "divine");
        items.sort(ObjektCatalogSort.collectionComparator("last_member"));
        assertThat(items).extracting(row -> row.get("id"))
                .containsExactly("divine", "cream-old", "cream-new", "binary");
        items.sort(ObjektCatalogSort.collectionComparator("first_member"));
        assertThat(items).extracting(row -> row.get("id"))
                .containsExactly("binary", "cream-old", "cream-new", "divine");
    }

    @Test void lastMemberSortsJiyeonBeforeSeoyeonEvenWithOlderDate() {
        List<Map<String, Object>> items = new ArrayList<>(List.of(
                Map.of("id", "seoyeon-new", "member", "SeoYeon", "createdAt", "2026-10-01T00:00:00Z", "collectionNo", "1Z"),
                Map.of("id", "jiyeon-old", "member", "JiYeon", "createdAt", "2024-01-01T00:00:00Z", "collectionNo", "24Z")));
        items.sort(ObjektCatalogSort.comparator("last_member"));
        assertThat(items).extracting(row -> row.get("id")).containsExactly("jiyeon-old", "seoyeon-new");
    }

    @Test void firstMemberSortsSeoYeonNewestFirstThenLaterMembers() {
        List<Map<String, Object>> items = new ArrayList<>(List.of(
                Map.of("id", "hyerin-new", "member", "HyeRin", "createdAt", "2026-11-01T00:00:00Z", "collectionNo", "8Z"),
                Map.of("id", "seoyeon-old", "member", "SeoYeon", "createdAt", "2024-01-01T00:00:00Z", "collectionNo", "1Z"),
                Map.of("id", "seoyeon-new", "member", "SeoYeon", "createdAt", "2026-10-01T00:00:00Z", "collectionNo", "3Z")));
        items.sort(ObjektCatalogSort.comparator("first_member"));
        assertThat(items).extracting(row -> row.get("id"))
                .containsExactly("seoyeon-new", "seoyeon-old", "hyerin-new");
    }
}
