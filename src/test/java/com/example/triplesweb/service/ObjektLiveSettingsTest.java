package com.example.triplesweb.service;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class ObjektLiveSettingsTest {
    private static Clock at(int hour, int minute) {
        Instant instant = LocalDateTime.of(2026, 11, 10, hour, minute)
                .atZone(ObjektLiveSettings.ZONE).toInstant();
        return Clock.fixed(instant, ZoneOffset.UTC);
    }

    private static ObjektLiveSettings settings(Clock clock) {
        return new ObjektLiveSettings(clock, 20_000, 10_000, 60_000, 30_000, 600_000, 30_000, 15_000);
    }

    @Test void dropWindowIsTenAmToMidnightSeoul() {
        assertThat(settings(at(9, 59)).dropWindow()).isFalse();
        assertThat(settings(at(10, 0)).dropWindow()).isTrue();
        assertThat(settings(at(12, 0)).dropWindow()).isTrue();
        assertThat(settings(at(22, 0)).dropWindow()).isTrue();
        assertThat(settings(at(23, 59)).dropWindow()).isTrue();
        Instant midnight = LocalDateTime.of(2026, 11, 11, 0, 0)
                .atZone(ObjektLiveSettings.ZONE).toInstant();
        assertThat(settings(Clock.fixed(midnight, ZoneOffset.UTC)).dropWindow()).isFalse();
    }

    @Test void dropWindowUsesFasterPollAndHistoryTtl() {
        ObjektLiveSettings morning = settings(at(9, 0));
        assertThat(morning.pollMs()).isEqualTo(20_000);
        assertThat(morning.refreshMs()).isEqualTo(60_000);
        assertThat(morning.historyTtlMs()).isEqualTo(30_000);
        ObjektLiveSettings drop = settings(at(10, 30));
        assertThat(drop.pollMs()).isEqualTo(10_000);
        assertThat(drop.refreshMs()).isEqualTo(30_000);
        assertThat(drop.historyTtlMs()).isEqualTo(15_000);
        assertThat(drop.forceRefreshMs()).isEqualTo(600_000);
    }

    @Test void dueUsesDropRefreshDuringWindow() {
        ObjektLiveSettings drop = settings(at(10, 10));
        long now = System.currentTimeMillis();
        assertThat(drop.due(0)).isTrue();
        assertThat(drop.due(now - 29_000)).isFalse();
        assertThat(drop.due(now - 31_000)).isTrue();
    }

    @Test void catalogSignatureChangesWhenACollectionAppears() {
        var before = java.util.List.of(java.util.Map.<String, Object>of("id", "a", "collectionId", "A"));
        var after = java.util.List.of(
                java.util.Map.<String, Object>of("id", "b", "collectionId", "B"),
                java.util.Map.<String, Object>of("id", "a", "collectionId", "A"));
        assertThat(ObjektService.signature(before)).isNotEqualTo(ObjektService.signature(after));
        assertThat(ObjektService.signature(before)).isEqualTo(ObjektService.signature(before));
    }

    @Test void catalogSignatureChangesWhenFrontImageChanges() {
        var before = java.util.List.of(java.util.Map.<String, Object>of(
                "id", "a", "collectionId", "A", "frontImage", "https://cdn.example/old.webp"));
        var after = java.util.List.of(java.util.Map.<String, Object>of(
                "id", "a", "collectionId", "A", "frontImage", "https://cdn.example/new.webp"));
        assertThat(ObjektService.signature(before)).isNotEqualTo(ObjektService.signature(after));
    }
}
