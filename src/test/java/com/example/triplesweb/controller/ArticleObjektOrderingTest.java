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
                Map.of("member", "HyeRin", "season", "Ever01"), Map.of("member", "SeoYeon", "season", "Binary02")));
        Article article = new Article(); article.setId(3L); article.setTitle("test"); article.setContent("test");
        when(articles.showWithFiles(3L)).thenReturn(article);
        when(links.findForArticle(3L)).thenReturn(List.of());
        for (String category : List.of("trade", "sell")) {
            var fresh = new ExtendedModelMap();
            controller.showArticleForm(category, fresh);
            var edited = new ExtendedModelMap();
            controller.editArticle(category, 3L, edited);
            for (var model : List.of(fresh, edited)) {
                assertThat(model.get("objektMemberOptions")).isEqualTo(List.of("SeoYeon", "HyeRin", "Jiyeon", "Alpha"));
                assertThat(model.get("objektSeasonOptions")).isEqualTo(List.of("Cream02", "Binary02", "Ever01", "Atom01"));
            }
        }
    }
}
