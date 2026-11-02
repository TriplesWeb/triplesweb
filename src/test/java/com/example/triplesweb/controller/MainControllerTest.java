package com.example.triplesweb.controller;

import com.example.triplesweb.config.MustacheConfig;
import com.example.triplesweb.entity.Album;
import com.example.triplesweb.entity.Article;
import com.example.triplesweb.entity.MediaType;
import com.example.triplesweb.repository.AlbumRepository;
import com.example.triplesweb.repository.ScheduleRepository;
import com.example.triplesweb.service.ArchiveService;
import com.example.triplesweb.service.ArticleServiceImpl;
import com.example.triplesweb.service.ArtistSnapshotService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import java.util.List;
import java.util.Optional;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(MainController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(MustacheConfig.class)
class MainControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean ArticleServiceImpl articles;
    @MockitoBean ScheduleRepository schedules;
    @MockitoBean ArchiveService archives;
    @MockitoBean AlbumRepository albums;
    @MockitoBean ArtistSnapshotService artists;

    @Test void homeRendersNoticeAlbumAndSplitArchivesWithoutChangingAboutLinks() throws Exception {
        Article notice = new Article();
        notice.setId(3L);
        notice.setTitle("점검 안내");
        notice.setCategory("notice");
        notice.setUserId("admin");
        when(articles.findRecentByCategory("notice", 5)).thenReturn(List.of(notice));
        when(schedules.findAll()).thenReturn(List.of());
        when(artists.findAll()).thenReturn(List.of());
        when(archives.pageByType(eq(MediaType.IMAGE), eq(0), eq(4), isNull())).thenReturn(new PageImpl<>(List.of()));
        when(archives.pageByType(eq(MediaType.VIDEO), eq(0), eq(4), isNull())).thenReturn(new PageImpl<>(List.of()));
        Album album = new Album();
        album.setAlbumId(11L);
        album.setAlbumName("LOVE & POP Pt.2");
        when(albums.findByAlbumName("LOVE & POP Pt.2")).thenReturn(Optional.of(album));

        mvc.perform(get("/")).andExpect(status().isOk())
                .andExpect(content().string(containsString("공지사항")))
                .andExpect(content().string(containsString("/article/notice/3")))
                .andExpect(content().string(containsString("/triples/album/11")))
                .andExpect(content().string(containsString("영상 아카이브")))
                .andExpect(content().string(containsString("/archive/video_archive")))
                .andExpect(content().string(containsString("Who is tripleS?")))
                .andExpect(content().string(containsString("href=\"/triples/artist\"")))
                .andExpect(content().string(containsString("href=\"/artist\"")));
    }
}
