package com.example.triplesweb.dto;

import com.example.triplesweb.entity.Shop;
import lombok.Getter;

/** 굿즈리스트 목록 카드. */
@Getter
public class ShopListCard {
    private final long id;
    private final String eventName;
    private final String displayCoverImage;
    private final String salesPeriod;
    private final Long sectionId;
    private final Long eventId;

    public ShopListCard(Shop shop) {
        this.id = shop.getId() == null ? 0L : shop.getId();
        this.eventName = shop.getEventName();
        this.displayCoverImage = shop.getDisplayCoverImage();
        this.salesPeriod = shop.getSalesPeriod() == null ? "" : shop.getSalesPeriod();
        this.sectionId = shop.getSection() != null ? shop.getSection().getSectionId() : null;
        this.eventId = shop.getEvent() != null ? shop.getEvent().getEventId() : null;
    }
}
