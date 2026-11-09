package com.example.triplesweb.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/** 굿즈리스트 목록 페이지용 섹션 묶음. */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShopSectionGroupDto {
    private Long sectionId;
    private String name;
    private boolean other;
    /** 탭/패널 DOM id (예: shop-section-1, shop-section-other) */
    private String tabKey;
    @Builder.Default
    private List<ShopListCard> shops = new ArrayList<>();
}
