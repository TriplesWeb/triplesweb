package com.example.triplesweb.dto;

import com.example.triplesweb.entity.Event;
import com.example.triplesweb.entity.EventStop;
import com.example.triplesweb.util.ImageUrls;
import lombok.Getter;
import org.springframework.util.StringUtils;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/** 이벤트 목록 카드용. LONGTEXT eventInfo를 실어 오지 않는다. */
@Getter
public class EventListCard {
    private final long eventId;
    private final String eventName;
    private final String displayEventImage;
    /** 회차에서 뽑은 기간 표시 (예: 2024-06-15 또는 2022.12.02~2023.02.18) */
    private final String eventDate;
    /** 지역 요약 (예: 부산 · 대구 · 광주) */
    private final String regionLabel;
    private final Long sectionId;

    public EventListCard(Event event) {
        this.eventId = event.getEventId() == null ? 0L : event.getEventId();
        this.eventName = event.getEventName() == null ? "" : event.getEventName();
        this.displayEventImage = ImageUrls.orPlaceholder(event.getEventImage());
        List<EventStop> ordered = event.getOrderedStops();
        this.eventDate = periodLabel(ordered);
        this.regionLabel = regionLabel(ordered);
        this.sectionId = event.getSection() != null ? event.getSection().getSectionId() : null;
    }

    private static String periodLabel(List<EventStop> stops) {
        if (stops == null || stops.isEmpty()) return "";
        List<String> dates = stops.stream()
                .map(EventStop::getEventDate)
                .filter(StringUtils::hasText)
                .map(String::trim)
                .toList();
        if (dates.isEmpty()) return "";
        String first = dates.get(0);
        String last = dates.get(dates.size() - 1);
        if (dates.size() == 1 || Objects.equals(first, last)) return first;
        // 기간 표기가 이미 들어 있으면 첫·끝 회차 날짜만 이어 붙인다.
        String start = first.contains("~") ? first.substring(0, first.indexOf('~')).trim() : first;
        String end = last.contains("~") ? last.substring(last.lastIndexOf('~') + 1).trim() : last;
        return start + "~" + end;
    }

    private static String regionLabel(List<EventStop> stops) {
        if (stops == null || stops.isEmpty()) return null;
        String joined = stops.stream()
                .map(EventStop::getRegion)
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .collect(Collectors.joining(" · "));
        return joined.isBlank() ? null : joined;
    }

    /** 목록 정렬용: 가장 늦은 회차 날짜 키 */
    public static String sortKey(Event event) {
        return event.getOrderedStops().stream()
                .map(EventStop::getEventDate)
                .filter(StringUtils::hasText)
                .map(d -> d.contains("~") ? d.substring(d.lastIndexOf('~') + 1).trim() : d.trim())
                .max(Comparator.naturalOrder())
                .orElse("");
    }
}
