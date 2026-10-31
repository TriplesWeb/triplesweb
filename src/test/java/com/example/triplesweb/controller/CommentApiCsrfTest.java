package com.example.triplesweb.controller;

import com.example.triplesweb.SecurityConfig;
import com.example.triplesweb.dto.CommentDto;
import com.example.triplesweb.service.CommentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = CommentApiController.class)
@Import(SecurityConfig.class)
class CommentApiCsrfTest {
    @Autowired MockMvc mvc;
    @MockitoBean CommentService comments;

    @Test void createWithoutCsrfIsForbidden() throws Exception {
        mvc.perform(post("/api/articles/9/comments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\":\"hi\",\"userId\":\"익명\"}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(comments);
    }

    @Test void createWithValidCsrfSucceeds() throws Exception {
        CommentDto saved = new CommentDto();
        saved.setId(1L);
        saved.setArticleId(9L);
        saved.setBody("hi");
        saved.setUserId("익명");
        when(comments.create(eq(9L), any(CommentDto.class))).thenReturn(saved);

        mvc.perform(post("/api/articles/9/comments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\":\"hi\",\"userId\":\"익명\"}")
                        .with(csrf()))
                .andExpect(status().isOk());
        verify(comments).create(eq(9L), any(CommentDto.class));
    }

    @Test void updateAndDeleteRequireCsrf() throws Exception {
        mvc.perform(put("/api/articles/9/comments/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":1,\"body\":\"edit\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/articles/9/comments/1"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(comments);

        CommentDto updated = new CommentDto();
        updated.setId(1L);
        updated.setBody("edit");
        when(comments.update(eq(1L), any(CommentDto.class))).thenReturn(updated);
        when(comments.delete(1L)).thenReturn(updated);

        mvc.perform(put("/api/articles/9/comments/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":1,\"body\":\"edit\"}")
                        .with(csrf()))
                .andExpect(status().isOk());
        mvc.perform(delete("/api/articles/9/comments/1").with(csrf()))
                .andExpect(status().isOk());
        verify(comments).update(eq(1L), any(CommentDto.class));
        verify(comments).delete(1L);
    }
}
