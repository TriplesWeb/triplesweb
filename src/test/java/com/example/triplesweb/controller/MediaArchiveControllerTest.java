package com.example.triplesweb.controller;

import com.example.triplesweb.config.MustacheConfig;
import com.example.triplesweb.dto.MediaDto;
import com.example.triplesweb.repository.ArtistRepository;
import com.example.triplesweb.repository.ScheduleRepository;
import com.example.triplesweb.service.AboutAdminAuth;
import com.example.triplesweb.service.MediaService;
import com.example.triplesweb.service.MediaViewCountService;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MediaArchiveController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(MustacheConfig.class)
class MediaArchiveControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean MediaService media;
    @MockitoBean MediaViewCountService mediaViewCounts;
    @MockitoBean ArtistRepository artistRepository;
    @MockitoBean ScheduleRepository scheduleRepository;
    @MockitoBean AboutAdminAuth aboutAdminAuth;

    @BeforeEach
    void stubs() {
        lenient().when(artistRepository.findAll()).thenReturn(List.of());
        lenient().when(aboutAdminAuth.isAdmin(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(false);
        List<MediaDto> videos = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            videos.add(video(i, i < 5 ? "0:40" : "3:11"));
        }
        when(media.listAll()).thenReturn(videos);
    }

    @Test
    void pagesArchiveAndLimitsVisibleCards() throws Exception {
        String first = mvc.perform(get("/media"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("@triplescosmos")))
                .andExpect(content().string(containsString("페이지 1 / 2")))
                .andExpect(content().string(containsString("쇼츠만 보기")))
                .andReturn().getResponse().getContentAsString();
        assertThat(Jsoup.parse(first).select("article.media-card")).hasSize(24);

        String second = mvc.perform(get("/media").param("page", "2"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("페이지 2 / 2")))
                .andReturn().getResponse().getContentAsString();
        assertThat(Jsoup.parse(second).select("article.media-card")).hasSize(6);
    }

    @Test
    void shortsKindKeepsOnlyShortVideos() throws Exception {
        String html = mvc.perform(get("/media").param("kind", "shorts"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("전체 보기")))
                .andExpect(content().string(not(containsString("페이지 1 / 2"))))
                .andReturn().getResponse().getContentAsString();
        assertThat(Jsoup.parse(html).select("article.media-card")).hasSize(5);
        assertThat(Jsoup.parse(html).select(".media-short")).hasSize(5);
    }

    private static MediaDto video(int index, String duration) {
        String id = String.format("%011d", index);
        return MediaDto.builder()
                .id((long) index + 1)
                .tab("contents")
                .youtubeId(id)
                .title("영상 " + index)
                .duration(duration)
                .views(10L)
                .viewCountFmt("10")
                .publishedOn("2024-05-06")
                .build();
    }
}
