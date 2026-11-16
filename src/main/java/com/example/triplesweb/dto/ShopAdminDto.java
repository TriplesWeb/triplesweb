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
public class ShopAdminDto {
    private Long id;
    private String title;
    private Long eventId;
    private String eventName;
    private String salesPeriod;
    private String coverImage;
    private String mdList;
    private Long sectionId;
    private String sectionName;

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EventOption {
        private Long eventId;
        private String eventName;
        /** 공연 포스터. 샵 커버가 비면 이 이미지를 쓴다. */
        private String eventImage;
    }
}
