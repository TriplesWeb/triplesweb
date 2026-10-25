package com.example.triplesweb.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class GravityDisplayTest {
    @ParameterizedTest
    @CsvSource({
            "2026-07-28T01:00:00.000Z, 2026-07-28T13:00:00.000Z, 2026-07-28 10:00 ~ 22:00",
            "2026-07-28T18:00:00Z, 2026-07-28T19:00:00Z, 2026-07-29 03:00 ~ 04:00",
            "2026-07-28T14:00:00Z, 2026-07-28T16:00:00Z, 2026-07-28 23:00 ~ 2026-07-29 01:00",
            "2026-07-28T01:00:00+09:00, 2026-07-28T13:00:00+09:00, 2026-07-28 01:00 ~ 13:00",
            "2026-07-28T01:00:00, 2026-07-28T13:00:00, 2026-07-28 01:00 ~ 13:00",
            "2026-07-28, 2026-07-29, 2026-07-28 ~ 2026-07-29",
            "2026-07-28, 2026-07-28, 2026-07-28"
    })
    void formatsKoreanTimeWithoutInventingDateOnlyTimes(String start, String end, String expected) {
        assertThat(GravityDisplay.range(start, end)).isEqualTo(expected);
    }

    @Test void missingOrInvalidDatesDoNotLeakRawApiValues() {
        assertThat(GravityDisplay.range(null, null)).isEqualTo("일정 미정");
        assertThat(GravityDisplay.range("bad-date", "invalid")).isEqualTo("일정 미정");
        assertThat(GravityDisplay.range("2026-07-28T01:00:00Z", null)).isEqualTo("2026-07-28 10:00 ~ 종료 미정");
        assertThat(GravityDisplay.range(null, "2026-07-28T01:00:00Z")).isEqualTo("시작 미정 ~ 2026-07-28 10:00");
    }

    @Test void addsDisplayValuesWithoutChangingSourceDatesOrIds() {
        var source = Map.<String, Object>of("id", 21L, "statusLabel", "진행 중", "entireStartDate", "2026-07-28T01:00:00Z");
        var view = GravityDisplay.gravity(source);
        assertThat(view.get("id")).isEqualTo(21L);
        assertThat(view.get("statusClass")).isEqualTo("ongoing");
        assertThat(source).doesNotContainKey("periodLabel");
        assertThat(source.get("entireStartDate")).isEqualTo("2026-07-28T01:00:00Z");
        assertThat(GravityDisplay.gravity(Map.of("statusLabel", "예정")).get("statusClass")).isEqualTo("upcoming");
        assertThat(GravityDisplay.gravity(Map.of("statusLabel", "종료")).get("statusClass")).isEqualTo("ended");
    }

    @Test void formatsPollRevealInKorea() {
        assertThat(GravityDisplay.poll(Map.of("revealDate", "2026-07-28T18:00:00.000Z")).get("revealLabel"))
                .isEqualTo("2026-07-29 03:00");
    }
}
