package com.example.triplesweb.util;

import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.ToIntFunction;
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
        Comparator<Map<String, Object>> byNumber = Comparator.comparingDouble(ObjektCatalogSort::numberValue);
        String key = sort == null ? "" : sort.trim().toLowerCase(Locale.ROOT);
        return switch (key) {
            case "oldest" -> bySeasonOldest().thenComparing(byDateOldest()).thenComparing(byCollectionNo());
            case "number_low" -> byNumber;
            case "number_high" -> byNumber.reversed();
            case "mint_low" -> mintComparator(false);
            case "mint_high" -> mintComparator(true);
            case "first_member" -> byMemberOrder(false).thenComparing(byDateNewest()).thenComparing(byCollectionNo());
            case "last_member" -> byMemberOrder(true).thenComparing(byDateNewest()).thenComparing(byCollectionNo());
            default -> bySeasonNewest().thenComparing(byDateNewest()).thenComparing(byCollectionNo());
        };
    }

    private static Comparator<Map<String, Object>> byDateNewest() {
        return Comparator.comparing(ObjektCatalogSort::createdAtOrNull, Comparator.nullsLast(Comparator.reverseOrder()));
    }

    private static Comparator<Map<String, Object>> byDateOldest() {
        return Comparator.comparing(ObjektCatalogSort::createdAtOrNull, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER));
    }

    public static Comparator<Map<String, Object>> collectionComparator(String sort) {
        Comparator<Map<String, Object>> byReceived = Comparator.comparingInt(card -> {
            Object value = card.get("receivedIndex");
            return value instanceof Number number ? number.intValue() : 0;
        });
        Comparator<Map<String, Object>> byNumber = Comparator.comparingDouble(ObjektCatalogSort::numberValue);
        Comparator<Map<String, Object>> bySerial = Comparator.comparingLong(card -> {
            try { return Long.parseLong(text(card, "serial")); }
            catch (NumberFormatException ex) { return Long.MAX_VALUE; }
        });
        String key = sort == null ? "" : sort.trim().toLowerCase(Locale.ROOT);
        return switch (key) {
            case "oldest" -> bySeasonOldest().thenComparing(byReceived.reversed());
            case "number_low" -> byNumber;
            case "number_high" -> byNumber.reversed();
            case "first_member" -> byMemberOrder(false).thenComparing(byReceived);
            case "last_member" -> byMemberOrder(true).thenComparing(byReceived);
            case "mint_low" -> bySerial;
            case "mint_high" -> bySerial.reversed();
            default -> bySeasonNewest().thenComparing(byReceived);
        };
    }

    static long seasonRank(Map<String, Object> item) {
        long fromSeason = ObjektFilterOrder.seasonIndex(text(item, "season", "seasonKey"));
        if (fromSeason >= 0) return fromSeason;
        return ObjektFilterOrder.seasonIndex(text(item, "collectionId"));
    }

    private static Comparator<Map<String, Object>> bySeasonNewest() {
        return Comparator.comparingLong((Map<String, Object> item) -> {
            long index = seasonRank(item);
            return index < 0 ? Long.MIN_VALUE : index;
        }).reversed();
    }

    private static Comparator<Map<String, Object>> bySeasonOldest() {
        return Comparator.comparingLong((Map<String, Object> item) -> {
            long index = seasonRank(item);
            return index < 0 ? Long.MAX_VALUE : index;
        });
    }

    private static Comparator<Map<String, Object>> byCollectionNo() {
        return Comparator.comparing(item -> text(item, "collectionNo", "collectionNumber"), String.CASE_INSENSITIVE_ORDER);
    }

    private static String createdAtOrNull(Map<String, Object> item) {
        String date = text(item, "createdAt", "created_at", "date");
        return date.isEmpty() ? null : date;
    }

    private static Comparator<Map<String, Object>> mintComparator(boolean descending) {
        Comparator<Long> counts = descending ? Comparator.reverseOrder() : Comparator.naturalOrder();
        return Comparator.<Map<String, Object>, Long>comparing(ObjektCatalogSort::mintOf, Comparator.nullsLast(counts))
                .thenComparing(item -> text(item, "slug", "id"));
    }

    private static Comparator<Map<String, Object>> byMemberOrder(boolean lastFirst) {
        Map<Map<String, Object>, Integer> cache = new IdentityHashMap<>();
        ToIntFunction<Map<String, Object>> orderOf = item ->
                cache.computeIfAbsent(item, ObjektCatalogSort::memberOrder);
        if (!lastFirst) return Comparator.comparingInt(orderOf);
        return Comparator.comparingInt(item -> {
            int order = orderOf.applyAsInt(item);
            return order == 24 ? 24 : 23 - order;
        });
    }

    private static int memberOrder(Map<String, Object> item) {
        return MemberPortraitOptions.earliestMemberIndex(text(item, "member", "memberName"));
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
