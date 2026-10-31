package com.example.triplesweb.controller;

import com.example.triplesweb.SecurityConfig;
import com.example.triplesweb.entity.MediaType;
import com.example.triplesweb.entity.SavedMeta;
import com.example.triplesweb.service.FileStorageService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = ArticleUploadController.class)
@Import(SecurityConfig.class)
class ArticleUploadCsrfTest {
    @Autowired MockMvc mvc;
    @MockitoBean FileStorageService storage;

    private MockMultipartFile file() {
        return new MockMultipartFile("file", "triples.png", "image/png", new byte[]{1, 2, 3});
    }

    private SavedMeta meta() {
        return SavedMeta.builder()
                .originalName("triples.png")
                .savedName("images/trade/2026-10-31/uuid.png")
                .url("/uploads/images/trade/2026-10-31/uuid.png")
                .size(3)
                .fileType("image/png")
                .mediaType(MediaType.IMAGE)
                .build();
    }

    @Test void uploadWithoutCsrfIsForbidden() throws Exception {
        mvc.perform(multipart("/api/article/trade/uploadFile").file(file()))
                .andExpect(status().isForbidden());
        verifyNoInteractions(storage);
    }

    @Test void uploadWithValidCsrfSucceeds() throws Exception {
        when(storage.saveWithMeta(any(), eq("trade"))).thenReturn(meta());
        mvc.perform(multipart("/api/article/trade/uploadFile")
                        .file(file())
                        .with(csrf()))
                .andExpect(status().isOk());
        verify(storage).saveWithMeta(any(), eq("trade"));
    }
}
