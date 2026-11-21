package com.example.triplesweb.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

    /** Mustache용: 회차가 2개 이상이면 탭을 쓴다 */
    public boolean isHasMultipleStops() {
        return getOrderedStops().size() > 1;
    }

    /** Mustache용: 지역 그룹이 2개 이상이면 지역 탭을 쓴다 */
    public boolean isHasMultipleRegions() {
        return getRegionGroups().size() > 1;
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

    /** Mustache용: 지역별 회차 그룹 (등장 순서 유지) */
    @Transient
    public List<RegionGroup> getRegionGroups() {
        List<EventStop> ordered = getOrderedStops();
        if (ordered.isEmpty()) return List.of();
        Map<String, List<EventStop>> grouped = new LinkedHashMap<>();
        for (EventStop stop : ordered) {
            grouped.computeIfAbsent(stop.getRegionKey(), key -> new ArrayList<>()).add(stop);
        }
        List<RegionGroup> groups = new ArrayList<>(grouped.size());
        int index = 0;
        for (Map.Entry<String, List<EventStop>> entry : grouped.entrySet()) {
            String key = entry.getKey();
            groups.add(new RegionGroup(
                    key,
                    "r" + index + "-" + slugRegionKey(key),
                    entry.getValue().get(0).getRegionLabel(),
                    List.copyOf(entry.getValue()),
                    index == 0,
                    index > 0 && grouped.size() > 1));
            index++;
        }
        return groups;
    }

    static String slugRegionKey(String key) {
        if (key == null || key.isBlank()) return "etc";
        StringBuilder slug = new StringBuilder();
        for (int i = 0; i < key.length(); i++) {
            char ch = key.charAt(i);
            if (Character.isLetterOrDigit(ch)) {
                slug.append(Character.toLowerCase(ch));
            } else if (slug.length() > 0 && slug.charAt(slug.length() - 1) != '-') {
                slug.append('-');
            }
        }
        while (slug.length() > 0 && slug.charAt(slug.length() - 1) == '-') {
            slug.setLength(slug.length() - 1);
        }
        return slug.length() == 0 ? "etc" : slug.toString();
    }

    /** Mustache용: 투어 전체 기간 요약 (첫·끝 회차 날짜) */
    @Transient
    public String getTourPeriodSummary() {
        List<EventStop> ordered = getOrderedStops();
        if (ordered.isEmpty()) return "";
        String first = ordered.get(0).getEventDate();
        String last = ordered.get(ordered.size() - 1).getEventDate();
        if (first == null || first.isBlank()) return last == null ? "" : last.trim();
        if (last == null || last.isBlank() || first.trim().equals(last.trim())) return first.trim();
        String start = first.trim().contains("~") ? first.trim().split("~", 2)[0].trim() : first.trim();
        String end = last.trim().contains("~")
                ? last.trim().substring(last.trim().lastIndexOf('~') + 1).trim()
                : last.trim();
        if (start.equals(end)) return start;
        return start + " – " + end;
    }

    public boolean isHasTourPeriodSummary() {
        return !getTourPeriodSummary().isBlank();
    }

    /** Mustache용: 지역 나열 (서울 · 부산) */
    @Transient
    public String getTourLocationsSummary() {
        return getRegionGroups().stream()
                .map(RegionGroup::getRegionLabel)
                .collect(Collectors.joining(" · "));
    }

    public boolean isHasTourLocationsSummary() {
        return !getTourLocationsSummary().isBlank();
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

    /** 상세 페이지 지역 탭 한 묶음 */
    @Getter
    public static final class RegionGroup {
        private final String regionKey;
        private final String regionSlug;
        private final String regionLabel;
        private final List<EventStop> stops;
        private final boolean firstRegion;
        private final boolean regionHidden;

        private RegionGroup(String regionKey, String regionSlug, String regionLabel, List<EventStop> stops,
                            boolean firstRegion, boolean regionHidden) {
            this.regionKey = regionKey;
            this.regionSlug = regionSlug;
            this.regionLabel = regionLabel;
            this.stops = stops;
            this.firstRegion = firstRegion;
            this.regionHidden = regionHidden;
        }

        public boolean isHasMultipleDates() {
            return stops != null && stops.size() > 1;
        }

        public String getRegionPanelId() {
            return "event-region-" + regionSlug;
        }
    }
}
