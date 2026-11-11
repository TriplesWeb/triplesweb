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
public class EventAdminDto {
    private Long eventId;
    private String eventName;
    private String eventImage;
    private String eventInfo;
    private Long sectionId;
    private String sectionName;
    @Builder.Default
    private List<StopItem> stops = new ArrayList<>();

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StopItem {
        private Integer sortOrder;
        private String eventDate;
        private String region;
        private String venue;
        private String detail;
        /** 줄바꿈 셋리스트. `# 섹션` 또는 `곡|앨범|비고` */
        private String setlist;
        /** 샵 MD 공지. `# 카테고리`, `!공지이미지` (상품명|가격은 하위 호환) */
        private String mdList;
    }
}
