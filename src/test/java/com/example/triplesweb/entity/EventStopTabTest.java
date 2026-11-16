package com.example.triplesweb.entity;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class EventStopTabTest {

    @Test
    void shortTabDateStripsYearAndNormalizesRange() {
        assertThat(EventStop.shortTabDate("2026.10.10~2026.10.11")).isEqualTo("10.10–10.11");
        assertThat(EventStop.shortTabDate("2026.10.10")).isEqualTo("10.10");
        assertThat(EventStop.shortTabDate("2026-10-10")).isEqualTo("10.10");
        assertThat(EventStop.shortTabDate(null)).isEmpty();
    }

    @Test
    void tabLabelPrefersRegionAndShortDate() {
        Event event = new Event();
        event.setEventName("ASSEMBLE");
        EventStop stop = new EventStop();
        stop.setEvent(event);
        stop.setRegion("서울");
        stop.setEventDate("2026.10.10~2026.10.11");

        assertThat(stop.getTabLabel()).isEqualTo("서울 · 10.10–10.11");
        assertThat(stop.getDateTabLabel()).isEqualTo("10.10–10.11");
        assertThat(stop.getRegionKey()).isEqualTo("서울");
        assertThat(stop.getLocationSummary()).isEqualTo("서울");
    }

    @Test
    void hasMultipleStopsAndPanelHiddenFollowOrderedList() {
        Event event = new Event();
        event.setEventName("tour");

        EventStop seoul = new EventStop();
        seoul.setStopId(1L);
        seoul.setEvent(event);
        seoul.setRegion("서울");
        seoul.setEventDate("2026.10.10");
        seoul.setSortOrder(0);

        EventStop busan = new EventStop();
        busan.setStopId(2L);
        busan.setEvent(event);
        busan.setRegion("부산");
        busan.setEventDate("2026.10.18");
        busan.setSortOrder(1);

        event.setStops(new ArrayList<>(List.of(seoul, busan)));

        assertThat(event.isHasMultipleStops()).isTrue();
        assertThat(event.isHasMultipleRegions()).isTrue();
        assertThat(seoul.isFirstOrderedStop()).isTrue();
        assertThat(busan.isFirstOrderedStop()).isFalse();
        assertThat(seoul.isStopPanelHidden()).isFalse();
        assertThat(busan.isStopPanelHidden()).isTrue();
        assertThat(busan.getTabLabel()).isEqualTo("부산 · 10.18");
        assertThat(event.getTourPeriodSummary()).isEqualTo("2026.10.10 – 2026.10.18");
        assertThat(event.getTourLocationsSummary()).isEqualTo("서울 · 부산");
    }

    @Test
    void regionGroupsNestDatesUnderSameRegion() {
        Event event = new Event();
        event.setEventName("tour");

        EventStop day1 = stop(event, 1L, "서울", "2024.11.15", 0);
        EventStop day2 = stop(event, 2L, "서울", "2024.11.16", 1);
        EventStop busan = stop(event, 3L, "부산", "2024.11.20", 2);
        event.setStops(new ArrayList<>(List.of(day1, day2, busan)));

        List<Event.RegionGroup> groups = event.getRegionGroups();
        assertThat(groups).hasSize(2);
        assertThat(groups.get(0).getRegionLabel()).isEqualTo("서울");
        assertThat(groups.get(0).isFirstRegion()).isTrue();
        assertThat(groups.get(0).isRegionHidden()).isFalse();
        assertThat(groups.get(0).isHasMultipleDates()).isTrue();
        assertThat(groups.get(0).getStops()).containsExactly(day1, day2);
        assertThat(groups.get(1).getRegionLabel()).isEqualTo("부산");
        assertThat(groups.get(1).isRegionHidden()).isTrue();
        assertThat(groups.get(1).isHasMultipleDates()).isFalse();

        assertThat(day1.isFirstDateInRegion()).isTrue();
        assertThat(day2.isFirstDateInRegion()).isFalse();
        assertThat(day1.isHasMultipleDatesInRegion()).isTrue();
        assertThat(busan.isHasMultipleDatesInRegion()).isFalse();
        assertThat(day1.getDateTabLabel()).isEqualTo("11.15");
    }

    @Test
    void blankRegionGroupsAsOther() {
        Event event = new Event();
        event.setEventName("tour");
        EventStop only = stop(event, 1L, "  ", "2026.10.10", 0);
        event.setStops(new ArrayList<>(List.of(only)));

        assertThat(only.getRegionKey()).isEqualTo("기타");
        assertThat(event.getRegionGroups()).hasSize(1);
        assertThat(event.getRegionGroups().get(0).getRegionLabel()).isEqualTo("기타");
        assertThat(event.isHasMultipleRegions()).isFalse();
    }

    @Test
    void singleStopHidesTabsAndShowsPanel() {
        Event event = new Event();
        event.setEventName("tour");

        EventStop only = stop(event, 1L, "서울", "2026.10.10", 0);
        event.setStops(new ArrayList<>(List.of(only)));

        assertThat(event.isHasMultipleStops()).isFalse();
        assertThat(event.isHasMultipleRegions()).isFalse();
        assertThat(only.isHasMultipleStops()).isFalse();
        assertThat(only.isFirstOrderedStop()).isTrue();
        assertThat(only.isStopPanelHidden()).isFalse();
    }

    @Test
    void setlistSongCountSkipsSections() {
        EventStop stop = new EventStop();
        stop.setSetlist("# OPENING\n곡A\n곡B|앨범\nMENT\n곡C");
        assertThat(stop.getSetlistSongCount()).isEqualTo(3);
        assertThat(stop.isHasSetlistSongCount()).isTrue();
    }

    private static EventStop stop(Event event, long id, String region, String date, int order) {
        EventStop stop = new EventStop();
        stop.setStopId(id);
        stop.setEvent(event);
        stop.setRegion(region);
        stop.setEventDate(date);
        stop.setSortOrder(order);
        return stop;
    }
}
