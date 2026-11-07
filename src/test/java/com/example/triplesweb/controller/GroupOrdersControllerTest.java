package com.example.triplesweb.controller;

import com.example.triplesweb.config.MustacheConfig;
import com.example.triplesweb.entity.Article;
import com.example.triplesweb.entity.ArticleObjekt;
import com.example.triplesweb.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import java.util.List;
import java.util.Map;
import java.util.stream.LongStream;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ArticleController.class)
@AutoConfigureMockMvc(addFilters=false)
@Import(MustacheConfig.class)
class GroupOrdersControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean ArticleService articles;
    @MockitoBean CommentService comments;
    @MockitoBean ArticleFileServiceImpl files;
    @MockitoBean DateTimeFormatService dates;
    @MockitoBean ArticleObjektService links;
    @MockitoBean ObjektService objekts;
    @MockitoBean ArtistSnapshotService artists;

    private Article article(String category) {
        Article a=new Article(); a.setId(5L); a.setTitle("글"); a.setContent("<p>내용</p>"); a.setCategory(category);
        return a;
    }

    @Test void groupOrdersRoutesRedirectToTrade() throws Exception {
        mvc.perform(get("/article/group-orders")).andExpect(redirectedUrl("/article/trade"));
        mvc.perform(get("/article/group-orders").param("type","content").param("keyword","모집"))
            .andExpect(redirectedUrl("/article/trade"));
        mvc.perform(get("/article/group-orders/new")).andExpect(redirectedUrl("/article/trade"));
        mvc.perform(get("/article/group-orders/5")).andExpect(redirectedUrl("/article/trade"));
        mvc.perform(get("/article/group-orders/5/edit")).andExpect(redirectedUrl("/article/trade"));
        mvc.perform(post("/article/group-orders/new").param("title","분철").param("content","<p>본문</p>"))
            .andExpect(redirectedUrl("/article/trade"));
        verifyNoInteractions(articles);
    }

    @Test void marketTabsKeepTradeAndSellWithoutGroupOrders() throws Exception {
        for (String category : List.of("trade", "sell")) {
            when(articles.findRecentByCategory(eq(category), anyInt())).thenReturn(List.of(article(category)));
            mvc.perform(get("/article/" + category)).andExpect(status().isOk())
                .andExpect(content().string(containsString("href=\"/article/trade\"")))
                .andExpect(content().string(containsString("href=\"/article/sell\"")))
                .andExpect(content().string(not(containsString("href=\"/article/group-orders\""))))
                .andExpect(content().string(not(containsString("분철"))))
                .andExpect(content().string(containsString("href=\"/article/" + category + "\" aria-current=\"page\"")))
                .andExpect(content().string(not(containsString(">추천<"))))
                .andExpect(content().string(containsString("article-board-heading is-market is-no-likes")))
                .andExpect(content().string(containsString("article-board-row is-market is-no-likes")))
                .andExpect(content().string(containsString(">분류<")))
                .andExpect(content().string(containsString("article-board-swap")))
                .andExpect(content().string(containsString("objektSide=have")))
                .andExpect(content().string(not(containsString("objektSide=all"))))
                .andExpect(content().string(not(containsString(">전체</a>"))));
            verify(articles, never()).findPopularArticles(eq(category), anyInt());
        }
        mvc.perform(get("/article/info")).andExpect(status().isOk())
            .andExpect(content().string(not(containsString("class=\"market-tabs\""))));
        for (String category : List.of("free", "chat", "all")) {
            mvc.perform(get("/article/" + category)).andExpect(redirectedUrl("/article/trade"));
        }
    }

    @Test void memberSearchContinuesPastTwoHundredAndLegacyAllMatchesWant() throws Exception {
        List<Article> first = LongStream.iterate(300, n -> n - 1).limit(100).mapToObj(id -> {
            Article a = article("trade"); a.setId(id); return a;
        }).toList();
        List<Article> second = LongStream.iterate(200, n -> n - 1).limit(100).mapToObj(id -> {
            Article a = article("trade"); a.setId(id); return a;
        }).toList();
        Article older = article("trade");
        older.setId(100L);
        older.setTitle("오래된 교환 글");
        ArticleObjekt want = new ArticleObjekt();
        want.setSide("WANT");
        want.setMemberName("S1 X S9");
        when(articles.findCategoryBatchBefore(eq("trade"), eq("title"), eq(""), anyLong(), eq(100)))
            .thenAnswer(inv -> {
                long before = inv.getArgument(3);
                if (before == Long.MAX_VALUE) return first;
                if (before == 201L) return second;
                if (before == 101L) return List.of(older);
                return List.of();
            });
        when(links.groupByArticleId(anyList())).thenAnswer(inv -> {
            List<Long> ids = inv.getArgument(0);
            return ids.contains(100L) ? Map.of(100L, List.of(want)) : Map.of();
        });

        mvc.perform(get("/article/trade").param("member", "윤서연").param("objektSide", "all"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("오래된 교환 글")))
            .andExpect(content().string(containsString("기존 전체 검색 링크")));
        mvc.perform(get("/article/trade").param("member", "윤서연").param("objektSide", "have"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("오래된 교환 글")))
            .andExpect(content().string(containsString("Have·Want에 연결된 글을 모두 찾습니다")));
        mvc.perform(get("/article/trade").param("member", "윤서연").param("objektSide", "want"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("오래된 교환 글")));
        verify(articles, atLeastOnce()).findCategoryBatchBefore("trade", "title", "", 101L, 100);
    }

    @Test void memberSearchFindsEveryLinkedHaveAndWantName() throws Exception {
        Article post = article("trade");
        post.setId(88L);
        post.setTitle("코토네 김나경 교환");
        ArticleObjekt haveKotone = new ArticleObjekt();
        haveKotone.setSide("HAVE");
        haveKotone.setMemberName("코토네");
        ArticleObjekt haveNakyoung = new ArticleObjekt();
        haveNakyoung.setSide("HAVE");
        haveNakyoung.setMemberName("NaKyoung");
        ArticleObjekt wantNakyoung = new ArticleObjekt();
        wantNakyoung.setSide("WANT");
        wantNakyoung.setMemberName("김나경");
        when(articles.findCategoryBatchBefore(eq("trade"), eq("title"), eq(""), anyLong(), eq(100)))
            .thenReturn(List.of(post));
        when(links.groupByArticleId(anyList())).thenReturn(Map.of(88L, List.of(haveKotone, haveNakyoung)));
        mvc.perform(get("/article/trade").param("member", "김나경").param("objektSide", "have"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("코토네 김나경 교환")));
        mvc.perform(get("/article/trade").param("member", "코토네"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("코토네 김나경 교환")));

        when(links.groupByArticleId(anyList())).thenReturn(Map.of(88L, List.of(haveKotone, wantNakyoung)));
        mvc.perform(get("/article/trade").param("member", "김나경").param("objektSide", "have"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("코토네 김나경 교환")));
    }

    @Test void noticeBoardIsAllowed() throws Exception {
        when(articles.findRecentByCategory("notice",10)).thenReturn(List.of(article("notice")));
        mvc.perform(get("/article/notice")).andExpect(status().isOk())
            .andExpect(content().string(containsString("공지사항 게시판")))
            .andExpect(content().string(not(containsString("class=\"market-tabs\""))));
    }

    @Test void tradeCreateRequiresHaveAndWantThenStoresBoth() throws Exception {
        Article trade = new Article();
        trade.setId(7L);
        trade.setTitle("교환");
        trade.setContent("<p>내용</p>");
        trade.setCategory("trade");
        when(articles.create(any())).thenReturn(trade);
        mvc.perform(multipart("/article/trade/new")
                .param("title", "교환")
                .param("content", "<p>내용</p>")
                .param("selectedHaveObjektIds", "have-uuid")
                .param("selectedWantObjektIds", "want-uuid"))
            .andExpect(redirectedUrl("/article/trade/7"));
        verify(links).attachSelected(any(Article.class), eq(List.of("have-uuid")), eq(List.of("want-uuid")));

        mvc.perform(multipart("/article/trade/new")
                .param("title", "교환")
                .param("content", "<p>내용</p>")
                .param("selectedHaveObjektIds", "have-uuid"))
            .andExpect(status().isOk())
            .andExpect(model().attribute("errorMessage", "Want 오브젝트를 1개 이상 선택해주세요."));
        verify(articles, times(1)).create(any());
    }
}
