package com.example.triplesweb.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/** 샵 상세: 이벤트명·판매기간·지역별 MD 공지 사진. */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShopDetailDto {
    private Long id;
    private Long eventId;
    private String eventName;
    private String salesPeriod;
    private String displayCoverImage;
    private boolean hasSalesPeriod;
    private boolean hasMdPhotos;
    @Builder.Default
    private List<MdRegion> mdRegions = new ArrayList<>();

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MdRegion {
        /** DOM id용. 미저장이면 목록 인덱스를 쓴다. */
        private String regionKey;
        private String label;
        private String salesPeriod;
        private boolean hasSalesPeriod;
        private String eventDate;
        private String venue;
        private boolean hasVenue;
        private boolean hasPhotos;
        @Builder.Default
        private List<MdCategory> categories = new ArrayList<>();
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MdCategory {
        private String title;
        private String key;
        private boolean hasPhotos;
        @Builder.Default
        private List<String> photos = new ArrayList<>();
    }
}
