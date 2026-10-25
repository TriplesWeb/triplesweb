package com.example.triplesweb.controller;

import com.example.triplesweb.entity.Article;
import com.example.triplesweb.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.ui.ExtendedModelMap;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;

class ArticleObjektOrderingTest {
    @Test void tradeAndSellNewAndEditFormsShareMemberAndSeasonOrder() {
        var articles = mock(ArticleService.class);
        var objekts = mock(ObjektService.class);
        var links = mock(ArticleObjektService.class);
        var controller = new ArticleController(articles, mock(CommentService.class), mock(ArticleFileServiceImpl.class),
                mock(DateTimeFormatService.class), links, objekts);
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
            for (var model : List.of(fresh, edited)) {
                assertThat(model.get("objektMemberOptions")).isEqualTo(List.of("윤서연", "정혜린", "김유연", "박시온", "지연"));
                assertThat(model.get("hasOtherObjektOption")).isEqualTo(true);
                assertThat(model.get("objektSeasonOptions")).isEqualTo(List.of("Cream02", "Binary02", "Ever01", "Atom01"));
            }
        }
    }

    @Test void mergedMemberOptionSearchesAllOriginalAliases() {
        var catalog = mock(ObjektService.class);
        var controller = new ArticleController(mock(ArticleService.class), mock(CommentService.class), mock(ArticleFileServiceImpl.class),
                mock(DateTimeFormatService.class), mock(ArticleObjektService.class), catalog);
        when(catalog.findCollections("triples")).thenReturn(List.of(Map.of("member", "S21"),
                Map.of("member", "ChaeWon"), Map.of("member", "김채원"), Map.of("member", "S22"),
                Map.of("member", "zenith")));
        assertThat(controller.objektOptions("김채원", "", "", "", "members", 0, 24)).containsEntry("total", 3);
        assertThat(controller.objektOptions("S21", "", "", "", "members", 0, 24)).containsEntry("total", 3);
        assertThat(controller.objektOptions("", "", "", "", "other", 0, 24)).containsEntry("total", 1);
        var model = new ExtendedModelMap(); controller.showArticleForm("trade", model);
        assertThat(model.get("objektMemberOptions")).isEqualTo(List.of("김채원", "설린"));
        assertThat(model.get("hasOtherObjektOption")).isEqualTo(true);
    }
}
