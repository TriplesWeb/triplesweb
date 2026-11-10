package com.example.triplesweb.service;

import com.example.triplesweb.entity.Article;
import com.example.triplesweb.util.ObjektCatalogSort;
import com.example.triplesweb.util.ObjektLinks;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 헤더 종 패널용. 세션 없이 최신 공지와 카탈로그 오브젝트를 돌려 준다. */
@Service
@RequiredArgsConstructor
public class SiteAlertsService {

    static final int LIMIT = 8;

    private final ArticleService articles;
    private final ObjektService objekts;
    private final ObjektLiveSettings live;

    public Map<String, Object> alerts() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("catalogSig", objekts.catalogSignature());
        body.put("catalogCount", objekts.catalogCount());
        body.put("dropWindow", live.dropWindow());
        body.put("notices", notices());
        body.put("objekts", newestObjekts());
        return body;
    }

    private List<Map<String, Object>> notices() {
        List<Map<String, Object>> rows = new ArrayList<>();
        try {
            for (Article article : articles.findRecentByCategory("notice", LIMIT)) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("id", article.getId());
                row.put("title", article.getTitle() == null ? "" : article.getTitle());
                row.put("href", "/article/notice/" + article.getId());
                row.put("at", article.getFormattedDate());
                rows.add(row);
            }
        } catch (RuntimeException ignored) {
            return List.of();
        }
        return rows;
    }

    private List<Map<String, Object>> newestObjekts() {
        List<Map<String, Object>> catalog;
        try {
            catalog = objekts.findCollections("triples");
        } catch (RuntimeException ignored) {
            return List.of();
        }
        if (catalog == null || catalog.isEmpty()) return List.of();
        return catalog.stream()
                .sorted(Comparator.comparing(SiteAlertsService::createdAt, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(ObjektCatalogSort.comparator("latest")))
                .limit(LIMIT)
                .map(item -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    String member = text(item, "member", "memberName");
                    String number = text(item, "collectionNo", "collectionNumber");
                    String label = (member + " " + number).trim();
                    if (label.isBlank()) label = text(item, "collectionId", "searchText");
                    row.put("label", label);
                    row.put("href", ObjektLinks.detail(item));
                    row.put("collectionId", text(item, "collectionId", "slug"));
                    row.put("createdAt", createdAt(item) == null ? "" : createdAt(item));
                    return row;
                })
                .toList();
    }

    private static String createdAt(Map<String, Object> item) {
        for (String key : List.of("createdAt", "created_at", "date")) {
            Object value = item.get(key);
            if (value != null && !value.toString().isBlank() && !"null".equalsIgnoreCase(value.toString())) {
                return value.toString();
            }
        }
        return null;
    }

    private static String text(Map<String, Object> item, String... keys) {
        for (String key : keys) {
            Object value = item.get(key);
            if (value != null && !value.toString().isBlank() && !"null".equalsIgnoreCase(value.toString())) {
                return value.toString();
            }
        }
        return "";
    }
}
