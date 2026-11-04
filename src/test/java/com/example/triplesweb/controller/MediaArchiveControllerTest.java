package com.example.triplesweb.controller;

import com.example.triplesweb.config.MustacheConfig;
import com.example.triplesweb.repository.ArtistRepository;
import com.example.triplesweb.repository.ScheduleRepository;
import com.example.triplesweb.service.AboutAdminAuth;
import com.example.triplesweb.service.MediaService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MediaArchiveController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(MustacheConfig.class)
class MediaArchiveControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean MediaService media;
    @MockitoBean ArtistRepository artistRepository;
    @MockitoBean ScheduleRepository scheduleRepository;
    @MockitoBean AboutAdminAuth aboutAdminAuth;

    @Test
    void legacyMediaQueryRedirectEncodesDecodedArtistName() throws Exception {
        mvc.perform(get("/media").param("tab", "signal").param("artist", "서연"))
            .andExpect(status().isFound())
            .andExpect(header().string("Location", containsString("/media/signal")))
            .andExpect(header().string("Location", containsString("tab=signal")))
            .andExpect(header().string("Location", containsString("artist=%EC%84%9C%EC%97%B0")));
    }
}
