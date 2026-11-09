package com.example.triplesweb.service;

import com.example.triplesweb.dto.ShopDetailDto;
import com.example.triplesweb.entity.Event;
import com.example.triplesweb.entity.Shop;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ShopServiceMdPhotoTest {

    @Test
    void collectNoticeImages_dedupesAndKeepsOrder() {
        String mdList = """
                # Official MD
                !/uploads/a.jpg
                응원봉|45000
                !/uploads/a.jpg
                img:/uploads/b.png
                슬로건|15000
                """;

        List<String> photos = ShopService.collectNoticeImages(mdList);

        assertThat(photos).containsExactly("/uploads/a.jpg", "/uploads/b.png");
    }

    @Test
    void toDetail_groupsPhotosFromShopMdList() {
        Event event = new Event();
        event.setEventId(10L);
        event.setEventName("tripleS Come True");
        event.setEventInfo("info");

        Shop shop = new Shop();
        shop.setId(3L);
        shop.setTitle("tripleS Come True");
        shop.setEvent(event);
        shop.setSalesPeriod("2024.06.01~2024.09.30");
        shop.setMdList("""
                # Official MD
                !/uploads/seoul-md.jpg
                응원봉|45000
                # MD Benefit
                !/uploads/seoul-benefit.jpg
                """);

        ShopDetailDto detail = ShopService.toDetail(shop);

        assertThat(detail.getEventName()).isEqualTo("tripleS Come True");
        assertThat(detail.getSalesPeriod()).isEqualTo("2024.06.01~2024.09.30");
        assertThat(detail.isHasMdPhotos()).isTrue();
        assertThat(detail.getMdRegions()).hasSize(2);
        assertThat(detail.getMdRegions().get(0).getLabel()).isEqualTo("Official MD");
        assertThat(detail.getMdRegions().get(0).getCategories().get(0).getPhotos())
                .containsExactly("/uploads/seoul-md.jpg");
        assertThat(detail.getMdRegions().get(1).getCategories().get(0).getPhotos())
                .containsExactly("/uploads/seoul-benefit.jpg");
    }
}
