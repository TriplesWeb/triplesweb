package com.example.triplesweb.service;

import com.example.triplesweb.entity.Article;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 헤더 종 패널용. 세션 없이 최신 공지와 미리 계산된 드롭 알림을 돌려 준다. */
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
        body.put("objekts", dropAlerts());
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

    private List<Map<String, Object>> dropAlerts() {
        try {
            List<Map<String, Object>> drops = objekts.newestDrops();
            return drops == null ? List.of() : drops;
        } catch (RuntimeException ignored) {
            return List.of();
        }
    }
}
