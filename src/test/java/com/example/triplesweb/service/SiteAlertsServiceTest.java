package com.example.triplesweb.service;

import com.example.triplesweb.entity.Article;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SiteAlertsServiceTest {
    @Test
    @SuppressWarnings("unchecked")
    void alertsReturnTodayReleasedDropsAndNotices() {
        ArticleService articles = mock(ArticleService.class);
        ObjektService objekts = mock(ObjektService.class);
        ObjektLiveSettings live = mock(ObjektLiveSettings.class);
        Article notice = new Article();
        notice.setId(7L);
        notice.setTitle("드롭 안내");
        notice.setCategory("notice");
        String today = todayNoon();
        when(articles.findRecentByCategory("notice", 8)).thenReturn(List.of(notice));
        when(objekts.catalogSignature()).thenReturn("sig-2");
        when(objekts.catalogCount()).thenReturn(24);
        when(live.dropWindow()).thenReturn(true);
        when(objekts.newestDrops()).thenReturn(List.of(
                Map.of("label", "Cream02(CC) 350z 새 오브젝트 출시",
                        "href", "/cosmo/objekt?season=cream02&kind=350z",
                        "collectionId", "Cream02 HaYeon 350z",
                        "createdAt", today)
        ));

        Map<String, Object> body = new SiteAlertsService(articles, objekts, live).alerts();
        assertThat(body.get("catalogSig")).isEqualTo("sig-2");
        assertThat(body.get("dropWindow")).isEqualTo(true);
        List<Map<String, Object>> notices = (List<Map<String, Object>>) body.get("notices");
        assertThat(notices.get(0).get("href")).isEqualTo("/article/notice/7");
        List<Map<String, Object>> newest = (List<Map<String, Object>>) body.get("objekts");
        assertThat(newest).hasSize(1);
        assertThat(newest.get(0).get("label")).isEqualTo("Cream02(CC) 350z 새 오브젝트 출시");
        assertThat(newest.get(0).get("href")).isEqualTo("/cosmo/objekt?season=cream02&kind=350z");
        assertThat(newest.get(0).get("createdAt")).isEqualTo(today);
    }

    @Test
    @SuppressWarnings("unchecked")
    void alertsHideOlderCatalogRowsAndDropWindowWithoutARelease() {
        ArticleService articles = mock(ArticleService.class);
        ObjektService objekts = mock(ObjektService.class);
        ObjektLiveSettings live = mock(ObjektLiveSettings.class);
        when(articles.findRecentByCategory("notice", 8)).thenReturn(List.of());
        when(objekts.catalogSignature()).thenReturn("sig-3");
        when(objekts.catalogCount()).thenReturn(24);
        when(live.dropWindow()).thenReturn(true);
        when(objekts.newestDrops()).thenReturn(List.of(
                Map.of("label", "Cream02(CC) 334Z 새 오브젝트 출시",
                        "href", "/cosmo/objekt?season=cream02&kind=334z",
                        "createdAt", "2026-11-10T09:00:00Z")
        ));

        Map<String, Object> body = new SiteAlertsService(articles, objekts, live).alerts();
        assertThat((List<?>) body.get("objekts")).isEmpty();
        assertThat(body.get("dropWindow")).isEqualTo(false);
    }

    @Test
    @SuppressWarnings("unchecked")
    void alertsKeepTodayDropsEvenOutsideDropHours() {
        ArticleService articles = mock(ArticleService.class);
        ObjektService objekts = mock(ObjektService.class);
        ObjektLiveSettings live = mock(ObjektLiveSettings.class);
        String today = todayNoon();
        when(articles.findRecentByCategory("notice", 8)).thenReturn(List.of());
        when(objekts.catalogSignature()).thenReturn("sig-4");
        when(objekts.catalogCount()).thenReturn(24);
        when(live.dropWindow()).thenReturn(false);
        when(objekts.newestDrops()).thenReturn(List.of(
                Map.of("label", "Cream02(CC) 350z 새 오브젝트 출시",
                        "href", "/cosmo/objekt?season=cream02&kind=350z",
                        "createdAt", today)
        ));

        Map<String, Object> body = new SiteAlertsService(articles, objekts, live).alerts();
        assertThat(body.get("dropWindow")).isEqualTo(false);
        List<Map<String, Object>> newest = (List<Map<String, Object>>) body.get("objekts");
        assertThat(newest).hasSize(1);
        assertThat(newest.get(0).get("label")).isEqualTo("Cream02(CC) 350z 새 오브젝트 출시");
    }

    @Test
    void releasedTodayKeepsThreeNewestOfToday() {
        LocalDate today = LocalDate.of(2026, 11, 15);
        List<Map<String, Object>> drops = List.of(
                Map.of("label", "a", "createdAt", "2026-11-15T04:00:00Z"),
                Map.of("label", "b", "createdAt", "2026-11-15T03:00:00Z"),
                Map.of("label", "c", "createdAt", "2026-11-15T02:00:00Z"),
                Map.of("label", "d", "createdAt", "2026-11-15T01:00:00Z"),
                Map.of("label", "old", "createdAt", "2026-11-14T14:00:00Z")
        );
        assertThat(SiteAlertsService.releasedToday(drops, today))
                .extracting(drop -> drop.get("label"))
                .containsExactly("a", "b", "c");
    }

    @Test
    void dropAlertsKeepOnlyThreeNewestGroups() {
        List<Map<String, Object>> views = List.of(
                row("Cream02", "334Z", "HaYeon", "2026-11-15T08:00:00Z"),
                row("Cream02", "336Z", "HaYeon", "2026-11-15T07:00:00Z"),
                row("Cream02", "337Z", "HaYeon", "2026-11-15T06:00:00Z"),
                row("Cream02", "335Z", "HaYeon", "2026-11-15T05:00:00Z")
        );
        assertThat(ObjektService.dropAlerts(views)).extracting(drop -> drop.get("label"))
                .containsExactly(
                        "Cream02(CC) 334Z 새 오브젝트 출시",
                        "Cream02(CC) 336Z 새 오브젝트 출시",
                        "Cream02(CC) 337Z 새 오브젝트 출시");
    }

    @Test
    void dropAlertsCollapseSameSeasonNumberIntoOneRow() {
        Map<String, Object> hayeon = row("Cream02", "350z", "HaYeon", "2026-11-10T10:00:00Z");
        Map<String, Object> seoyeon = row("Cream02", "350z", "SeoYeon", "2026-11-10T09:00:00Z");
        Map<String, Object> older = row("Atom01", "101", "HaYeon", "2025-01-01T00:00:00Z");

        List<Map<String, Object>> drops = ObjektService.dropAlerts(List.of(hayeon, seoyeon, older));
        assertThat(drops).hasSize(2);
        assertThat(drops.get(0).get("label")).isEqualTo("Cream02(CC) 350z 새 오브젝트 출시");
        assertThat(drops.get(0).get("href")).isEqualTo("/cosmo/objekt?season=cream02&kind=350z");
        assertThat(drops.get(0).get("createdAt")).isEqualTo("2026-11-10T09:00:00Z");
        assertThat(drops.get(0).get("label").toString()).doesNotContain("HaYeon").doesNotContain("SeoYeon");
        assertThat(drops.get(1).get("label")).isEqualTo("Atom01(A) 101 새 오브젝트 출시");
    }

    @Test
    void dropLabelsOmitUnknownOrMissingSeasonCodes() {
        Map<String, Object> unknown = row("Welcome", "001Z", "HaYeon", "2026-01-02T00:00:00Z");
        Map<String, Object> noSeason = row("", "002A", "SeoYeon", "2026-01-01T00:00:00Z");
        List<Map<String, Object>> drops = ObjektService.dropAlerts(List.of(unknown, noSeason));
        assertThat(drops).extracting(drop -> drop.get("label"))
                .containsExactly("Welcome 001Z 새 오브젝트 출시", "002A 새 오브젝트 출시");
    }

    private static Map<String, Object> row(String season, String number, String member, String createdAt) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("season", season);
        item.put("seasonKey", season.toLowerCase());
        item.put("collectionNo", number);
        item.put("member", member);
        item.put("createdAt", createdAt);
        item.put("collectionId", season + " " + member + " " + number);
        item.put("slug", member.toLowerCase() + number.toLowerCase());
        return item;
    }

    private static String todayNoon() {
        return LocalDate.now(ObjektLiveSettings.ZONE).atTime(12, 0)
                .atZone(ObjektLiveSettings.ZONE).toInstant().toString();
    }
}
