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
        assertThat(seoul.isFirstOrderedStop()).isTrue();
        assertThat(busan.isFirstOrderedStop()).isFalse();
        assertThat(seoul.isStopPanelHidden()).isFalse();
        assertThat(busan.isStopPanelHidden()).isTrue();
        assertThat(busan.getTabLabel()).isEqualTo("부산 · 10.18");
    }

    @Test
    void singleStopHidesTabsAndShowsPanel() {
        Event event = new Event();
        event.setEventName("tour");

        EventStop only = new EventStop();
        only.setStopId(1L);
        only.setEvent(event);
        only.setRegion("서울");
        only.setEventDate("2026.10.10");
        only.setSortOrder(0);
        event.setStops(new ArrayList<>(List.of(only)));

        assertThat(event.isHasMultipleStops()).isFalse();
        assertThat(only.isHasMultipleStops()).isFalse();
        assertThat(only.isFirstOrderedStop()).isTrue();
        assertThat(only.isStopPanelHidden()).isFalse();
    }
}
