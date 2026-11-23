package com.example.triplesweb.controller;

import com.example.triplesweb.entity.ArchiveSource;
import com.example.triplesweb.service.AboutAdminAuth;
import com.example.triplesweb.service.ArchiveService;
import com.example.triplesweb.service.ArchiveSourceService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ArchiveBulkApiController.class)
@AutoConfigureMockMvc(addFilters = false)
class ArchiveBulkApiControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean ArchiveService archiveService;
    @MockitoBean ArchiveSourceService archiveSourceService;
    @MockitoBean AboutAdminAuth aboutAdminAuth;

    @Test
    void bulkUpdateSourceRequiresAdminAndUpdates() throws Exception {
        ArchiveSource source = new ArchiveSource();
        source.setCode("OFFICIAL_SNS");
        when(archiveSourceService.requireExisting("OFFICIAL_SNS")).thenReturn(source);
        when(archiveService.bulkUpdateSource(eq(List.of(1L, 2L)), eq("OFFICIAL_SNS"))).thenReturn(2);

        mvc.perform(post("/api/archive/bulk-source")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"ids":[1,2],"source":"OFFICIAL_SNS"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.updated").value(2));

        verify(aboutAdminAuth).requireAdmin(any(), any());
        verify(archiveService).bulkUpdateSource(List.of(1L, 2L), "OFFICIAL_SNS");
    }

    @Test
    void bulkUpdateSourceRejectsEmptyIds() throws Exception {
        mvc.perform(post("/api/archive/bulk-source")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"ids":[],"source":"OFFICIAL_SNS"}
                                """))
                .andExpect(status().isBadRequest());

        verify(aboutAdminAuth).requireAdmin(any(), any());
        verify(archiveService, never()).bulkUpdateSource(anyList(), anyString());
    }

    @Test
    void bulkUpdateSourceRejectsUnknownSource() throws Exception {
        when(archiveSourceService.requireExisting("MISSING"))
                .thenThrow(new IllegalArgumentException("출처를 선택하세요."));

        mvc.perform(post("/api/archive/bulk-source")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"ids":[1],"source":"MISSING"}
                                """))
                .andExpect(status().isBadRequest());

        verify(aboutAdminAuth).requireAdmin(any(), any());
        verify(archiveService, never()).bulkUpdateSource(anyList(), anyString());
    }

    @Test
    void bulkUpdateSourcePropagatesAdminDenial() throws Exception {
        doThrow(new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.FORBIDDEN, "관리자 세션이 필요합니다."))
                .when(aboutAdminAuth).requireAdmin(any(), any());

        mvc.perform(post("/api/archive/bulk-source")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"ids":[1],"source":"OFFICIAL_SNS"}
                                """))
                .andExpect(status().isForbidden());

        verify(archiveService, never()).bulkUpdateSource(anyList(), anyString());
    }
}
