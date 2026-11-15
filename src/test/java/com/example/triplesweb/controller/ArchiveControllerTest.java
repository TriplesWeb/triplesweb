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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
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

        mvc.perform(get("/archive")).andExpect(status().isOk())
                .andExpect(content().string(containsString("포토")))
                .andExpect(content().string(containsString("<h1>아카이브</h1>")))
                .andExpect(content().string(containsString("site-section-tabs")))
                .andExpect(content().string(containsString("사진 날짜")))
                .andExpect(content().string(containsString("실제로 올라온 날짜를 선택하세요.")))
                .andExpect(content().string(containsString("S1.png")))
                .andExpect(content().string(containsString("id=\"archive-member-picker\"")))
                .andExpect(content().string(not(containsString("archive-symbol-picker"))))
                .andExpect(content().string(containsString("archive-symbol-chip")))
                .andExpect(content().string(containsString("data-archive-edit")))
                .andExpect(content().string(containsString("data-archive-delete")))
                .andExpect(content().string(containsString("archiveEditForm")))
                .andExpect(content().string(containsString("사진 추가")))
                .andExpect(content().string(containsString("멤버 선택")))
                .andExpect(content().string(containsString("id=\"uploadModal\" hidden")))
                .andExpect(content().string(containsString("archive-upload-picker")))
                .andExpect(content().string(containsString("data-upload-url=\"/archive/upload/image\"")))
                .andExpect(content().string(containsString("data-url=\"/uploads/a.jpg\"")))
                .andExpect(content().string(containsString("목록에서 보이는 모습")))
                .andExpect(content().string(containsString("id=\"archiveDetail\"")))
                .andExpect(content().string(containsString("더보기")))
                .andExpect(content().string(containsString("/archive/all")))
                .andExpect(content().string(containsString(">검색</button>")))
                .andExpect(content().string(containsString("data-stats-toggle")))
                .andExpect(content().string(containsString("name=\"go\"")))
                .andExpect(content().string(containsString("이 사진 날짜")))
                .andExpect(content().string(containsString("실제로 올라온 날짜로 찾습니다.")))
                .andExpect(content().string(not(containsString("id=\"archiveTrack\""))))
                .andExpect(content().string(not(containsString("최근 사진"))))
                .andExpect(content().string(not(containsString("id=\"archiveGalleryControls\""))))
                .andExpect(content().string(not(containsString("archive-photo-carousel"))))
                .andExpect(content().string(not(containsString("사진 아카이브"))))
                .andExpect(content().string(not(containsString("등록된 사진과 출연 표기를 확인하세요."))))
                .andExpect(content().string(not(containsString("원본 보기 ↗"))))
                .andExpect(content().string(not(containsString("14.8M"))))
                .andExpect(content().string(not(containsString("Polygon"))));
    }

    @Test void archiveMemberPickerUsesPortraitsNotSymbolLogos() throws Exception {
        var member = new Artist();
        member.setName("윤서연");
        member.setEngName("SeoYeon");
        member.setColor("#22aeff");
        member.setProfileImage("/uploads/portraits/seoyeon.jpg");
        when(artistRepository.findAll()).thenReturn(List.of(member));
        when(archiveService.pageByType(eq(MediaType.IMAGE), anyInt(), anyInt(), isNull()))
                .thenReturn(new PageImpl<>(List.of(photo())));
        String html = mvc.perform(get("/archive")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        int start = html.indexOf("id=\"archive-member-picker\"");
        int end = html.indexOf("</div>", start);
        String picker = html.substring(start, end);
        org.assertj.core.api.Assertions.assertThat(picker).contains("/uploads/portraits/seoyeon.jpg");
        org.assertj.core.api.Assertions.assertThat(picker).doesNotContain("S1.png");
        org.assertj.core.api.Assertions.assertThat(html).contains("S1.png");
        org.assertj.core.api.Assertions.assertThat(html).doesNotContain("archive-symbol-picker");
    }

    @Test void guestImageArchiveHidesEditAndDelete() throws Exception {
        when(aboutAdminAuth.isAdmin(any(), any())).thenReturn(false);
        when(artistRepository.findAll()).thenReturn(List.of());
        when(archiveService.pageByType(eq(MediaType.IMAGE), anyInt(), anyInt(), isNull())).thenReturn(new PageImpl<>(List.of(photo())));

        mvc.perform(get("/archive")).andExpect(status().isOk())
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

        mvc.perform(get("/archive").param("type", "video")).andExpect(status().isOk())
                .andExpect(content().string(containsString("data-poster=\"/uploads/poster.webp\"")))
                .andExpect(content().string(containsString("src=\"/uploads/poster.webp\"")))
                .andExpect(content().string(containsString("data-archive-video")))
                .andExpect(content().string(containsString("id=\"archiveVideoPlayer\"")))
                .andExpect(content().string(not(containsString("<video controls preload=\"metadata\""))))
                .andExpect(content().string(containsString("name=\"thumbnail\"")))
                .andExpect(content().string(containsString("무대영상")))
                .andExpect(content().string(containsString("<h1>아카이브</h1>")))
                .andExpect(content().string(containsString("영상 날짜")))
                .andExpect(content().string(containsString("data-upload-url=\"/archive/upload/video\"")))
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
                .andExpect(redirectedUrl("/archive?type=video"));
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
        mvc.perform(get(type == MediaType.IMAGE ? "/archive" : "/archive").param("type", type == MediaType.IMAGE ? "image" : "video"))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("data-archive-number"))))
                .andExpect(content().string(containsString("--member-color: #ffab62;")))
                .andExpect(content().string(containsString("--member-color: #b9cac4;")))
                .andExpect(content().string(containsString("data-id=\"99\"")))
                .andExpect(content().string(not(containsString(">#99</span>"))));
    }

    @Test void listingOmitsFrontNumbersAndKeepsPageOffset() throws Exception {
        when(artistRepository.findAll()).thenReturn(List.of());
        when(archiveService.pageByType(eq(MediaType.IMAGE), anyInt(), anyInt(), isNull()))
                .thenReturn(new PageImpl<>(List.of(photo()), PageRequest.of(1, 9), 20));
        mvc.perform(get("/archive").param("page", "1"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("data-number-offset=\"9\"")))
                .andExpect(content().string(not(containsString("data-archive-number"))))
                .andExpect(content().string(not(containsString("id=\"archiveTrack\""))));
    }

    @Test void photoCardsShowTitleThenAllMemberLogos() throws Exception {
        when(artistRepository.findAll()).thenReturn(List.of());
        Archive group = photo();
        group.setArtistNames("윤서연, 윤서연, 정혜린, 이지우, <김채연>");
        Archive solo = photo();
        solo.setId(8L);
        solo.setTitle("지서연");
        solo.setArtistNames("서연 \"A\"");
        when(archiveService.pageByType(eq(MediaType.IMAGE), anyInt(), anyInt(), isNull()))
                .thenReturn(new PageImpl<>(List.of(group, solo)));
        String html = mvc.perform(get("/archive"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("윤서연")))
                .andExpect(content().string(not(containsString("id=\"archiveTrack\""))))
                .andReturn().getResponse().getContentAsString();
        String grid = html.substring(html.indexOf("id=\"archiveGrid\""));
        org.assertj.core.api.Assertions.assertThat(grid).contains("<h3>포토</h3>");
        org.assertj.core.api.Assertions.assertThat(grid.indexOf("<h3>포토</h3>"))
                .isLessThan(grid.indexOf("archive-chip-name\">윤서연</span>"));
        org.assertj.core.api.Assertions.assertThat(grid).contains("archive-chip-name\">정혜린</span>");
        org.assertj.core.api.Assertions.assertThat(grid).contains("archive-chip-name\">이지우</span>");
        org.assertj.core.api.Assertions.assertThat(grid).contains("title=\"서연 &quot;A&quot;\"");
        org.assertj.core.api.Assertions.assertThat(html).doesNotContain("외 0명");
        org.assertj.core.api.Assertions.assertThat(html).doesNotContain("최근 사진");
    }

    @Test void pageJumpRedirectsToZeroBasedPage() throws Exception {
        mvc.perform(get("/archive").param("go", "2").param("q", "heart"))
                .andExpect(redirectedUrl("/archive?q=heart&page=1"));
        mvc.perform(get("/archive").param("type", "video").param("go", "1"))
                .andExpect(redirectedUrl("/archive?type=video"));
        mvc.perform(get("/archive/all").param("go", "3").param("members", "윤서연"))
                .andExpect(redirectedUrl("/archive/all?members=%EC%9C%A4%EC%84%9C%EC%97%B0&page=2"));
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
        String html = mvc.perform(get("/archive")
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
        org.assertj.core.api.Assertions.assertThat(html).contains("사진 날짜 시작");
    }

    @Test void exactPhotoDateFilterUsesRegisteredOn() throws Exception {
        when(artistRepository.findAll()).thenReturn(List.of());
        when(archiveService.pageByType(eq(MediaType.IMAGE), anyInt(), anyInt(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(photo())));
        mvc.perform(get("/archive").param("on", "2026-03-15"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("name=\"on\"")))
                .andExpect(content().string(containsString("value=\"2026-03-15\"")));
        verify(archiveService).pageByType(eq(MediaType.IMAGE), anyInt(), anyInt(), any(), any(),
                eq(java.time.LocalDate.of(2026, 3, 15)), eq(java.time.LocalDate.of(2026, 3, 15)));
    }

    @Test void photoBrowsePageListsAllPhotosWithoutCarousel() throws Exception {
        when(artistRepository.findAll()).thenReturn(List.of());
        Archive group = photo();
        group.setArtistNames("윤서연, 정혜린");
        when(archiveService.pageByType(eq(MediaType.IMAGE), anyInt(), anyInt(), isNull()))
                .thenReturn(new PageImpl<>(List.of(group), PageRequest.of(0, 24), 1));
        String html = mvc.perform(get("/archive/all"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("사진 전체")))
                .andExpect(content().string(containsString("is-browse")))
                .andExpect(content().string(containsString("data-archive-open")))
                .andExpect(content().string(not(containsString("id=\"archiveTrack\""))))
                .andExpect(content().string(not(containsString("id=\"archiveGalleryControls\""))))
                .andReturn().getResponse().getContentAsString();
        String visible = html.substring(html.indexOf("id=\"archiveGrid\""));
        org.assertj.core.api.Assertions.assertThat(visible).contains("윤서연");
        org.assertj.core.api.Assertions.assertThat(visible).contains("archive-chip-name\">정혜린</span>");
        org.assertj.core.api.Assertions.assertThat(visible.indexOf("<h3>포토</h3>"))
                .isLessThan(visible.indexOf("archive-chip-name\">윤서연</span>"));
    }

    @Test void invalidMemberColorUsesReadableNeutralBadge() throws Exception {
        var member = new Artist();
        member.setName("서연");
        member.setColor("red;position:fixed");
        when(artistRepository.findAll()).thenReturn(List.of(member));
        when(archiveService.pageByType(eq(MediaType.IMAGE), anyInt(), anyInt(), isNull()))
                .thenReturn(new PageImpl<>(List.of(photo())));
        mvc.perform(get("/archive"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("--member-color: #b9cac4;")))
                .andExpect(content().string(not(containsString("--member-color: red;position:fixed"))));
    }

    @Test void oldArchiveUrlsRedirectAndKeepFilters() throws Exception {
        mvc.perform(get("/archive/image_archive")).andExpect(redirectedUrl("/archive"));
        mvc.perform(get("/archive/video_archive")).andExpect(redirectedUrl("/archive?type=video"));
        mvc.perform(get("/archive/image_archive/all")).andExpect(redirectedUrl("/archive/all"));
        mvc.perform(get("/archive/image_archive").param("q", "heart").param("page", "1"))
                .andExpect(redirectedUrl("/archive?q=heart&page=1"));
        mvc.perform(get("/archive/video_archive").param("q", "stage"))
                .andExpect(redirectedUrl("/archive?type=video&q=stage"));
    }

    @Test void imageUploadStaysOnTypedEndpoint() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "a.jpg", "image/jpeg", new byte[]{1, 2});
        mvc.perform(multipart("/archive/upload/image").file(file)
                        .param("title", "사진").param("artistNames", "서연").param("registeredOn", "2026-10-03"))
                .andExpect(redirectedUrl("/archive"));
        verify(archiveService).uploadImageOnly(any(), eq("사진"), eq(MediaType.IMAGE), eq("서연"), eq(java.time.LocalDate.of(2026, 10, 3)));
        verify(aboutAdminAuth).requireAdmin(any(), any());
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
