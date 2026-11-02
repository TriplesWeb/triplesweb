package com.example.triplesweb.controller;

import com.example.triplesweb.config.MustacheConfig;
import com.example.triplesweb.entity.Article;
import com.example.triplesweb.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import java.util.List;
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
                .andExpect(content().string(not(containsString(">추천<"))));
            verify(articles, never()).findPopularArticles(eq(category), anyInt());
        }
        mvc.perform(get("/article/info")).andExpect(status().isOk())
            .andExpect(content().string(not(containsString("class=\"market-tabs\""))));
        for (String category : List.of("free", "chat", "all")) {
            mvc.perform(get("/article/" + category)).andExpect(redirectedUrl("/article/trade"));
        }
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
