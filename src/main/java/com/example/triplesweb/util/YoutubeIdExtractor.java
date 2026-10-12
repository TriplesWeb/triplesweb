package com.example.triplesweb.util;


import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

public class YoutubeIdExtractor {

    public static String extract(String urlOrId) {
        if (urlOrId == null || urlOrId.isBlank()) return "";
        String s = urlOrId.trim();
        if (s.isEmpty()) return "";


        if (s.matches("^[a-zA-Z0-9_-]{6,15}$")) return s;

        try {
            URI u = URI.create(s);
            String host = u.getHost() == null ? "" : u.getHost();
            String path = u.getPath() == null ? "" : u.getPath();

            if (host.contains("youtu.be")) {
                return path.replace("/", "");
            }
            if (host.contains("youtube.com")) {
                Map<String, String> q = splitQuery(u.getRawQuery());
                if (q.containsKey("v")) return q.get("v");

                String[] parts = path.split("/");
                for (int i = 0; i < parts.length - 1; i++) {
                    if ("embed".equals(parts[i]) || "shorts".equals(parts[i])) {
                        return parts[i + 1];
                    }
                }
            }
        } catch (Exception ignored) {}
        return "";
    }

    private static Map<String, String> splitQuery(String query) {
        Map<String, String> map = new LinkedHashMap<>();
        if (query == null || query.isBlank()) return map;
        for (String pair : query.split("&")) {
            if (pair.isEmpty()) continue;

            int idx = pair.indexOf('=');
            if (idx < 0) continue;
            String k = (idx > 0) ? pair.substring(0, idx) : "";
            String v = (idx < pair.length() - 1) ? pair.substring(idx + 1) : "";

            map.put(
                    URLDecoder.decode(k, StandardCharsets.UTF_8),
                    URLDecoder.decode(v, StandardCharsets.UTF_8)
            );
        }
        return map;
    }
}
