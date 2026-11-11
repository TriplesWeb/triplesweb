package com.example.triplesweb.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShopSectionDto {
    private Long sectionId;
    private String name;
    private int sortOrder;
    /** 공연(이벤트) 목록 구분에서 가져온 항목이면 true. 샵에서 이름·삭제를 막는다. */
    private boolean fromEvent;
}
