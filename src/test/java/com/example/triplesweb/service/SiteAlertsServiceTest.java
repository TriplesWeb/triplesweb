package com.example.triplesweb.service;

import com.example.triplesweb.entity.Article;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SiteAlertsServiceTest {
    @Test
    @SuppressWarnings("unchecked")
    void newestObjektsFollowCreatedAtAndNoticesUseNoticeCategory() {
        ArticleService articles = mock(ArticleService.class);
        ObjektService objekts = mock(ObjektService.class);
        ObjektLiveSettings live = mock(ObjektLiveSettings.class);
        Article notice = new Article();
        notice.setId(7L);
        notice.setTitle("드롭 안내");
        notice.setCategory("notice");
        when(articles.findRecentByCategory("notice", 8)).thenReturn(List.of(notice));
        when(objekts.catalogSignature()).thenReturn("sig-2");
        when(objekts.catalogCount()).thenReturn(2);
        when(live.dropWindow()).thenReturn(true);
        when(objekts.findCollections("triples")).thenReturn(List.of(
                Map.of("member", "SeoYeon", "collectionNo", "101", "collectionId", "old", "createdAt", "2025-01-01", "slug", "seo-101"),
                Map.of("member", "HaYeon", "collectionNo", "323", "collectionId", "new", "createdAt", "2026-11-10", "slug", "ha-323")
        ));

        Map<String, Object> body = new SiteAlertsService(articles, objekts, live).alerts();
        assertThat(body.get("catalogSig")).isEqualTo("sig-2");
        assertThat(body.get("dropWindow")).isEqualTo(true);
        List<Map<String, Object>> notices = (List<Map<String, Object>>) body.get("notices");
        assertThat(notices.get(0).get("href")).isEqualTo("/article/notice/7");
        List<Map<String, Object>> newest = (List<Map<String, Object>>) body.get("objekts");
        assertThat(newest.get(0).get("label").toString()).contains("HaYeon");
        assertThat(newest.get(0).get("createdAt")).isEqualTo("2026-11-10");
    }
}
