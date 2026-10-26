package com.example.triplesweb.util;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;

/** Public URLs use the index's readable slug; persistence continues using the collection ID. */
public final class ObjektLinks {
    private ObjektLinks() {}
    public static String id(Map<String, Object> item) {
        for (String field : new String[]{"id", "collectionId", "slug"}) {
            String value = String.valueOf(item.getOrDefault(field, ""));
            if (!value.isBlank()) return value;
        }
        return "";
    }
    public static String key(Map<String, Object> item) {
        String slug = String.valueOf(item.getOrDefault("slug", "")).toLowerCase(Locale.ROOT);
        return slug.matches("[a-z0-9][a-z0-9-]{0,199}") ? slug : id(item);
    }
    public static String encode(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20"); }
    public static String detail(Map<String, Object> item) { return "/cosmo/objekt/detail/" + encode(key(item)); }
    public static String member(Map<String, Object> item) { return "/cosmo/objekt?group=members&member=" + encode(String.valueOf(item.getOrDefault("member", ""))); }
    public static String compose(Map<String, Object> item, String category) { return "/article/" + category + "/new?selectedObjektIds=" + encode(id(item)); }
}
