package com.example.triplesweb.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/** 이벤트 목록 페이지용 섹션 묶음. */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EventSectionGroupDto {
    private Long sectionId;
    private String name;
    private boolean other;
    /** 탭/패널 DOM id (예: event-section-1, event-section-other) */
    private String tabKey;
    @Builder.Default
    private List<EventListCard> events = new ArrayList<>();
}
