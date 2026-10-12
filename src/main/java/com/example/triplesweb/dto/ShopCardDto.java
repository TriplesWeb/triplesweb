package com.example.triplesweb.dto;


import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

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
