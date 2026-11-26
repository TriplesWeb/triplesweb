package com.example.triplesweb.controller;

import com.example.triplesweb.service.AboutAdminAuth;
import com.example.triplesweb.service.MediaChannelSyncService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(value = MediaYoutubeSyncController.class, properties = "chemistry.admin.password=test-password")
@AutoConfigureMockMvc(addFilters = false)
@Import({AboutAdminAuth.class, com.example.triplesweb.config.PasswordConfig.class})
class MediaYoutubeSyncControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean MediaChannelSyncService sync;

    @Test
    void anonymousCanReadSearchStatusAndKickSearch() throws Exception {
        when(sync.externalSearchReady()).thenReturn(false);
        when(sync.externalSearchRunning()).thenReturn(true);
        mvc.perform(get("/api/media/youtube/search-status"))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"ready\":false,\"running\":true}", true));
        verify(sync).ensureExternalSearch();
    }

    @Test
    void anonymousCannotSync() throws Exception {
        mvc.perform(post("/api/media/youtube/sync")).andExpect(status().isForbidden());
        verifyNoInteractions(sync);
    }

    @Test
    void adminReceivesCounts() throws Exception {
        when(sync.syncFull()).thenReturn(new MediaChannelSyncService.Result(10, 4, 1, 5));
        mvc.perform(post("/api/media/youtube/sync").sessionAttr(AboutAdminAuth.SESSION_ADMIN, true))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"scanned\":10,\"inserted\":4,\"updated\":1,\"skipped\":5}", true));
    }
}
