package com.example.triplesweb.dto;


import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 샵 목록 카드 UI용 DTO. 대표 이미지 URL 등 요약 정보만 포함. */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class ShopCardDto {
    private Long id;
    private String category;
    private String name;
    private int price;
    private boolean soldout;
    private String mainImageUrl;
}
