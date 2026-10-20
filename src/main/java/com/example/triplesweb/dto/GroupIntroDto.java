package com.example.triplesweb.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class GroupIntroDto {
    private String title;
    private String body;
    private String imageUrl;
    private String tabImageUrl;
}
