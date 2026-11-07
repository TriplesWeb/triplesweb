package com.example.triplesweb.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 공연·행사 이벤트(투어 단위). {@link EventSection} N:1, {@link EventStop} 1:N.
 */
@Entity
@Table(name = "event")
@Getter
@Setter
@ToString(exclude = {"section", "stops"})
@NoArgsConstructor
public class Event {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long eventId;

    @Column(unique = true, nullable = false)
    private String eventName;

    private String eventImage;

    @Transient
    public String getDisplayEventImage() {
        return com.example.triplesweb.util.ImageUrls.orPlaceholder(eventImage);
    }

    @Column(nullable = false, columnDefinition = "LONGTEXT")
    private String eventInfo;

    /** 목록 구분(섹션). null이면 「기타」 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "section_id")
    private EventSection section;

    @OneToMany(mappedBy = "event", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<EventStop> stops = new ArrayList<>();

    /** Mustache용: 소개 문구가 있을 때 */
    public boolean isHasEventInfo() {
        return eventInfo != null && !eventInfo.isBlank();
    }

    /** Mustache용: 지역 회차가 있을 때 */
    public boolean isHasStops() {
        return stops != null && !stops.isEmpty();
    }

    /** Mustache용: sortOrder·날짜 순 정렬된 회차 */
    public List<EventStop> getOrderedStops() {
        if (stops == null || stops.isEmpty()) return List.of();
        return stops.stream()
                .sorted(Comparator
                        .comparingInt(EventStop::getSortOrder)
                        .thenComparing(s -> s.getEventDate() != null ? s.getEventDate() : "")
                        .thenComparing(s -> s.getStopId() != null ? s.getStopId() : 0L))
                .collect(Collectors.toList());
    }

    /** Mustache용: MD가 있는 회차가 있을 때 */
    public boolean isHasMdGoods() {
        return !getMdGoodsStops().isEmpty();
    }

    /** Mustache용: MD가 등록된 회차만 (굿즈 섹션) */
    public List<EventStop> getMdGoodsStops() {
        return getOrderedStops().stream()
                .filter(EventStop::isHasMdList)
                .collect(Collectors.toList());
    }
}
