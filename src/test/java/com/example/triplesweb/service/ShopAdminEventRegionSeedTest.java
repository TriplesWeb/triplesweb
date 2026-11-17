package com.example.triplesweb.service;

import com.example.triplesweb.dto.ShopAdminDto;
import com.example.triplesweb.entity.Event;
import com.example.triplesweb.entity.EventStop;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShopAdminEventRegionSeedTest {

    @Test
    void buildRegionOptions_groupsStopsByRegionAndSpansDates() {
        Event event = new Event();
        event.setEventName("Come True");
        event.getStops().add(stop(event, 0, "서울", "2026.10.10~2026.10.11"));
        event.getStops().add(stop(event, 1, "서울", "2026.10.12"));
        event.getStops().add(stop(event, 2, "타이베이", "2026.10.24"));
        event.getStops().add(stop(event, 3, "마카오", "2026.11.07~2026.11.08"));

        List<ShopAdminDto.EventRegionOption> regions = ShopAdminService.buildRegionOptions(event);

        assertEquals(3, regions.size());
        assertEquals("서울", regions.get(0).getName());
        assertEquals("2026.10.10", regions.get(0).getSalesStart());
        assertEquals("2026.10.12", regions.get(0).getSalesEnd());
        assertEquals("타이베이", regions.get(1).getName());
        assertEquals("2026.10.24", regions.get(1).getSalesStart());
        assertEquals("", regions.get(1).getSalesEnd());
        assertEquals("마카오", regions.get(2).getName());
        assertEquals("2026.11.07", regions.get(2).getSalesStart());
        assertEquals("2026.11.08", regions.get(2).getSalesEnd());
    }

    @Test
    void splitEventDateBounds_supportsTildeRange() {
        assertEquals("2026.10.10", ShopAdminService.splitEventDateBounds("2026.10.10~2026.10.11")[0]);
        assertEquals("2026.10.11", ShopAdminService.splitEventDateBounds("2026.10.10~2026.10.11")[1]);
        assertEquals("2026.10.24", ShopAdminService.splitEventDateBounds("2026.10.24")[0]);
        assertTrue(ShopAdminService.splitEventDateBounds("2026.10.24")[1].isBlank());
    }

    private static EventStop stop(Event event, int order, String region, String date) {
        EventStop stop = new EventStop();
        stop.setEvent(event);
        stop.setSortOrder(order);
        stop.setRegion(region);
        stop.setEventDate(date);
        return stop;
    }
}
