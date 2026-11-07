package com.example.triplesweb.util;

import java.util.Comparator;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Shared catalog sort/kind matching for objekt pages and the article picker. */
public final class ObjektCatalogSort {
    private static final Pattern DIGITS = Pattern.compile("(\\d+)");

    private ObjektCatalogSort() {}

    public static String normalizeKind(String kind) {
        if (kind == null) return "";
        String trimmed = kind.trim();
        if (trimmed.isEmpty() || "all".equalsIgnoreCase(trimmed) || "전체".equals(trimmed)) return "";
        return trimmed.replaceAll("[^A-Za-z0-9]", "").toLowerCase(Locale.ROOT);
    }

    public static boolean matchesKind(String selectedKind, Map<String, Object> item) {
        String kindKey = normalizeKind(selectedKind);
        if (kindKey.isEmpty()) return true;
        return normalizeKind(text(item, "collectionNo", "collectionNumber")).equals(kindKey);
    }

    public static Comparator<Map<String, Object>> comparator(String sort) {
        Comparator<Map<String, Object>> byDate = Comparator.comparing(
                item -> text(item, "createdAt", "created_at", "date"),
                Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER));
        Comparator<Map<String, Object>> byNumber = Comparator.comparingDouble(ObjektCatalogSort::numberValue);
        Comparator<Map<String, Object>> byMember = Comparator.comparing(
                item -> text(item, "member", "memberName"),
                Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER));
        String key = sort == null ? "" : sort.trim().toLowerCase(Locale.ROOT);
        return switch (key) {
            case "oldest" -> byDate;
            case "number_low" -> byNumber;
            case "number_high" -> byNumber.reversed();
            case "mint_low" -> mintComparator(false);
            case "mint_high" -> mintComparator(true);
            case "first_member" -> Comparator.<Map<String, Object>>comparingInt(ObjektCatalogSort::memberOrder)
                    .thenComparing(byMember);
            case "last_member" -> Comparator.<Map<String, Object>>comparingInt(item -> {
                int order = memberOrder(item);
                return order == 24 ? 24 : 23 - order;
            }).thenComparing(byMember);
            default -> byDate.reversed();
        };
    }

    private static Comparator<Map<String, Object>> mintComparator(boolean descending) {
        Comparator<Long> counts = descending ? Comparator.reverseOrder() : Comparator.naturalOrder();
        return Comparator.<Map<String, Object>, Long>comparing(ObjektCatalogSort::mintOf, Comparator.nullsLast(counts))
                .thenComparing(item -> text(item, "slug", "id"));
    }

    private static int memberOrder(Map<String, Object> item) {
        return MemberPortraitOptions.objektMembers(text(item, "member", "memberName")).stream()
                .mapToInt(MemberPortraitOptions::memberIndex)
                .min()
                .orElse(24);
    }

    private static Long mintOf(Map<String, Object> item) {
        Object existing = item.get("mintCount");
        if (existing instanceof Number number) return number.longValue();
        String raw = text(item, "mintCount");
        if (raw.isEmpty()) return null;
        try {
            return Long.parseLong(raw);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static double numberValue(Map<String, Object> item) {
        String raw = text(item, "numberValue", "number", "collectionNo");
        try {
            return Double.parseDouble(raw);
        } catch (NumberFormatException ignored) {
            Matcher match = DIGITS.matcher(raw);
            if (match.find()) {
                try {
                    return Double.parseDouble(match.group(1));
                } catch (NumberFormatException ignoredAgain) {
                    return 0;
                }
            }
            return 0;
        }
    }

    private static String text(Map<String, Object> item, String... keys) {
        if (item == null) return "";
        for (String key : keys) {
            Object value = item.get(key);
            if (value != null && !value.toString().isBlank() && !"null".equalsIgnoreCase(value.toString())) {
                return value.toString();
            }
        }
        return "";
    }
}
