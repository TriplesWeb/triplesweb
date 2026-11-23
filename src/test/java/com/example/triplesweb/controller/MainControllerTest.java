package com.example.triplesweb.controller;

import com.example.triplesweb.config.MustacheConfig;
import com.example.triplesweb.dto.HomeAlbumHero;
import com.example.triplesweb.entity.Album;
import com.example.triplesweb.entity.Archive;
import com.example.triplesweb.entity.MediaType;
import com.example.triplesweb.entity.SavedMeta;
import com.example.triplesweb.entity.Schedule;
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
import java.time.LocalDate;
import java.util.List;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.verify;
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
        LocalDate first = LocalDate.now().withDayOfMonth(1);
        Schedule monthly = new Schedule("달력 일정", first, null);
        when(schedules.findOverlapping(first, first.plusMonths(1), null)).thenReturn(List.of(monthly));
        when(artists.findAll()).thenReturn(List.of());
        Archive localPhoto = new Archive("최근 사진", SavedMeta.builder().url("/uploads/images/archive/photo.jpg").build());
        Archive remotePhoto = new Archive("외부 사진", SavedMeta.builder().url("https://example.com/photo.jpg").build());
        when(archives.pageByType(eq(MediaType.IMAGE), eq(0), eq(4), isNull()))
                .thenReturn(new PageImpl<>(List.of(localPhoto, remotePhoto)));
        when(archives.pageByType(eq(MediaType.VIDEO), eq(0), eq(4), isNull())).thenReturn(new PageImpl<>(List.of()));
        Album album = new Album();
        album.setAlbumId(14L);
        album.setAlbumName("LOVE&POP pt.2");
        album.setAlbumImage("/uploads/images/album/cover.webp");
        album.setAlbumInfo("끝나지 않을 영원한 트리플에스의 스토리");
        when(albumService.findLatestHomeHero()).thenReturn(HomeAlbumHero.from(album));

        clearInvocations(schedules);
        controller.refreshHomeSnapshot();
        verify(schedules).findOverlapping(first, first.plusMonths(1), null);
        clearInvocations(schedules, archives, albumService, artists);
        mvc.perform(get("/")).andExpect(status().isOk())
                .andExpect(content().string(not(containsString("공지사항"))))
                .andExpect(content().string(containsString("스케줄")))
                .andExpect(content().string(containsString("다가오는 일정")))
                .andExpect(content().string(containsString("등록된 일정이 없습니다")))
                .andExpect(content().string(containsString("\"start\":\"" + first + "\"")))
                .andExpect(content().string(containsString("\"title\":\"달력 일정\"")))
                .andExpect(content().string(not(containsString("\"rawEnd\""))))
                .andExpect(content().string(containsString("/triples/album/14")))
                .andExpect(content().string(containsString("LOVE&amp;POP pt.2")))
                .andExpect(content().string(containsString("앨범 자세히 보기")))
                .andExpect(content().string(containsString("컴백")))
                .andExpect(content().string(containsString("/uploads/images/album/cover.webp")))
                .andExpect(content().string(containsString("아카이브")))
                .andExpect(content().string(containsString("src=\"/uploads/images/archive/photo.jpg?w&#x3D;480\"")))
                .andExpect(content().string(not(containsString("src=\"/uploads/images/archive/photo.jpg\""))))
                .andExpect(content().string(containsString("src=\"https://example.com/photo.jpg\"")))
                .andExpect(content().string(containsString("/archive?type=video")))
                .andExpect(content().string(containsString("Who is tripleS?")))
                .andExpect(content().string(containsString("href=\"/triples/artist\"")))
                .andExpect(content().string(containsString("href=\"/artist\"")))
                .andExpect(content().string(containsString("header-objekt-jump")));
        verifyNoInteractions(schedules, archives, albumService, artists);
    }
}
