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
                .andExpect(content().string(containsString("id=\"archiveDetail\"")))
                .andExpect(content().string(containsString("더보기")))
                .andExpect(content().string(containsString("/archive/image_archive/all")))
                .andExpect(content().string(containsString(">날짜<")))
                .andExpect(content().string(not(containsString("등록된 사진과 출연 표기를 확인하세요."))))
                .andExpect(content().string(not(containsString("원본 보기 ↗"))))
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

        mvc.perform(multipart("/archive/7").file(file).param("title", "새 제목").param("artistNames", "서연").param("registeredOn", "2026-10-01"))
                .andExpect(status().isOk());
        verify(archiveService).update(eq(7L), eq("새 제목"), any(), eq("서연"), isNull(), eq(false), eq(java.time.LocalDate.of(2026, 10, 1)));
        verify(aboutAdminAuth).requireAdmin(any(), any());

        mvc.perform(delete("/archive/7")).andExpect(status().isOk());
        verify(archiveService).delete(7L);
    }

    @Test void videoUploadAcceptsOptionalThumbnail() throws Exception {
        var video = new MockMultipartFile("file", "video.mp4", "video/mp4", new byte[]{1});
        var thumbnail = new MockMultipartFile("thumbnail", "poster.jfif", "application/octet-stream", new byte[]{2});
        mvc.perform(multipart("/archive/upload/video").file(video).file(thumbnail)
                        .param("title", "영상").param("artistNames", "서연").param("registeredOn", "2026-10-02"))
                .andExpect(status().is3xxRedirection());
        verify(archiveService).uploadVideoOnly(any(), eq("영상"), eq(MediaType.VIDEO), eq("서연"), any(), eq(java.time.LocalDate.of(2026, 10, 2)));
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

    @Test void recentHighlightsCollapseManyMembersToFirstPlusCount() throws Exception {
        when(artistRepository.findAll()).thenReturn(List.of());
        Archive group = photo();
        group.setArtistNames("윤서연, 윤서연, 정혜린, 이지우, <김채연>");
        Archive solo = photo();
        solo.setId(8L);
        solo.setArtistNames("서연 \"A\"");
        when(archiveService.pageByType(eq(MediaType.IMAGE), anyInt(), anyInt(), isNull()))
                .thenReturn(new PageImpl<>(List.of(group, solo)));
        String html = mvc.perform(get("/archive/image_archive"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("외 3명")))
                .andExpect(content().string(containsString("윤서연")))
                .andReturn().getResponse().getContentAsString();
        int trackAt = html.indexOf("id=\"archiveTrack\"");
        String recent = html.substring(trackAt, html.indexOf("</section>", trackAt));
        int soloAt = recent.indexOf("data-archive-ref=\"8\"");
        String many = recent.substring(0, soloAt);
        String one = recent.substring(soloAt);
        String preview = many.substring(0, many.indexOf("ar-card-members"));
        org.assertj.core.api.Assertions.assertThat(preview).contains("archive-member-more");
        org.assertj.core.api.Assertions.assertThat(preview).contains("<span>윤서연</span>");
        org.assertj.core.api.Assertions.assertThat(preview).doesNotContain("<span>정혜린</span>");
        org.assertj.core.api.Assertions.assertThat(many).contains("title=\"윤서연, 정혜린, 이지우, &lt;김채연&gt;\"");
        org.assertj.core.api.Assertions.assertThat(many).contains("archive-member-rest\">정혜린, 이지우, &lt;김채연&gt;");
        org.assertj.core.api.Assertions.assertThat(many).doesNotContain("외 4명");
        org.assertj.core.api.Assertions.assertThat(one).doesNotContain("archive-member-more");
        org.assertj.core.api.Assertions.assertThat(one).contains("title=\"서연 &quot;A&quot;\"");
        org.assertj.core.api.Assertions.assertThat(html).contains("<span>정혜린</span>");
        org.assertj.core.api.Assertions.assertThat(html).doesNotContain("외 0명");
    }

    @Test void recentHighlightsStayFiveNewestFromTheLeft() throws Exception {
        when(artistRepository.findAll()).thenReturn(List.of());
        Archive first = photo(); first.setId(50L); first.setTitle("최신");
        Archive second = photo(); second.setId(40L); second.setTitle("둘째");
        Archive third = photo(); third.setId(30L); third.setTitle("셋째");
        Archive fourth = photo(); fourth.setId(20L); fourth.setTitle("넷째");
        Archive fifth = photo(); fifth.setId(10L); fifth.setTitle("다섯째");
        Archive older = photo(); older.setId(1L); older.setTitle("이전");
        when(archiveService.pageByType(eq(MediaType.IMAGE), anyInt(), anyInt(), isNull())).thenAnswer(invocation -> {
            int size = invocation.getArgument(2);
            List<Archive> latest = List.of(first, second, third, fourth, fifth, older);
            if (size == 5) return new PageImpl<>(latest.subList(0, 5));
            return new PageImpl<>(List.of(older), PageRequest.of(1, 9), 6);
        });
        String html = mvc.perform(get("/archive/image_archive").param("page", "1"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        int trackAt = html.indexOf("id=\"archiveTrack\"");
        String recent = html.substring(trackAt, html.indexOf("</section>", trackAt));
        org.assertj.core.api.Assertions.assertThat(recent).contains("최신", "둘째", "셋째", "넷째", "다섯째");
        org.assertj.core.api.Assertions.assertThat(recent).doesNotContain("이전");
        org.assertj.core.api.Assertions.assertThat(recent.indexOf("최신")).isLessThan(recent.indexOf("다섯째"));
        org.assertj.core.api.Assertions.assertThat(recent).contains("data-archive-number>1</span>");
        org.assertj.core.api.Assertions.assertThat(recent).contains("data-archive-number>5</span>");
        org.assertj.core.api.Assertions.assertThat(html).contains("data-archive-number>10</span>");
    }

    @Test void searchStatShowsColoredMembersAndOtherFilters() throws Exception {
        var seoyeon = new Artist();
        seoyeon.setName("윤서연");
        seoyeon.setColor("#22aeff");
        when(artistRepository.findAll()).thenReturn(List.of(seoyeon));
        when(archiveService.pageByType(eq(MediaType.IMAGE), anyInt(), anyInt(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(photo())));
        when(archiveService.pageByType(eq(MediaType.IMAGE), anyInt(), anyInt(), isNull()))
                .thenReturn(new PageImpl<>(List.of()));
        String html = mvc.perform(get("/archive/image_archive")
                        .param("members", "윤서연")
                        .param("members", "지연")
                        .param("q", "심장")
                        .param("from", "2026-01-01")
                        .param("to", "2026-12-31"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        org.assertj.core.api.Assertions.assertThat(html).contains("ar-filter-member");
        org.assertj.core.api.Assertions.assertThat(html).contains("--member-color: #22aeff");
        org.assertj.core.api.Assertions.assertThat(html).contains("외 1명");
        org.assertj.core.api.Assertions.assertThat(html).contains("심장");
        org.assertj.core.api.Assertions.assertThat(html).contains("2026-01-01 ~ 2026-12-31");
        org.assertj.core.api.Assertions.assertThat(html).contains("--filter-count: 3");
    }

    @Test void photoBrowsePageListsAllPhotosWithoutCarousel() throws Exception {
        when(artistRepository.findAll()).thenReturn(List.of());
        Archive group = photo();
        group.setArtistNames("윤서연, 정혜린");
        when(archiveService.pageByType(eq(MediaType.IMAGE), anyInt(), anyInt(), isNull()))
                .thenReturn(new PageImpl<>(List.of(group), PageRequest.of(0, 24), 1));
        String html = mvc.perform(get("/archive/image_archive/all"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("사진 전체")))
                .andExpect(content().string(containsString("is-browse")))
                .andExpect(content().string(containsString("data-archive-open")))
                .andExpect(content().string(containsString("외 1명")))
                .andExpect(content().string(not(containsString("id=\"archiveTrack\""))))
                .andExpect(content().string(not(containsString("id=\"archiveGalleryControls\""))))
                .andReturn().getResponse().getContentAsString();
        String visible = html.substring(html.indexOf("id=\"archiveGrid\""), html.indexOf("ar-card-members"));
        org.assertj.core.api.Assertions.assertThat(visible).contains("윤서연");
        org.assertj.core.api.Assertions.assertThat(visible).doesNotContain("<span>정혜린</span>");
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
