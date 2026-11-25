package com.example.triplesweb.controller;

import com.example.triplesweb.entity.Article;
import com.example.triplesweb.entity.ArticleObjekt;
import com.example.triplesweb.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.ui.ExtendedModelMap;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;

class ArticleObjektOrderingTest {
    @Test void composePrefillsOnlyKnownSelectedObjectsForTradeAndSell() {
        var catalog = mock(ObjektService.class);
        var controller = new ArticleController(mock(ArticleService.class), mock(ArticleFileServiceImpl.class),
                mock(DateTimeFormatService.class), mock(ArticleObjektService.class), catalog, mock(ArtistSnapshotService.class));
        when(catalog.findCollections("triples")).thenReturn(List.of(Map.of("id","uuid","slug","hayeoncc367z","collectionId","Cream02 HaYeon 367Z","member","HaYeon","season","Cream02")));
        for (String category : List.of("trade","sell")) {
            var model = new ExtendedModelMap();
            controller.showArticleForm(category, List.of("uuid","invalid"), model);
            assertThat((List<Map<String,Object>>)model.get("objektSelected")).hasSize(1)
                    .first().extracting(row -> row.get("id")).isEqualTo("uuid");
            assertThat((List<Map<String,Object>>)model.get("objektSelected"))
                    .extracting(row -> row.get("cardTitle")).containsExactly("HaYeon 367Z");
            assertThat(model.get("objektInitialSearch")).isEqualTo("Cream02 HaYeon 367Z");
            if ("trade".equals(category)) {
                assertThat(model.get("isTradeObjektSplit")).isEqualTo(true);
                assertThat((List<Map<String,Object>>)model.get("objektHaveSelected")).hasSize(1);
                assertThat((List<Map<String,Object>>)model.get("objektWantSelected")).isEmpty();
            } else {
                assertThat(model.get("isTradeObjektSplit")).isEqualTo(false);
            }
        }
    }
    @Test void tradeAndSellNewAndEditFormsShareMemberAndSeasonOrder() {
        var articles = mock(ArticleService.class);
        var objekts = mock(ObjektService.class);
        var links = mock(ArticleObjektService.class);
        var controller = new ArticleController(articles, mock(ArticleFileServiceImpl.class),
                mock(DateTimeFormatService.class), links, objekts, mock(ArtistSnapshotService.class));
        when(objekts.findCollections("triples")).thenReturn(List.of(
                Map.of("member", "Jiyeon", "season", "Atom01"), Map.of("member", "Alpha", "season", "Cream02"),
                Map.of("member", "HyeRin", "season", "Ever01"), Map.of("member", "SeoYeon", "season", "Binary02"),
                Map.of("member", "S5 X S20", "season", "Cream02")));
        Article article = new Article(); article.setId(3L); article.setTitle("test"); article.setContent("test");
        when(articles.showWithFiles(3L)).thenReturn(article);
        when(links.findForArticle(3L)).thenReturn(List.of());
        for (String category : List.of("trade", "sell")) {
            var fresh = new ExtendedModelMap();
            controller.showArticleForm(category, fresh);
            var edited = new ExtendedModelMap();
            controller.editArticle(category, 3L, edited);
            var options = controller.objektOptions("", "", "", "", "all", 0, 24, "", "latest");
            assertThat((List<Map<String, Object>>) options.get("items"))
                    .extracting(item -> item.get("member"), item -> item.get("memberDisplay"))
                    .contains(org.assertj.core.api.Assertions.tuple("Jiyeon", "지연"),
                            org.assertj.core.api.Assertions.tuple("SeoYeon", "윤서연"),
                            org.assertj.core.api.Assertions.tuple("S5 X S20", "김유연 × 박시온"));
            for (var model : List.of(fresh, edited)) {
                assertThat((List<Map<String, Object>>) model.get("memberPickerOptions"))
                        .extracting(row -> row.get("value"))
                        .containsExactly("윤서연", "정혜린", "김유연", "박시온", "지연");
                assertThat(model.get("selectedGroup")).isEqualTo("all");
                assertThat(model.get("objektSeasonOptions")).isEqualTo(List.of("Cream02", "Binary02", "Ever01", "Atom01"));
            }
        }
    }

    @Test void mergedMemberOptionSearchesAllOriginalAliases() {
        var catalog = mock(ObjektService.class);
        var controller = new ArticleController(mock(ArticleService.class), mock(ArticleFileServiceImpl.class),
                mock(DateTimeFormatService.class), mock(ArticleObjektService.class), catalog, mock(ArtistSnapshotService.class));
        when(catalog.findCollections("triples")).thenReturn(List.of(Map.of("member", "S21"),
                Map.of("member", "ChaeWon"), Map.of("member", "김채원"), Map.of("member", "S22"),
                Map.of("member", "zenith")));
        assertThat(controller.objektOptions("김채원", "", "", "", "members", 0, 24, "", "latest")).containsEntry("total", 3);
        assertThat(controller.objektOptions("S21", "", "", "", "members", 0, 24, "", "latest")).containsEntry("total", 3);
        assertThat(controller.objektOptions("", "", "", "", "other", 0, 24, "", "latest")).containsEntry("total", 1);
        var model = new ExtendedModelMap(); controller.showArticleForm("trade", model);
        assertThat((List<Map<String, Object>>) model.get("memberPickerOptions"))
                .extracting(row -> row.get("value"))
                .containsExactly("김채원", "설린");
        assertThat(model.get("selectedGroup")).isEqualTo("all");
    }

    @Test @SuppressWarnings("unchecked") void objektOptionsFiltersKindAndSortsByNumber() {
        var catalog = mock(ObjektService.class);
        var controller = new ArticleController(mock(ArticleService.class), mock(ArticleFileServiceImpl.class),
                mock(DateTimeFormatService.class), mock(ArticleObjektService.class), catalog, mock(ArtistSnapshotService.class));
        when(catalog.findCollections("triples")).thenReturn(List.of(
                Map.of("id", "a", "member", "SeoYeon", "collectionNo", "102Z", "createdAt", "2024-01-01"),
                Map.of("id", "b", "member", "HyeRin", "collectionNo", "101Z", "createdAt", "2024-02-01"),
                Map.of("id", "c", "member", "Jiwoo", "collectionNo", "102Z", "createdAt", "2024-03-01")));
        var kindFiltered = controller.objektOptions("", "", "", "", "all", 0, 24, "102Z", "latest");
        assertThat(kindFiltered.get("total")).isEqualTo(2);
        var sorted = controller.objektOptions("", "", "", "", "all", 0, 24, "", "number_low");
        assertThat((List<Map<String, Object>>) sorted.get("items"))
                .extracting(row -> row.get("collectionNo"))
                .containsExactly("101Z", "102Z", "102Z");
        var form = new ExtendedModelMap();
        controller.showArticleForm("trade", form);
        assertThat((List<String>) form.get("objektKindOptions")).containsExactly("101Z", "102Z");
    }

    @Test void tradeDetailRedirectsToFeed() {
        var controller = new ArticleController(mock(ArticleService.class), mock(ArticleFileServiceImpl.class),
                mock(DateTimeFormatService.class), mock(ArticleObjektService.class), mock(ObjektService.class), mock(ArtistSnapshotService.class));
        var model = new ExtendedModelMap();
        assertThat(controller.showArticle("trade", 9L, model)).isEqualTo("redirect:/trade");
        assertThat(controller.showArticle("sell", 11L, model)).isEqualTo("redirect:/sell");
    }

}
