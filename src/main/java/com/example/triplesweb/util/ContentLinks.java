package com.example.triplesweb.util;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 아티스트 단독 컨텐츠/출연 방송용 영상 링크 목록 JSON 유틸.
 * 저장 형식: [{"title":"...","url":"https://..."}, ...]
 */
public final class ContentLinks {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private ContentLinks() {}

    public static List<Map<String, String>> parse(String raw) {
        if (!StringUtils.hasText(raw)) return List.of();
        String trimmed = raw.trim();
        if (!trimmed.startsWith("[")) {
            // 예전 평문 호환: 한 줄이면 제목만
            return List.of(Map.of("title", trimmed, "url", ""));
        }
        try {
            List<Map<String, Object>> rows = MAPPER.readValue(trimmed, new TypeReference<>() {});
            List<Map<String, String>> out = new ArrayList<>();
            for (Map<String, Object> row : rows) {
                if (row == null) continue;
                String title = stringVal(row.get("title"));
                if (!StringUtils.hasText(title)) title = stringVal(row.get("text"));
                String url = stringVal(row.get("url"));
                if (!StringUtils.hasText(title) && !StringUtils.hasText(url)) continue;
                Map<String, String> item = new LinkedHashMap<>();
                item.put("title", title);
                item.put("url", url);
                out.add(item);
            }
            return out;
        } catch (Exception e) {
            return List.of(Map.of("title", trimmed, "url", ""));
        }
    }

    public static String toJson(List<Map<String, String>> links) {
        List<Map<String, String>> clean = new ArrayList<>();
        if (links != null) {
            for (Map<String, String> link : links) {
                if (link == null) continue;
                String title = link.get("title") != null ? link.get("title").trim() : "";
                String url = link.get("url") != null ? link.get("url").trim() : "";
                if (!StringUtils.hasText(title) && !StringUtils.hasText(url)) continue;
                if (!StringUtils.hasText(url)) continue;
                if (!url.matches("(?i)^https?://.+")) {
                    throw new IllegalArgumentException("영상 링크는 http(s):// 로 시작해야 합니다: " + url);
                }
                if (!StringUtils.hasText(title)) title = url;
                Map<String, String> item = new LinkedHashMap<>();
                item.put("title", title);
                item.put("url", url);
                clean.add(item);
            }
        }
        if (clean.isEmpty()) return "";
        try {
            return MAPPER.writeValueAsString(clean);
        } catch (Exception e) {
            throw new IllegalStateException("링크 JSON 직렬화 실패", e);
        }
    }

    public static String normalizeJson(String raw) {
        return toJson(parse(raw));
    }

    private static String stringVal(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
