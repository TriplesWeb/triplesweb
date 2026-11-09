package com.example.triplesweb.service;

import com.example.triplesweb.dto.ShopDetailDto;
import com.example.triplesweb.entity.Event;
import com.example.triplesweb.entity.EventStop;
import com.example.triplesweb.entity.Shop;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ShopServiceMdPhotoTest {

    @Test
    void collectNoticeImages_dedupesAndKeepsOrder() {
        EventStop stop = new EventStop();
        stop.setMdList("""
                # Official MD
                !/uploads/a.jpg
                응원봉|45000
                !/uploads/a.jpg
                img:/uploads/b.png
                슬로건|15000
                """);

        List<String> photos = ShopService.collectNoticeImages(stop);

        assertThat(photos).containsExactly("/uploads/a.jpg", "/uploads/b.png");
    }

    @Test
    void toDetail_groupsPhotoCategoriesByRegion() {
        Event event = new Event();
        event.setEventId(10L);
        event.setEventName("tripleS Come True");
        event.setEventInfo("info");

        EventStop seoul = new EventStop();
        seoul.setStopId(1L);
        seoul.setEvent(event);
        seoul.setEventDate("2024.06.15");
        seoul.setRegion("서울");
        seoul.setVenue("올림픽홀");
        seoul.setSortOrder(0);
        seoul.setMdList("""
                # Official MD
                !/uploads/seoul-md.jpg
                응원봉|45000
                # MD Benefit
                !/uploads/seoul-benefit.jpg
                특전 포토카드
                """);

        EventStop bangkok = new EventStop();
        bangkok.setStopId(2L);
        bangkok.setEvent(event);
        bangkok.setEventDate("2024.09.01");
        bangkok.setRegion("방콕");
        bangkok.setSortOrder(1);
        bangkok.setMdList("""
                # Official MD
                !/uploads/bkk-md.jpg
                """);

        event.getStops().add(seoul);
        event.getStops().add(bangkok);

        Shop shop = new Shop();
        shop.setId(3L);
        shop.setEvent(event);
        shop.setSalesPeriod("2024.06.01~2024.09.30");

        ShopDetailDto detail = ShopService.toDetail(shop);

        assertThat(detail.getEventName()).isEqualTo("tripleS Come True");
        assertThat(detail.getSalesPeriod()).isEqualTo("2024.06.01~2024.09.30");
        assertThat(detail.isHasMdPhotos()).isTrue();
        assertThat(detail.getMdRegions()).hasSize(2);

        ShopDetailDto.MdRegion seoulRegion = detail.getMdRegions().get(0);
        assertThat(seoulRegion.getLabel()).contains("서울");
        assertThat(seoulRegion.getRegionKey()).isEqualTo("1");
        assertThat(seoulRegion.getCategories()).hasSize(2);
        assertThat(seoulRegion.getCategories().get(0).getTitle()).isEqualTo("Official MD");
        assertThat(seoulRegion.getCategories().get(0).getPhotos()).containsExactly("/uploads/seoul-md.jpg");
        assertThat(seoulRegion.getCategories().get(1).getPhotos()).containsExactly("/uploads/seoul-benefit.jpg");

        assertThat(detail.getMdRegions().get(1).getCategories().get(0).getPhotos())
                .containsExactly("/uploads/bkk-md.jpg");
    }
}
