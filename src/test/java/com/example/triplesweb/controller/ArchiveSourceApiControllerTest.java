package com.example.triplesweb.controller;

import com.example.triplesweb.dto.ArchiveSourceDto;
import com.example.triplesweb.service.AboutAdminAuth;
import com.example.triplesweb.service.ArchiveSourceService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ArchiveSourceApiController.class)
@AutoConfigureMockMvc(addFilters = false)
class ArchiveSourceApiControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean ArchiveSourceService archiveSourceService;
    @MockitoBean AboutAdminAuth aboutAdminAuth;

    @Test
    void listVisibleDoesNotRequireAdmin() throws Exception {
        when(archiveSourceService.listVisible()).thenReturn(List.of(
                new ArchiveSourceDto(1L, "COSMO_ROOM", "cosmo_room", "코스모룸", "#00f5d4", 10, true)));
        mvc.perform(get("/api/archive/sources"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].code").value("COSMO_ROOM"));
        verifyNoInteractions(aboutAdminAuth);
    }

    @Test
    void listAllRequiresAdmin() throws Exception {
        when(archiveSourceService.listAll()).thenReturn(List.of());
        mvc.perform(get("/api/archive/sources").param("all", "true"))
                .andExpect(status().isOk());
        verify(aboutAdminAuth).requireAdmin(any(), any());
    }

    @Test
    void createRequiresAdmin() throws Exception {
        when(archiveSourceService.create(eq("FAN_MEET"), eq("팬미팅"), eq("#abcdef"), isNull(), anyBoolean()))
                .thenReturn(new ArchiveSourceDto(9L, "FAN_MEET", "fan_meet", "팬미팅", "#abcdef", 80, true));
        mvc.perform(post("/api/archive/sources")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"FAN_MEET","label":"팬미팅","color":"#abcdef","visible":true}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("FAN_MEET"));
        verify(aboutAdminAuth).requireAdmin(any(), any());
    }

    @Test
    void updateRequiresAdmin() throws Exception {
        when(archiveSourceService.update(eq(3L), eq("공식"), eq("#111111"), eq(15), eq(false)))
                .thenReturn(new ArchiveSourceDto(3L, "OFFICIAL_SNS", "official_sns", "공식", "#111111", 15, false));
        mvc.perform(put("/api/archive/sources/3")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"label":"공식","color":"#111111","sortOrder":15,"visible":false}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.visible").value(false));
        verify(aboutAdminAuth).requireAdmin(any(), any());
    }

    @Test
    void deleteConflictWhenSourceIsUsed() throws Exception {
        doThrow(new ResponseStatusException(HttpStatus.CONFLICT, "사용 중"))
                .when(archiveSourceService).delete(anyLong());
        mvc.perform(delete("/api/archive/sources/1"))
                .andExpect(status().isConflict());
        verify(aboutAdminAuth).requireAdmin(any(), any());
    }
}
