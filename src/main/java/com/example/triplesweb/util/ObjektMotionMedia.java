package com.example.triplesweb.util;

import java.util.Locale;
import java.util.Map;

/** Motion 클래스 영상 URL. Cosmo 공개 카탈로그에는 frontMedia가 없어 Apollo CDN을 쓴다. */
public final class ObjektMotionMedia {
    private static final String APOLLO_MCO = "https://cdn.apollo.cafe/mco";

    private ObjektMotionMedia() {}

    public static String resolve(Map<String, Object> item) {
        String existing = text(item, "frontMedia");
        if (!existing.isBlank()) return existing;
        if (!"motion".equalsIgnoreCase(text(item, "class", "classKey"))) return "";
        String slug = slug(item);
        if (slug.isBlank()) return "";
        String artist = text(item, "artist").toLowerCase(Locale.ROOT);
        if (artist.isBlank()) artist = "triples";
        return APOLLO_MCO + "/" + artist + "/" + slug + ".mp4";
    }

    static String slug(Map<String, Object> item) {
        String slug = text(item, "slug").toLowerCase(Locale.ROOT);
        if (slug.matches("[a-z0-9]+(?:-[a-z0-9]+)+")) return slug;
        String collectionId = text(item, "collectionId");
        if (collectionId.isBlank()) return "";
        return collectionId.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
    }

    private static String text(Map<String, Object> item, String... keys) {
        if (item == null) return "";
        for (String key : keys) {
            Object value = item.get(key);
            if (value != null && !value.toString().isBlank() && !"null".equalsIgnoreCase(value.toString().trim())) {
                return value.toString().trim();
            }
        }
        return "";
    }
}
