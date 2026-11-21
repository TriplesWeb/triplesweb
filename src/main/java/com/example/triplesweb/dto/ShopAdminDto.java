package com.example.triplesweb.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

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
        /** 회차 지역별 MD 시드(이름·개최일). 샵 편집에서 자동 채움용. */
        @Builder.Default
        private List<EventRegionOption> regions = new ArrayList<>();
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EventRegionOption {
        private String name;
        /** 표시형 날짜(2026.10.10). 종료와 같으면 salesEnd는 비운다. */
        private String salesStart;
        private String salesEnd;
    }
}
