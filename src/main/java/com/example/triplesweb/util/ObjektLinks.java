package com.example.triplesweb.util;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;

/** Public URLs use readable card names; upstream slugs and persistent collection IDs stay unchanged. */
public final class ObjektLinks {
    private static final String UUID_PATTERN = "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}";

    private ObjektLinks() {}
    public static String id(Map<String, Object> item) {
        for (String field : new String[]{"id", "collectionId", "slug"}) {
            String value = String.valueOf(item.getOrDefault(field, ""));
            if (!value.isBlank() && !"null".equalsIgnoreCase(value)) return value;
        }
        return "";
    }
    public static String key(Map<String, Object> item) {
        String seasonRaw = first(item, "season", "seasonKey");
        String season = token(seasonRaw);
        String number = token(first(item, "collectionNo", "collectionNumber"));
        String slug = String.valueOf(item.getOrDefault("slug", "")).toLowerCase(Locale.ROOT);
        String member = token(first(item, "member", "memberName"));
        if (member.isEmpty() && !season.isEmpty() && !number.isEmpty()
                && slug.startsWith(season + "-") && slug.endsWith("-" + number)
                && slug.length() > season.length() + number.length() + 2) {
            member = token(slug.substring(season.length() + 1, slug.length() - number.length() - 1));
        }
        if (!member.isEmpty() && !season.isEmpty() && !number.isEmpty()) {
            String seasonCode = ObjektSeasonCodes.code(seasonRaw);
            if (seasonCode.isEmpty()) seasonCode = season;
            return member + seasonCode + number;
        }
        if (isReadableSlug(slug)) return slug;
        String collectionId = token(first(item, "collectionId"));
        if (!collectionId.isEmpty()) return collectionId;
        String fallback = id(item).toLowerCase(Locale.ROOT);
        return isReadableSlug(fallback) ? fallback : id(item);
    }
    public static String encode(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20"); }
    public static String detail(Map<String, Object> item) { return "/cosmo/objekt/detail/" + encode(key(item)); }
    public static String member(Map<String, Object> item) { return "/cosmo/objekt?group=members&member=" + encode(String.valueOf(item.getOrDefault("member", ""))); }
    public static String compose(Map<String, Object> item, String category) { return "/article/" + category + "/new?selectedObjektIds=" + encode(id(item)); }

    private static String first(Map<String, Object> item, String... keys) {
        for (String key : keys) {
            Object value = item.get(key);
            if (value != null && !value.toString().isBlank() && !"null".equalsIgnoreCase(value.toString())) {
                return value.toString();
            }
        }
        return "";
    }

    private static String token(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    private static boolean isReadableSlug(String slug) {
        if (slug == null || slug.isBlank()) return false;
        if (slug.matches(UUID_PATTERN)) return false;
        return slug.matches("[a-z0-9][a-z0-9-]{0,199}");
    }
}
