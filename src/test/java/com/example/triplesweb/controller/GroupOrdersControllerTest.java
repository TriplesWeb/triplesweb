package com.example.triplesweb.controller;

import com.example.triplesweb.config.MustacheConfig;
import com.example.triplesweb.entity.Article;
import com.example.triplesweb.service.*;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
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

    private Article article() {
        Article a=new Article(); a.setId(5L); a.setTitle("분철 모집"); a.setContent("<p>내용</p>"); a.setCategory("group-orders");
        return a;
    }
    @Test void listSearchAndComposeReuseBoardWithoutRecommendations() throws Exception {
        when(articles.findRecentByCategory("group-orders",10)).thenReturn(List.of(article()));
        mvc.perform(get("/article/group-orders")).andExpect(status().isOk())
            .andExpect(content().string(containsString("분철 게시판")))
            .andExpect(content().string(not(containsString(">추천<"))))
            .andExpect(content().string(containsString("/article/group-orders/new")));
        mvc.perform(get("/article/group-orders").param("type","content").param("keyword","모집"))
            .andExpect(status().isOk());
        verify(articles).searchByCategory("group-orders","content","모집",10);
        verify(articles,never()).findPopularArticles(eq("group-orders"),anyInt());
        mvc.perform(get("/article/group-orders/new")).andExpect(status().isOk())
            .andExpect(model().attribute("showObjektPicker",true));
    }
    @Test void createIncludesAttachmentsAndSelectedObjekts() throws Exception {
        when(articles.create(any())).thenReturn(article());
        var upload=new MockMultipartFile("uploadFiles","photo.jpg","image/jpeg",new byte[]{1,2,3});
        mvc.perform(multipart("/article/group-orders/new").file(upload)
                .param("title","분철 모집").param("content","<p>모집 내용</p>").param("selectedObjektIds","uuid"))
            .andExpect(redirectedUrl("/article/group-orders/5"));
        var saved=ArgumentCaptor.forClass(Article.class);verify(articles).create(saved.capture());
        assertThat(saved.getValue().getCategory()).isEqualTo("group-orders");
        verify(files).uploadFile(eq(upload),any(Article.class));
        verify(links).attachSelected(any(Article.class),eq(List.of("uuid")));
    }
    @Test void detailEditDeleteAndDisabledLikeUseExistingContracts() throws Exception {
        when(articles.showWithFiles(5L)).thenReturn(article());
        when(links.findForArticle(5L)).thenReturn(List.of());
        mvc.perform(get("/article/group-orders/5")).andExpect(status().isOk())
            .andExpect(model().attribute("isLikeAllowed",false))
            .andExpect(content().string(not(containsString("id=\"likeButton\""))));
        verify(comments).show(5L);
        mvc.perform(get("/article/group-orders/5/edit")).andExpect(status().isOk());
        when(articles.updateWithFiles(eq(5L),anyString(),anyString(),any(),any(),any())).thenReturn(article());
        mvc.perform(post("/article/group-orders/5").param("title","수정한 모집").param("content","<p>수정 내용</p>"))
            .andExpect(redirectedUrl("/article/group-orders/5"));
        verify(links).replaceSelected(any(Article.class),isNull());
        mvc.perform(post("/article/group-orders/5/like")).andExpect(content().string("false"));
        verify(articles,never()).toggleLike(anyLong(),anyString());
        when(files.findFileIdsByArticleId(5L)).thenReturn(List.of(9L));
        mvc.perform(post("/article/group-orders/5/delete")).andExpect(redirectedUrl("/article/group-orders"));
        verify(files).deleteFileById(9L);verify(articles).delete(5L);
    }
}
