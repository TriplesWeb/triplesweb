package com.example.triplesweb.controller;

import com.example.triplesweb.config.MustacheConfig;
import com.example.triplesweb.entity.Archive;
import com.example.triplesweb.entity.Artist;
import com.example.triplesweb.entity.MediaType;
import com.example.triplesweb.entity.SavedMeta;
import com.example.triplesweb.repository.ArtistRepository;
import com.example.triplesweb.service.AboutAdminAuth;
import com.example.triplesweb.service.ArchiveService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;

@WebMvcTest(ArchiveController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(MustacheConfig.class)
class ArchiveControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean ArchiveService archiveService;
    @MockitoBean AboutAdminAuth aboutAdminAuth;
    @MockitoBean ArtistRepository artistRepository;

    @Test void adminImageArchiveShowsEditAndDelete() throws Exception {
        when(aboutAdminAuth.isAdmin(any(), any())).thenReturn(true);
        when(artistRepository.findAll()).thenReturn(List.of());
        when(archiveService.pageByType(eq(MediaType.IMAGE), anyInt(), anyInt(), isNull())).thenReturn(new PageImpl<>(List.of(photo())));

        mvc.perform(get("/archive/image_archive")).andExpect(status().isOk())
                .andExpect(content().string(containsString("포토")))
                .andExpect(content().string(containsString("사진 아카이브")))
                .andExpect(content().string(containsString("data-archive-edit")))
                .andExpect(content().string(containsString("data-archive-delete")))
                .andExpect(content().string(containsString("archiveEditForm")))
                .andExpect(content().string(containsString("사진 추가")))
                .andExpect(content().string(containsString("멤버 선택")))
                .andExpect(content().string(containsString("id=\"uploadModal\" hidden")))
                .andExpect(content().string(containsString("archive-upload-picker")))
                .andExpect(content().string(containsString("data-url=\"/uploads/a.jpg\"")))
                .andExpect(content().string(containsString("목록에서 보이는 모습")))
                .andExpect(content().string(not(containsString("14.8M"))))
                .andExpect(content().string(not(containsString("Polygon"))));
    }

    @Test void guestImageArchiveHidesEditAndDelete() throws Exception {
        when(aboutAdminAuth.isAdmin(any(), any())).thenReturn(false);
        when(artistRepository.findAll()).thenReturn(List.of());
        when(archiveService.pageByType(eq(MediaType.IMAGE), anyInt(), anyInt(), isNull())).thenReturn(new PageImpl<>(List.of(photo())));

        mvc.perform(get("/archive/image_archive")).andExpect(status().isOk())
                .andExpect(content().string(containsString("포토")))
                .andExpect(content().string(containsString("data-admin-only")))
                .andExpect(content().string(containsString("data-archive-edit")))
                .andExpect(content().string(containsString("사진 추가")));
    }

    @Test void videoArchiveUsesSameLiveFields() throws Exception {
        when(aboutAdminAuth.isAdmin(any(), any())).thenReturn(false);
        when(artistRepository.findAll()).thenReturn(List.of());
        Archive video = photo();
        video.setTitle("무대영상");
        video.setThumbnailUrl("/uploads/poster.webp");
        video.setFile(SavedMeta.builder().url("/uploads/a.mp4").mediaType(MediaType.VIDEO).build());
        when(archiveService.pageByType(eq(MediaType.VIDEO), anyInt(), anyInt(), isNull())).thenReturn(new PageImpl<>(List.of(video)));

        mvc.perform(get("/archive/video_archive")).andExpect(status().isOk())
                .andExpect(content().string(containsString("data-poster=\"/uploads/poster.webp\"")))
                .andExpect(content().string(containsString("src=\"/uploads/poster.webp\"")))
                .andExpect(content().string(containsString("data-archive-video")))
                .andExpect(content().string(containsString("id=\"archiveVideoPlayer\"")))
                .andExpect(content().string(not(containsString("<video controls preload=\"metadata\""))))
                .andExpect(content().string(containsString("name=\"thumbnail\"")))
                .andExpect(content().string(containsString("무대영상")))
                .andExpect(content().string(containsString("영상 아카이브")))
                .andExpect(content().string(containsString("data-admin-only")))
                .andExpect(content().string(containsString("영상 추가")))
                .andExpect(content().string(containsString("archive-upload-picker")))
                .andExpect(content().string(containsString("id=\"editModal\" hidden")))
                .andExpect(content().string(containsString("data-url=\"/uploads/a.mp4\"")));
    }

    @Test void adminCanUpdateAndDelete() throws Exception {
        when(archiveService.delete(7L)).thenReturn(true);
        MockMultipartFile file = new MockMultipartFile("file", "a.jpg", "image/jpeg", new byte[]{1, 2});

        mvc.perform(multipart("/archive/7").file(file).param("title", "새 제목").param("artistNames", "서연"))
                .andExpect(status().isOk());
        verify(archiveService).update(eq(7L), eq("새 제목"), any(), eq("서연"), isNull(), eq(false));
        verify(aboutAdminAuth).requireAdmin(any(), any());

        mvc.perform(delete("/archive/7")).andExpect(status().isOk());
        verify(archiveService).delete(7L);
    }

    @Test void videoUploadAcceptsOptionalThumbnail() throws Exception {
        var video = new MockMultipartFile("file", "video.mp4", "video/mp4", new byte[]{1});
        var thumbnail = new MockMultipartFile("thumbnail", "poster.jfif", "application/octet-stream", new byte[]{2});
        mvc.perform(multipart("/archive/upload/video").file(video).file(thumbnail)
                        .param("title", "영상").param("artistNames", "서연"))
                .andExpect(status().is3xxRedirection());
        verify(archiveService).uploadVideoOnly(any(), eq("영상"), eq(MediaType.VIDEO), eq("서연"), any());
        verify(aboutAdminAuth).requireAdmin(any(), any());
    }

    @ParameterizedTest
    @EnumSource(value = MediaType.class, names = {"IMAGE", "VIDEO"})
    void archivesUseDisplayNumbersAndIndividualMemberColors(MediaType type) throws Exception {
        var member = new Artist();
        member.setArtistId(24L);
        member.setName("지연");
        member.setColor("#ffab62");
        when(artistRepository.findAll()).thenReturn(List.of(member));
        Archive first = photo(), second = photo();
        first.setId(3L);
        first.setArtistNames("지연, 게스트");
        second.setId(99L);
        when(archiveService.pageByType(eq(type), anyInt(), anyInt(), isNull()))
                .thenReturn(new PageImpl<>(List.of(first, second), PageRequest.of(0, 9), 2));
        mvc.perform(get(type == MediaType.IMAGE ? "/archive/image_archive" : "/archive/video_archive"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("data-archive-number>1</span>")))
                .andExpect(content().string(containsString("data-archive-number>2</span>")))
                .andExpect(content().string(containsString("--member-color: #ffab62;")))
                .andExpect(content().string(containsString("--member-color: #b9cac4;")))
                .andExpect(content().string(containsString("data-id=\"99\"")))
                .andExpect(content().string(not(containsString(">#99</span>"))));
    }

    @Test void displayNumbersContinueAcrossPagesWithoutUsingDatabaseIds() throws Exception {
        when(artistRepository.findAll()).thenReturn(List.of());
        when(archiveService.pageByType(eq(MediaType.IMAGE), anyInt(), anyInt(), isNull()))
                .thenReturn(new PageImpl<>(List.of(photo()), PageRequest.of(1, 9), 20));
        mvc.perform(get("/archive/image_archive").param("page", "1"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("data-number-offset=\"9\"")))
                .andExpect(content().string(containsString("data-archive-number>10</span>")));
    }

    @Test void invalidMemberColorUsesReadableNeutralBadge() throws Exception {
        var member = new Artist();
        member.setName("서연");
        member.setColor("red;position:fixed");
        when(artistRepository.findAll()).thenReturn(List.of(member));
        when(archiveService.pageByType(eq(MediaType.IMAGE), anyInt(), anyInt(), isNull()))
                .thenReturn(new PageImpl<>(List.of(photo())));
        mvc.perform(get("/archive/image_archive"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("--member-color: #b9cac4;")))
                .andExpect(content().string(not(containsString("--member-color: red;position:fixed"))));
    }

    private static Archive photo() {
        Archive archive = new Archive();
        archive.setId(7L);
        archive.setTitle("포토");
        archive.setArtistNames("서연");
        archive.setFile(SavedMeta.builder().url("/uploads/a.jpg").mediaType(MediaType.IMAGE).build());
        return archive;
    }
}
