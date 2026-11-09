package com.example.triplesweb.controller;

import com.example.triplesweb.config.MustacheConfig;
import com.example.triplesweb.dto.HomeAlbumHero;
import com.example.triplesweb.entity.Album;
import com.example.triplesweb.entity.MediaType;
import com.example.triplesweb.repository.ScheduleRepository;
import com.example.triplesweb.service.AlbumService;
import com.example.triplesweb.service.ArchiveService;
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
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(MainController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(MustacheConfig.class)
class MainControllerTest {
    @Autowired MockMvc mvc;
    @Autowired MainController controller;
    @MockitoBean ScheduleRepository schedules;
    @MockitoBean ArchiveService archives;
    @MockitoBean AlbumService albumService;
    @MockitoBean ArtistSnapshotService artists;

    @Test void homeRendersAlbumAndSplitArchivesWithoutNoticeOrChangingAboutLinks() throws Exception {
        when(schedules.findAllWithArtists()).thenReturn(List.of());
        when(artists.findAll()).thenReturn(List.of());
        when(archives.pageByType(eq(MediaType.IMAGE), eq(0), eq(4), isNull())).thenReturn(new PageImpl<>(List.of()));
        when(archives.pageByType(eq(MediaType.VIDEO), eq(0), eq(4), isNull())).thenReturn(new PageImpl<>(List.of()));
        Album album = new Album();
        album.setAlbumId(14L);
        album.setAlbumName("LOVE&POP pt.2");
        album.setAlbumImage("/uploads/images/album/cover.webp");
        album.setAlbumInfo("끝나지 않을 영원한 트리플에스의 스토리");
        when(albumService.findLatestHomeHero()).thenReturn(HomeAlbumHero.from(album));

        controller.refreshHomeSnapshot();
        clearInvocations(schedules, archives, albumService, artists);
        mvc.perform(get("/")).andExpect(status().isOk())
                .andExpect(content().string(not(containsString("공지사항"))))
                .andExpect(content().string(containsString("스케줄")))
                .andExpect(content().string(containsString("다가오는 일정")))
                .andExpect(content().string(containsString("등록된 일정이 없습니다")))
                .andExpect(content().string(containsString("/triples/album/14")))
                .andExpect(content().string(containsString("LOVE&amp;POP pt.2")))
                .andExpect(content().string(containsString("앨범 자세히 보기")))
                .andExpect(content().string(containsString("컴백")))
                .andExpect(content().string(containsString("/uploads/images/album/cover.webp")))
                .andExpect(content().string(containsString("영상 아카이브")))
                .andExpect(content().string(containsString("/archive/video_archive")))
                .andExpect(content().string(containsString("Who is tripleS?")))
                .andExpect(content().string(containsString("href=\"/triples/artist\"")))
                .andExpect(content().string(containsString("href=\"/artist\"")))
                .andExpect(content().string(containsString("header-objekt-jump")));
        verifyNoInteractions(schedules, archives, albumService, artists);
    }
}
