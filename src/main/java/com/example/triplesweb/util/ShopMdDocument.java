package com.example.triplesweb.util;

import com.example.triplesweb.entity.EventStop;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 샵 MD 공지 DSL.
 * <pre>
 * ## 방콕
 * @2024.06.15~2025.03.01
 * # Official MD
 * !/uploads/a.jpg
 * # MD Benefit
 * !/uploads/b.jpg
 * </pre>
 * {@code ##}가 없으면 예전 형식({@code # 구분}만)으로 보고, 각 구분을 지역 한 칸으로 올린다.
 */
public final class ShopMdDocument {

    private static final Pattern BARE_IMAGE_URL = Pattern.compile(
            "(?i)^https?://\\S+\\.(png|jpe?g|webp|gif)(\\?\\S*)?$");

    private ShopMdDocument() {
    }

    public record Category(String title, String key, List<String> photos) {
        public Category {
            title = title == null ? "" : title;
            key = key == null ? "" : key;
            photos = photos == null ? List.of() : List.copyOf(photos);
        }

        public boolean hasPhotos() {
            return !photos.isEmpty();
        }
    }

    public record Region(String name, String salesPeriod, List<Category> categories) {
        public Region {
            name = name == null ? "" : name;
            salesPeriod = salesPeriod == null || salesPeriod.isBlank() ? null : salesPeriod.trim();
            categories = categories == null ? List.of() : List.copyOf(categories);
        }

        public boolean hasSalesPeriod() {
            return salesPeriod != null && !salesPeriod.isBlank();
        }

        public boolean hasPhotos() {
            return categories.stream().anyMatch(Category::hasPhotos);
        }
    }

    public static List<Region> parse(String raw) {
        List<String> lines = splitLines(raw);
        if (lines.isEmpty()) return List.of();

        boolean hasRegionMarker = lines.stream().anyMatch(l -> l.startsWith("##"));
        if (!hasRegionMarker) {
            return legacyAsRegions(raw);
        }

        List<Region> regions = new ArrayList<>();
        String regionName = null;
        String salesPeriod = null;
        String categoryTitle = null;
        List<String> photos = new ArrayList<>();
        List<Category> categories = new ArrayList<>();

        for (String line : lines) {
            if (line.startsWith("##")) {
                flushCategory(categories, categoryTitle, photos);
                flushRegion(regions, regionName, salesPeriod, categories);
                regionName = line.substring(2).trim();
                if (regionName.isEmpty()) regionName = "지역";
                salesPeriod = null;
                categoryTitle = null;
                photos = new ArrayList<>();
                categories = new ArrayList<>();
                continue;
            }
            if (regionName == null) {
                regionName = "지역";
            }
            if (line.startsWith("@")) {
                String period = line.substring(1).trim();
                if (period.regionMatches(true, 0, "sales:", 0, 6)) {
                    period = period.substring(6).trim();
                }
                salesPeriod = period.isEmpty() ? null : period;
                continue;
            }
            if (line.startsWith("#")) {
                flushCategory(categories, categoryTitle, photos);
                categoryTitle = line.substring(1).trim();
                if (categoryTitle.isEmpty()) categoryTitle = "공지";
                photos = new ArrayList<>();
                continue;
            }
            String imageUrl = parseImageLine(line);
            if (imageUrl == null) continue;
            if (categoryTitle == null) categoryTitle = "공지";
            if (!photos.contains(imageUrl)) photos.add(imageUrl);
        }
        flushCategory(categories, categoryTitle, photos);
        flushRegion(regions, regionName, salesPeriod, categories);
        return regions;
    }

    /** 지역 판매 기간이 있으면 전체 요약 문자열을 만든다. */
    public static String summarizeSalesPeriod(List<Region> regions) {
        if (regions == null || regions.isEmpty()) return null;
        List<String> periods = regions.stream()
                .map(Region::salesPeriod)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
        if (periods.isEmpty()) return null;
        if (periods.size() == 1) return periods.get(0);
        return String.join(" / ", periods);
    }

    private static List<Region> legacyAsRegions(String raw) {
        List<Region> regions = new ArrayList<>();
        int index = 0;
        for (EventStop.MdCategory category : EventStop.MdCategory.parseAll(raw)) {
            List<String> photos = dedupe(category.getNoticeImages());
            if (photos.isEmpty()) {
                index++;
                continue;
            }
            String title = StringUtils.hasText(category.getTitle()) ? category.getTitle() : "공지";
            String key = StringUtils.hasText(category.getKey()) ? category.getKey() : ("c" + index);
            regions.add(new Region(title, null, List.of(new Category(title, key, photos))));
            index++;
        }
        return regions;
    }

    private static void flushCategory(List<Category> out, String title, List<String> photos) {
        if (title == null) return;
        List<String> cleaned = dedupe(photos);
        if (cleaned.isEmpty()) return;
        int index = out.size();
        String key = "c" + index + "-" + slug(title);
        if (key.endsWith("-")) key = "c" + index;
        out.add(new Category(title, key, cleaned));
    }

    private static void flushRegion(List<Region> out, String name, String salesPeriod, List<Category> categories) {
        if (name == null) return;
        List<Category> withPhotos = categories.stream().filter(Category::hasPhotos).toList();
        if (withPhotos.isEmpty() && !StringUtils.hasText(salesPeriod) && out.isEmpty() && name.equals("지역")) {
            return;
        }
        if (withPhotos.isEmpty() && !StringUtils.hasText(name)) return;
        // 사진 없는 빈 지역은 저장·표시에서 제외
        if (withPhotos.isEmpty()) return;
        out.add(new Region(name, salesPeriod, withPhotos));
    }

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

    private static List<String> dedupe(List<String> urls) {
        if (urls == null || urls.isEmpty()) return List.of();
        Set<String> seen = new LinkedHashSet<>();
        List<String> out = new ArrayList<>();
        for (String url : urls) {
            if (!StringUtils.hasText(url)) continue;
            String trimmed = url.trim();
            if (seen.add(trimmed)) out.add(trimmed);
        }
        return out;
    }

    private static List<String> splitLines(String raw) {
        if (raw == null || raw.isBlank()) return List.of();
        return java.util.Arrays.stream(raw.split("\\R"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    private static String slug(String title) {
        return title.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9가-힣]+", "-")
                .replaceAll("(^-)|(-$)", "");
    }
}
