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
        var controller = new ArticleController(mock(ArticleService.class), mock(CommentService.class), mock(ArticleFileServiceImpl.class),
                mock(DateTimeFormatService.class), mock(ArticleObjektService.class), catalog);
        when(catalog.findCollections("triples")).thenReturn(List.of(Map.of("id","uuid","slug","hayeoncc367z","collectionId","Cream02 HaYeon 367Z","member","HaYeon")));
        for (String category : List.of("trade","sell","group-orders")) {
            var model = new ExtendedModelMap();
            controller.showArticleForm(category, List.of("uuid","invalid"), model);
            assertThat((List<Map<String,Object>>)model.get("objektSelected")).hasSize(1)
                    .first().extracting(row -> row.get("id")).isEqualTo("uuid");
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
        var controller = new ArticleController(articles, mock(CommentService.class), mock(ArticleFileServiceImpl.class),
                mock(DateTimeFormatService.class), links, objekts);
        when(objekts.findCollections("triples")).thenReturn(List.of(
                Map.of("member", "Jiyeon", "season", "Atom01"), Map.of("member", "Alpha", "season", "Cream02"),
                Map.of("member", "HyeRin", "season", "Ever01"), Map.of("member", "SeoYeon", "season", "Binary02"),
                Map.of("member", "S5 X S20", "season", "Cream02")));
        Article article = new Article(); article.setId(3L); article.setTitle("test"); article.setContent("test");
        when(articles.showWithFiles(3L)).thenReturn(article);
        when(links.findForArticle(3L)).thenReturn(List.of());
        for (String category : List.of("trade", "sell", "group-orders")) {
            var fresh = new ExtendedModelMap();
            controller.showArticleForm(category, fresh);
            var edited = new ExtendedModelMap();
            controller.editArticle(category, 3L, edited);
            var options = controller.objektOptions("", "", "", "", "all", 0, 24);
        assertThat((List<Map<String, Object>>) options.get("items"))
                .extracting(item -> item.get("member"))
                .contains("지연", "윤서연", "김유연 × 박시온");
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

    @Test @SuppressWarnings("unchecked") void detailShowsKoreanObjektMembers() {
        var articles = mock(ArticleService.class);
        var links = mock(ArticleObjektService.class);
        var controller = new ArticleController(articles, mock(CommentService.class), mock(ArticleFileServiceImpl.class),
                mock(DateTimeFormatService.class), links, mock(ObjektService.class));
        ArticleObjekt duo = new ArticleObjekt();
        duo.setObjektKey("duo");
        duo.setCollectionId("Cream02");
        duo.setMemberName("S5 X S20");
        duo.setCollectionNo("100");
        ArticleObjekt solo = new ArticleObjekt();
        solo.setObjektKey("solo");
        solo.setMemberName("JiYeon");
        when(links.findForArticle(9L)).thenReturn(List.of(duo, solo));
        Article article = new Article();
        article.setId(9L);
        article.setTitle("교환");
        article.setContent("<p>내용</p>");
        article.setCategory("trade");
        when(articles.showWithFiles(9L)).thenReturn(article);
        when(articles.getLikesCount(9L)).thenReturn(0L);
        var model = new ExtendedModelMap();
        controller.showArticle("trade", 9L, null, model);
        assertThat((List<Map<String, Object>>) model.get("linkedObjekts"))
                .extracting(row -> row.get("memberName"))
                .containsExactly("김유연 × 박시온", "지연");
    }

    @Test void blankTitleOrContentAndUnchangedEditAreRejected() throws Exception {
        var articles = mock(ArticleService.class);
        var links = mock(ArticleObjektService.class);
        var controller = new ArticleController(articles, mock(CommentService.class), mock(ArticleFileServiceImpl.class),
                mock(DateTimeFormatService.class), links, mock(ObjektService.class));
        Article existing = new Article();
        existing.setId(4L);
        existing.setTitle("같은 제목");
        existing.setContent("<p>같은 내용</p>");
        existing.setCategory("info");
        when(articles.showWithFiles(4L)).thenReturn(existing);
        when(links.findForArticle(4L)).thenReturn(List.of());

        Article blankTitle = new Article();
        blankTitle.setTitle("  ");
        blankTitle.setContent("<p>본문</p>");
        var missingTitle = new ExtendedModelMap();
        assertThat(controller.updateArticle("info", 4L, blankTitle, null, null, null, null, null, null, missingTitle))
                .isEqualTo("article/article_edit");
        assertThat(missingTitle.get("errorMessage")).isEqualTo("제목을 입력해주세요.");

        Article blankBody = new Article();
        blankBody.setTitle("제목");
        blankBody.setContent("<p><br></p>");
        var missingBody = new ExtendedModelMap();
        assertThat(controller.updateArticle("info", 4L, blankBody, null, null, null, null, null, null, missingBody))
                .isEqualTo("article/article_edit");
        assertThat(missingBody.get("errorMessage")).isEqualTo("내용을 입력해주세요.");

        existing.setTitle("같은 제목");
        existing.setContent("<p>같은 내용</p>");
        Article same = new Article();
        same.setTitle("같은 제목");
        same.setContent("<p>같은 내용</p>");
        var unchanged = new ExtendedModelMap();
        assertThat(controller.updateArticle("info", 4L, same, null, null, null, null, null, null, unchanged))
                .isEqualTo("article/article_edit");
        assertThat(unchanged.get("errorMessage")).isEqualTo("게시글을 수정해주세요.");
    }

    @Test @SuppressWarnings("unchecked") void tradeDetailSplitsHaveAndWantZones() {
        var articles = mock(ArticleService.class);
        var links = mock(ArticleObjektService.class);
        var controller = new ArticleController(articles, mock(CommentService.class), mock(ArticleFileServiceImpl.class),
                mock(DateTimeFormatService.class), links, mock(ObjektService.class));
        ArticleObjekt have = new ArticleObjekt();
        have.setObjektKey("have-key");
        have.setSide(ArticleObjektService.SIDE_HAVE);
        have.setCollectionId("Have Card");
        have.setMemberName("JiYeon");
        ArticleObjekt want = new ArticleObjekt();
        want.setObjektKey("want-key");
        want.setSide(ArticleObjektService.SIDE_WANT);
        want.setCollectionId("Want Card");
        want.setMemberName("HaYeon");
        when(links.findForArticle(11L)).thenReturn(List.of(have, want));
        when(links.findHaveForArticle(11L)).thenReturn(List.of(have));
        when(links.findWantForArticle(11L)).thenReturn(List.of(want));
        Article article = new Article();
        article.setId(11L);
        article.setTitle("교환");
        article.setContent("<p>내용</p>");
        article.setCategory("trade");
        when(articles.showWithFiles(11L)).thenReturn(article);
        when(articles.getLikesCount(11L)).thenReturn(0L);
        var model = new ExtendedModelMap();
        controller.showArticle("trade", 11L, null, model);
        assertThat(model.get("isTradeObjektSplit")).isEqualTo(true);
        assertThat((List<Map<String, Object>>) model.get("linkedHaveObjekts"))
                .extracting(row -> row.get("collectionId")).containsExactly("Have Card");
        assertThat((List<Map<String, Object>>) model.get("linkedWantObjekts"))
                .extracting(row -> row.get("collectionId")).containsExactly("Want Card");
    }
}
