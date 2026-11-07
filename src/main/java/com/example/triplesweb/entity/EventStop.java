package com.example.triplesweb.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 투어·공연의 지역 회차. {@link Event} N:1.
 * 회차마다 셋리스트·MD 리스트를 가진다.
 */
@Entity
@Table(name = "event_stop")
@Getter
@Setter
@NoArgsConstructor
@ToString(exclude = {"event"})
public class EventStop {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long stopId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "event_id", nullable = false)
    private Event event;

    /** 개최일. 단일일 또는 기간(예: 2022.12.02~2022.12.04) */
    @Column(nullable = false, length = 80)
    private String eventDate;

    /** 지역·도시 (예: 부산, 일본). 비어 있으면 제목에 괄호 미표시 */
    @Column(length = 120)
    private String region;

    @Column(length = 255)
    private String venue;

    /** 아코디언 펼침 시 추가 안내 */
    @Column(columnDefinition = "LONGTEXT")
    private String detail;

    /**
     * 셋리스트. 줄바꿈으로 항목 구분.
     * <ul>
     *   <li>{@code # VCR 1 : OPENING VCR} — 섹션(표 가로줄)</li>
     *   <li>{@code 곡명} / {@code 곡명|앨범} / {@code 곡명|앨범|비고}</li>
     *   <li>{@code VCR}/{@code MENT}로 시작하는 줄도 섹션으로 인식</li>
     * </ul>
     */
    @Column(columnDefinition = "LONGTEXT")
    private String setlist;

    /**
     * MD 리스트. 줄바꿈으로 구분.
     * <ul>
     *   <li>{@code # Official MD} — 카테고리</li>
     *   <li>{@code 상품명} 또는 {@code 상품명|가격}</li>
     * </ul>
     */
    @Column(name = "md_list", columnDefinition = "LONGTEXT")
    private String mdList;

    @Column(nullable = false)
    private int sortOrder;

    public boolean isHasRegion() {
        return region != null && !region.isBlank();
    }

    public boolean isHasVenue() {
        return venue != null && !venue.isBlank();
    }

    public boolean isHasDetail() {
        return detail != null && !detail.isBlank();
    }

    public boolean isHasSetlist() {
        return !getSetlistRows().isEmpty();
    }

    public boolean isHasMdList() {
        return !getMdCategories().isEmpty();
    }

    /** 회차 아코디언 본문(상세·셋리스트). MD는 하단 굿즈 섹션으로 분리 */
    public boolean isHasPanelContent() {
        return isHasDetail() || isHasSetlist();
    }

    /** Mustache용: 공연명 (지역) */
    @Transient
    public String getDisplayTitle() {
        String name = event != null && event.getEventName() != null ? event.getEventName() : "";
        if (isHasRegion()) {
            return name + " (" + region.trim() + ")";
        }
        return name;
    }

    /** Mustache용: 굿즈 구역 제목 (예: tripleS Come True in 서울) */
    @Transient
    public String getGoodsLabel() {
        String name = event != null && event.getEventName() != null ? event.getEventName() : "";
        if (isHasRegion()) {
            return name + " in " + region.trim();
        }
        return name;
    }

    /** Mustache용: 셋리스트 표 행 (섹션 + 곡) */
    @Transient
    public List<SetlistRow> getSetlistRows() {
        return SetlistRow.parseAll(setlist);
    }

    /** Mustache용: MD 카테고리 목록 */
    @Transient
    public List<MdCategory> getMdCategories() {
        return MdCategory.parseAll(mdList);
    }

    /** 하위 호환: 모든 카테고리 상품을 평탄화 */
    @Transient
    public List<MdLine> getMdItems() {
        return getMdCategories().stream()
                .flatMap(c -> c.getItems().stream())
                .collect(Collectors.toList());
    }

    private static List<String> splitLines(String raw) {
        if (raw == null || raw.isBlank()) return List.of();
        return Arrays.stream(raw.split("\\R"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }

    /**
     * 셋리스트 한 행.
     * 섹션은 표 가로줄, 곡은 순서·곡명·비고 칸으로 표시.
     */
    @Getter
    public static final class SetlistRow {
        private static final Pattern SECTION_PREFIX = Pattern.compile(
                "^(?i)(VCR|MENT|ENCORE|INTRO|OUTRO|MC)\\b.*");

        private final boolean section;
        private final String sectionLabel;
        private final Integer orderNum;
        private final String title;
        private final String subtitle;
        private final String note;
        private final boolean hasSubtitle;
        private final boolean hasNote;
        private final boolean hasOrder;

        private SetlistRow(boolean section, String sectionLabel, Integer orderNum,
                           String title, String subtitle, String note) {
            this.section = section;
            this.sectionLabel = sectionLabel;
            this.orderNum = orderNum;
            this.title = title;
            this.subtitle = subtitle;
            this.note = note;
            this.hasSubtitle = subtitle != null && !subtitle.isBlank();
            this.hasNote = note != null && !note.isBlank();
            this.hasOrder = orderNum != null;
        }

        static List<SetlistRow> parseAll(String raw) {
            List<String> lines = splitLines(raw);
            if (lines.isEmpty()) return List.of();
            List<SetlistRow> rows = new ArrayList<>(lines.size());
            int songOrder = 0;
            for (String line : lines) {
                if (isSectionLine(line)) {
                    String label = line.startsWith("#") ? line.substring(1).trim() : line;
                    if (label.isEmpty()) continue;
                    rows.add(new SetlistRow(true, label, null, null, null, null));
                    continue;
                }
                songOrder++;
                rows.add(parseSong(line, songOrder));
            }
            return rows;
        }

        private static boolean isSectionLine(String line) {
            if (line.startsWith("#")) return true;
            return SECTION_PREFIX.matcher(line).matches();
        }

        private static SetlistRow parseSong(String line, int order) {
            String[] parts = line.split("\\|", -1);
            String title = parts[0].trim();
            String subtitle = parts.length > 1 ? parts[1].trim() : "";
            String note = parts.length > 2 ? parts[2].trim() : "";
            if (title.isEmpty()) title = line;
            return new SetlistRow(false, null, order,
                    title,
                    subtitle.isEmpty() ? null : subtitle,
                    note.isEmpty() ? null : note);
        }
    }

    /**
     * MD 카테고리.
     * {@code # Official MD} 아래 {@code !이미지URL}(판매 공지)과 {@code 상품|가격}.
     */
    @Getter
    public static final class MdCategory {
        private static final String DEFAULT_TITLE = "Official MD";
        private static final Pattern BARE_IMAGE_URL = Pattern.compile(
                "(?i)^https?://\\S+\\.(png|jpe?g|webp|gif)(\\?\\S*)?$");

        private final String title;
        /** DOM id용 키 (공백·특수문자 제거) */
        private final String key;
        private final List<String> noticeImages;
        private final List<MdLine> items;
        private final boolean hasNoticeImages;
        private final boolean hasItems;

        private MdCategory(String title, String key, List<String> noticeImages, List<MdLine> items) {
            this.title = title;
            this.key = key;
            this.noticeImages = List.copyOf(noticeImages);
            this.items = List.copyOf(items);
            this.hasNoticeImages = !this.noticeImages.isEmpty();
            this.hasItems = !this.items.isEmpty();
        }

        static List<MdCategory> parseAll(String raw) {
            List<String> lines = splitLines(raw);
            if (lines.isEmpty()) return List.of();

            List<MdCategory> categories = new ArrayList<>();
            String currentTitle = null;
            List<String> currentImages = new ArrayList<>();
            List<MdLine> currentItems = new ArrayList<>();

            for (String line : lines) {
                if (line.startsWith("#")) {
                    flushCategory(categories, currentTitle, currentImages, currentItems);
                    currentTitle = line.substring(1).trim();
                    currentImages = new ArrayList<>();
                    currentItems = new ArrayList<>();
                    if (currentTitle.isEmpty()) currentTitle = DEFAULT_TITLE;
                    continue;
                }
                if (currentTitle == null) currentTitle = DEFAULT_TITLE;
                String imageUrl = parseImageLine(line);
                if (imageUrl != null) {
                    currentImages.add(imageUrl);
                } else {
                    currentItems.add(MdLine.parse(line));
                }
            }
            flushCategory(categories, currentTitle, currentImages, currentItems);
            return categories;
        }

        /** {@code !/path.png}, {@code img:https://...}, 또는 이미지 확장자 URL */
        private static String parseImageLine(String line) {
            if (line.startsWith("!")) {
                String url = line.substring(1).trim();
                return url.isEmpty() ? null : url;
            }
            if (line.regionMatches(true, 0, "img:", 0, 4)) {
                String url = line.substring(4).trim();
                return url.isEmpty() ? null : url;
            }
            if (BARE_IMAGE_URL.matcher(line).matches()) {
                return line;
            }
            return null;
        }

        private static void flushCategory(List<MdCategory> out, String title,
                                          List<String> images, List<MdLine> items) {
            if (title == null) return;
            if (images.isEmpty() && items.isEmpty()) return;
            int index = out.size();
            String key = "c" + index + "-" + title.toLowerCase()
                    .replaceAll("[^a-z0-9가-힣]+", "-")
                    .replaceAll("(^-)|(-$)", "");
            if (key.isBlank()) key = "c" + index;
            out.add(new MdCategory(title, key, images, items));
        }
    }

    /** MD 한 줄. {@code 이름} 또는 {@code 이름|가격} */
    @Getter
    public static final class MdLine {
        private final String name;
        private final String price;
        private final boolean hasPrice;

        private MdLine(String name, String price) {
            this.name = name;
            this.price = price;
            this.hasPrice = price != null && !price.isBlank();
        }

        static MdLine parse(String line) {
            int sep = line.indexOf('|');
            if (sep < 0) {
                return new MdLine(line, null);
            }
            String name = line.substring(0, sep).trim();
            String price = line.substring(sep + 1).trim();
            return new MdLine(name.isEmpty() ? line : name, price.isEmpty() ? null : price);
        }
    }
}
