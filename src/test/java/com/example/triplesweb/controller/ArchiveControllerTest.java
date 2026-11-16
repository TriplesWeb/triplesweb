package com.example.triplesweb.controller;

import com.example.triplesweb.config.MustacheConfig;
import com.example.triplesweb.entity.Archive;
import com.example.triplesweb.entity.ArchiveSource;
import com.example.triplesweb.entity.Artist;
import com.example.triplesweb.entity.MediaType;
import com.example.triplesweb.entity.SavedMeta;
import com.example.triplesweb.repository.ArtistRepository;
import com.example.triplesweb.dto.ChemistryDto;
import com.example.triplesweb.dto.ChemistryGroupDto;
import com.example.triplesweb.entity.Unit;
import com.example.triplesweb.service.AboutAdminAuth;
import com.example.triplesweb.service.ArchiveService;
import com.example.triplesweb.service.ChemistryGroupService;
import com.example.triplesweb.service.ChemistryService;
import com.example.triplesweb.service.UnitService;
import com.example.triplesweb.util.ArchiveFolderArt;
import org.junit.jupiter.api.BeforeEach;
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
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.lenient;
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
    @MockitoBean UnitService unitService;
    @MockitoBean ArchiveFolderArt archiveFolderArt;
    @MockitoBean ChemistryService chemistryService;
    @MockitoBean ChemistryGroupService chemistryGroupService;

    @BeforeEach void stubs() {
        lenient().when(unitService.listVisible()).thenReturn(List.of());
        lenient().when(archiveService.index()).thenReturn(List.of());
        lenient().when(archiveFolderArt.urlFor(any(), any(), any())).thenReturn("");
        lenient().when(chemistryService.findAll()).thenReturn(List.of());
        lenient().when(chemistryGroupService.findAll()).thenReturn(List.of());
    }

    private void stubFolder(MediaType type, Archive... items) {
        when(archiveService.pageInFolder(eq(type), anyInt(), anyInt(), nullable(String.class), any(),
                nullable(java.time.LocalDate.class), nullable(java.time.LocalDate.class),
                nullable(ArchiveSource.class), anyBoolean(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(items)));
    }

    @Test void folderHomeListsGroupUnitAndSoloFolders() throws Exception {
        mvc.perform(get("/archive")).andExpect(status().isOk())
                .andExpect(content().string(containsString("폴더를 선택하세요")))
                .andExpect(content().string(containsString("ar-folder")))
                .andExpect(content().string(containsString("S1 윤서연")))
                .andExpect(content().string(containsString("S24 지연")))
                .andExpect(content().string(containsString("ar-folder is-group")))
                .andExpect(content().string(containsString("전체 멤버")))
                .andExpect(content().string(containsString("ar-folder is-parent")))
                .andExpect(content().string(containsString("ar-folder-label\">유닛별</span>")))
                .andExpect(content().string(containsString("ar-folder-label\">케미</span>")))
                .andExpect(content().string(containsString("folder&#x3D;units")))
                .andExpect(content().string(containsString("folder&#x3D;combos")))
                .andExpect(content().string(containsString("/archive?folder")))
                .andExpect(content().string(not(containsString("id=\"archiveGrid\""))))
                .andExpect(content().string(not(containsString("site-section-tabs"))))
                .andExpect(content().string(containsString("ar-folder-shape")))
                .andExpect(content().string(containsString("ar-folder is-solo")))
                .andExpect(content().string(not(containsString("ar-folder-label\">2명 케미</span>"))))
                .andExpect(content().string(not(containsString("ar-folder-label\">3명 케미</span>"))))
                .andExpect(content().string(not(containsString("ar-folder-label\">5명 이상</span>"))))
                .andExpect(content().string(containsString("ar-home-upload")))
                .andExpect(content().string(containsString("사진 추가")))
                .andExpect(content().string(containsString("id=\"uploadModal\"")));
    }

    @Test void folderHomeGroupsNonUnitChemistryByMemberCount() throws Exception {
        Archive duo = photo();
        duo.setArtistNames("윤서연,정혜린");
        Archive otherDuo = photo();
        otherDuo.setId(8L);
        otherDuo.setArtistNames("김채연,김유연");
        Archive trio = photo();
        trio.setId(9L);
        trio.setArtistNames("윤서연,정혜린,이지우");
        Archive six = photo();
        six.setId(10L);
        six.setArtistNames("윤서연,정혜린,이지우,김채연,김유연,김수민");
        when(archiveService.index()).thenReturn(List.of(duo, otherDuo, trio, six));
        String html = mvc.perform(get("/archive")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        org.assertj.core.api.Assertions.assertThat(html).contains("ar-folder-label\">케미</span>");
        org.assertj.core.api.Assertions.assertThat(html).contains("folder&#x3D;combos");
        org.assertj.core.api.Assertions.assertThat(html).doesNotContain("ar-folder-label\">2명 케미</span>");
        org.assertj.core.api.Assertions.assertThat(html).doesNotContain("folder&#x3D;combo-2");
        org.assertj.core.api.Assertions.assertThat(html).doesNotContain("ar-folder is-combo");
        org.assertj.core.api.Assertions.assertThat(html).doesNotContain("ar-folder-art");
        org.assertj.core.api.Assertions.assertThat(html).doesNotContain("ar-folder-preview");
        int chem = html.indexOf("folder&#x3D;combos");
        int chemEnd = html.indexOf("</a>", chem);
        org.assertj.core.api.Assertions.assertThat(html.substring(chem, chemEnd)).contains("ar-folder-count\">4</span>");

        String index = mvc.perform(get("/archive").param("folder", "combos")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        org.assertj.core.api.Assertions.assertThat(index).contains(">케미</p>");
        org.assertj.core.api.Assertions.assertThat(index).contains("←목록으로");
        org.assertj.core.api.Assertions.assertThat(index).contains("href=\"/archive\"");
        org.assertj.core.api.Assertions.assertThat(index).contains("ar-folder-label\">2명 케미</span>");
        org.assertj.core.api.Assertions.assertThat(index).contains("ar-folder-label\">3명 케미</span>");
        org.assertj.core.api.Assertions.assertThat(index).contains("ar-folder-label\">4명 케미</span>");
        org.assertj.core.api.Assertions.assertThat(index).contains("ar-folder-label\">5명 이상</span>");
        org.assertj.core.api.Assertions.assertThat(index).contains("folder&#x3D;combo-2");
        org.assertj.core.api.Assertions.assertThat(index).contains("folder&#x3D;combo-3");
        org.assertj.core.api.Assertions.assertThat(index).contains("folder&#x3D;combo-4");
        org.assertj.core.api.Assertions.assertThat(index).contains("folder&#x3D;combo-5");
        org.assertj.core.api.Assertions.assertThat(index).doesNotContain("folder&#x3D;combo-6");
        org.assertj.core.api.Assertions.assertThat(index).doesNotContain("윤서연 × 정혜린");
        org.assertj.core.api.Assertions.assertThat(index).doesNotContain("folder=combo&amp;combo=");
        org.assertj.core.api.Assertions.assertThat(index).doesNotContain("folder&#x3D;combo&amp;combo");
        org.assertj.core.api.Assertions.assertThat(index).contains("ar-folder is-combo");
        org.assertj.core.api.Assertions.assertThat(index).doesNotContain("ar-folder-art");
        org.assertj.core.api.Assertions.assertThat(index).doesNotContain("ar-folder-preview");
        int comboTwo = index.indexOf("folder&#x3D;combo-2");
        int comboTwoEnd = index.indexOf("</a>", comboTwo);
        org.assertj.core.api.Assertions.assertThat(index.substring(comboTwo, comboTwoEnd)).contains("ar-folder-count\">2</span>");
        int comboFour = index.indexOf("folder&#x3D;combo-4");
        int comboFourEnd = index.indexOf("</a>", comboFour);
        org.assertj.core.api.Assertions.assertThat(index.substring(comboFour, comboFourEnd)).doesNotContain("ar-folder-count");
    }

    @Test void memberFoldersHidePreviewImages() throws Exception {
        Archive solo = photo();
        solo.setArtistNames("윤서연");
        when(archiveService.index()).thenReturn(List.of(solo));
        String html = mvc.perform(get("/archive")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        org.assertj.core.api.Assertions.assertThat(html).contains("ar-folder is-solo");
        org.assertj.core.api.Assertions.assertThat(html).doesNotContain("ar-folder-preview");
        org.assertj.core.api.Assertions.assertThat(html).doesNotContain("src=\"/uploads/a.jpg\"");
    }

    @Test void comboCountFolderKeepsMemberFilter() throws Exception {
        when(artistRepository.findAll()).thenReturn(List.of());
        stubFolder(MediaType.IMAGE, photo());
        mvc.perform(get("/archive").param("folder", "combo-2")).andExpect(status().isOk())
                .andExpect(content().string(containsString(">2명 케미</p>")))
                .andExpect(content().string(org.hamcrest.Matchers.anyOf(
                        containsString("href=\"/archive?folder=combos\""),
                        containsString("href=\"/archive?folder&#x3D;combos\""))))
                .andExpect(content().string(containsString("id=\"archiveChem\"")))
                .andExpect(content().string(containsString("케미 전체")))
                .andExpect(content().string(containsString("id=\"archive-member-picker\"")))
                .andExpect(content().string(containsString(">멤버 선택<")));
    }

    @Test void unitFolderWithoutImageOmitsPlaceholderPreview() throws Exception {
        Unit aura = new Unit();
        aura.setUnitId(17L);
        aura.setUnitName("Aura");
        aura.setVisible(true);
        when(unitService.listVisible()).thenReturn(List.of(aura));
        String home = mvc.perform(get("/archive")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        org.assertj.core.api.Assertions.assertThat(home).contains("ar-folder-label\">유닛별</span>");
        org.assertj.core.api.Assertions.assertThat(home).doesNotContain("folder&#x3D;unit-17");
        org.assertj.core.api.Assertions.assertThat(home).doesNotContain("Aura");

        String html = mvc.perform(get("/archive").param("folder", "units")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        org.assertj.core.api.Assertions.assertThat(html).contains(">유닛별</p>");
        org.assertj.core.api.Assertions.assertThat(html).contains("←목록으로");
        org.assertj.core.api.Assertions.assertThat(html).contains("Aura");
        org.assertj.core.api.Assertions.assertThat(html).contains("folder&#x3D;unit-17");
        org.assertj.core.api.Assertions.assertThat(html).doesNotContain("image-upload-pending");
        org.assertj.core.api.Assertions.assertThat(html).doesNotContain("이미지 업로드 예정");
    }

    @Test void comboFolderFiltersPhotosByChemistryTreePair() throws Exception {
        ChemistryDto pair = ChemistryDto.builder()
                .id(3L).memberA("윤서연").memberB("정혜린").label("시선강탈").build();
        when(chemistryService.findAll()).thenReturn(List.of(pair));
        stubFolder(MediaType.IMAGE, photo());
        String html = mvc.perform(get("/archive").param("folder", "combo-2").param("chem", "pair-3"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        org.assertj.core.api.Assertions.assertThat(html).contains("id=\"archiveChem\"");
        org.assertj.core.api.Assertions.assertThat(html).contains("시선강탈");
        org.assertj.core.api.Assertions.assertThat(html).contains("value=\"pair-3\"");
        org.assertj.core.api.Assertions.assertThat(html).contains(">시선강탈</p>");
        org.assertj.core.api.Assertions.assertThat(html).containsAnyOf("chem=pair-3", "chem&#x3D;pair-3");
        verify(archiveService).pageInFolder(eq(MediaType.IMAGE), anyInt(), anyInt(), nullable(String.class),
                eq(List.of("윤서연", "정혜린")),
                nullable(java.time.LocalDate.class), nullable(java.time.LocalDate.class),
                nullable(ArchiveSource.class), anyBoolean(), eq("combo-2"),
                eq(List.of("윤서연", "정혜린")), any());
    }

    @Test void comboThreeFolderListsMatchingChemistryGroups() throws Exception {
        ChemistryGroupDto trio = ChemistryGroupDto.builder()
                .id(8L).members(List.of("윤서연", "정혜린", "이지우")).label("트리오").build();
        ChemistryGroupDto pair = ChemistryGroupDto.builder()
                .id(9L).members(List.of("윤서연", "정혜린")).label("페어").build();
        when(chemistryGroupService.findAll()).thenReturn(List.of(trio, pair));
        stubFolder(MediaType.IMAGE, photo());
        String html = mvc.perform(get("/archive").param("folder", "combo-3")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        org.assertj.core.api.Assertions.assertThat(html).contains("트리오");
        org.assertj.core.api.Assertions.assertThat(html).contains("group-8");
        org.assertj.core.api.Assertions.assertThat(html).doesNotContain("group-9");
        org.assertj.core.api.Assertions.assertThat(html).doesNotContain("페어");
    }

    @Test void imageUploadOfTwoMembersOpensTheCountFolder() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "a.jpg", "image/jpeg", new byte[]{1, 2});
        mvc.perform(multipart("/archive/upload/image").file(file)
                        .param("title", "사진").param("artistNames", "윤서연").param("artistNames", "정혜린")
                        .param("registeredOn", "2026-10-03").param("source", "FANCAM"))
                .andExpect(redirectedUrl("/archive?folder=combo-2"));
    }

    @Test void imageUploadOfSixMembersOpensTheSixPlusFolder() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "a.jpg", "image/jpeg", new byte[]{1, 2});
        mvc.perform(multipart("/archive/upload/image").file(file)
                        .param("title", "사진")
                        .param("artistNames", "윤서연").param("artistNames", "정혜린").param("artistNames", "이지우")
                        .param("artistNames", "김채연").param("artistNames", "김유연").param("artistNames", "김수민")
                        .param("registeredOn", "2026-10-03").param("source", "FANCAM"))
                .andExpect(redirectedUrl("/archive?folder=combo-5"));
    }

    @Test void imageUploadOfThreeMembersOpensTheTrioFolder() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "a.jpg", "image/jpeg", new byte[]{1, 2});
        mvc.perform(multipart("/archive/upload/image").file(file)
                        .param("title", "사진")
                        .param("artistNames", "윤서연").param("artistNames", "정혜린").param("artistNames", "이지우")
                        .param("registeredOn", "2026-10-03").param("source", "FANCAM"))
                .andExpect(redirectedUrl("/archive?folder=combo-3"));
    }

    @Test void imageUploadOfFourMembersOpensTheQuartetFolder() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "a.jpg", "image/jpeg", new byte[]{1, 2});
        mvc.perform(multipart("/archive/upload/image").file(file)
                        .param("title", "사진")
                        .param("artistNames", "윤서연").param("artistNames", "정혜린").param("artistNames", "이지우")
                        .param("artistNames", "김채연")
                        .param("registeredOn", "2026-10-03").param("source", "FANCAM"))
                .andExpect(redirectedUrl("/archive?folder=combo-4"));
    }

    @Test void adminImageArchiveShowsEditAndDelete() throws Exception {
        when(aboutAdminAuth.isAdmin(any(), any())).thenReturn(true);
        when(artistRepository.findAll()).thenReturn(List.of());
        stubFolder(MediaType.IMAGE, photo());

        mvc.perform(get("/archive").param("folder", "s")).andExpect(status().isOk())
                .andExpect(content().string(containsString("포토")))
                .andExpect(content().string(containsString("<h1>아카이브</h1>")))
                .andExpect(content().string(containsString("site-section-tabs")))
                .andExpect(content().string(containsString("사진 날짜")))
                .andExpect(content().string(containsString("실제로 올라온 날짜를 선택하세요.")))
                .andExpect(content().string(containsString("type=\"date\"")))
                .andExpect(content().string(containsString("name=\"registeredOn\"")))
                .andExpect(content().string(containsString("data-date-clear")))
                .andExpect(content().string(containsString(">초기화</button>")))
                .andExpect(content().string(not(containsString(">지우기</button>"))))
                .andExpect(content().string(not(containsString("data-ymd-year"))))
                .andExpect(content().string(not(containsString("aria-label=\"연도\""))))
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
                .andExpect(content().string(not(containsString("더보기"))))
                .andExpect(content().string(not(containsString("원본 열기"))))
                .andExpect(content().string(not(containsString("archiveDetailOriginal"))))
                .andExpect(content().string(containsString(">검색</button>")))
                .andExpect(content().string(containsString("class=\"ar-submit\"")))
                .andExpect(content().string(not(containsString("ar-search-go"))))
                .andExpect(content().string(containsString("첫멤버순")))
                .andExpect(content().string(containsString("마지막멤버순")))
                .andExpect(content().string(containsString("data-member-order=\"0\"")))
                .andExpect(content().string(containsString("data-stats-toggle")))
                .andExpect(content().string(containsString("name=\"go\"")))
                .andExpect(content().string(not(containsString("이 사진 날짜"))))
                .andExpect(content().string(not(containsString("실제로 올라온 날짜로 찾습니다."))))
                .andExpect(content().string(not(containsString("id=\"archiveTrack\""))))
                .andExpect(content().string(not(containsString("최근 사진"))))
                .andExpect(content().string(not(containsString("id=\"archiveGalleryControls\""))))
                .andExpect(content().string(not(containsString("archive-photo-carousel"))))
                .andExpect(content().string(not(containsString("사진 아카이브"))))
                .andExpect(content().string(not(containsString("등록된 사진과 출연 표기를 확인하세요."))))
                .andExpect(content().string(not(containsString("원본 보기 ↗"))))
                .andExpect(content().string(not(containsString("14.8M"))))
                .andExpect(content().string(not(containsString("Polygon"))))
                .andExpect(content().string(not(containsString("extraArtists"))))
                .andExpect(content().string(not(containsString("다른 출연자"))))
                .andExpect(content().string(not(containsString("목록에 없는 출연자"))))
                .andExpect(content().string(containsString("파일 선택")))
                .andExpect(content().string(containsString("선택된 파일 없음")))
                .andExpect(content().string(containsString("코스모룸")))
                .andExpect(content().string(containsString("코스모톡/프롬")))
                .andExpect(content().string(containsString("공식 SNS")))
                .andExpect(content().string(containsString("외부 컨텐츠/방송")))
                .andExpect(content().string(containsString("기타/팬메이드")))
                .andExpect(content().string(not(containsString("직찍"))))
                .andExpect(content().string(not(containsString(">미분류<"))))
                .andExpect(content().string(containsString("unit-member-chip")))
                .andExpect(content().string(containsString("ar-source-tabs")))
                .andExpect(content().string(containsString("ar-folder-tabs")));
    }

    @Test void archiveMemberPickerUsesPortraitsNotSymbolLogos() throws Exception {
        var member = new Artist();
        member.setName("윤서연");
        member.setEngName("SeoYeon");
        member.setColor("#22aeff");
        member.setProfileImage("/uploads/portraits/seoyeon.jpg");
        when(artistRepository.findAll()).thenReturn(List.of(member));
        stubFolder(MediaType.IMAGE, photo());
        String html = mvc.perform(get("/archive").param("folder", "s")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        int start = html.indexOf("id=\"archive-member-picker\"");
        int end = html.indexOf("</div>", start);
        String picker = html.substring(start, end);
        org.assertj.core.api.Assertions.assertThat(picker).contains("/uploads/portraits/seoyeon.jpg");
        org.assertj.core.api.Assertions.assertThat(picker).doesNotContain("S1.png");
        org.assertj.core.api.Assertions.assertThat(html).contains("S1.png");
        org.assertj.core.api.Assertions.assertThat(html).doesNotContain("archive-symbol-picker");
    }

    @Test void folderViewKeepsFolderOnTabsAndHidesSoloMemberFilter() throws Exception {
        when(artistRepository.findAll()).thenReturn(List.of());
        stubFolder(MediaType.IMAGE, photo());
        String html = mvc.perform(get("/archive").param("folder", "s1").param("source", "fancam"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("ar-folder-tabs")))
                .andExpect(content().string(containsString("ar-source-tabs")))
                .andExpect(content().string(containsString("기타/팬메이드")))
                .andExpect(content().string(not(containsString("직찍"))))
                .andReturn().getResponse().getContentAsString();
        org.assertj.core.api.Assertions.assertThat(html).containsAnyOf("source=fanmade", "source&#x3D;fanmade");
        int toolbar = html.indexOf("id=\"archiveFilterForm\"");
        int typeTabs = html.indexOf("ar-folder-tabs");
        int sourceTabs = html.indexOf("ar-source-tabs");
        org.assertj.core.api.Assertions.assertThat(toolbar).isGreaterThan(0);
        org.assertj.core.api.Assertions.assertThat(typeTabs).isGreaterThan(toolbar);
        org.assertj.core.api.Assertions.assertThat(sourceTabs).isGreaterThan(typeTabs);
        org.assertj.core.api.Assertions.assertThat(html).contains("/archive?folder");
        org.assertj.core.api.Assertions.assertThat(html).contains("/archive?type");
        org.assertj.core.api.Assertions.assertThat(html).doesNotContain("folder=s1&amp;folder=s1");
        org.assertj.core.api.Assertions.assertThat(html).doesNotContain("folder&#x3D;s1&amp;folder&#x3D;s1");
        org.assertj.core.api.Assertions.assertThat(html).doesNotContain("id=\"archive-member-picker\"");
        org.assertj.core.api.Assertions.assertThat(html).doesNotContain(">멤버 선택<");
    }

    @Test void emptyFolderShowsPageOneAndMemberTitle() throws Exception {
        when(artistRepository.findAll()).thenReturn(List.of());
        stubFolder(MediaType.IMAGE);
        String html = mvc.perform(get("/archive").param("folder", "s19")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        org.assertj.core.api.Assertions.assertThat(html).contains("ar-folder-title");
        org.assertj.core.api.Assertions.assertThat(html).contains("S19 정하연");
        org.assertj.core.api.Assertions.assertThat(html).contains("←목록으로");
        org.assertj.core.api.Assertions.assertThat(html).doesNotContain(">폴더 목록<");
        org.assertj.core.api.Assertions.assertThat(html).doesNotContain("이 페이지에서 조건에 맞는");
        org.assertj.core.api.Assertions.assertThat(html).doesNotContain("id=\"archiveNone\"");
        org.assertj.core.api.Assertions.assertThat(html).contains("페이지 1 / 1");
        org.assertj.core.api.Assertions.assertThat(html).contains("is-current");
        org.assertj.core.api.Assertions.assertThat(html).contains("기타/팬메이드");
        org.assertj.core.api.Assertions.assertThat(html).doesNotContain("직찍");
        org.assertj.core.api.Assertions.assertThat(html).doesNotContain(">미분류<");
        org.assertj.core.api.Assertions.assertThat(html).contains("수정하기");
        org.assertj.core.api.Assertions.assertThat(html).contains("삭제하기");
        org.assertj.core.api.Assertions.assertThat(html).contains("전체 출처");
        org.assertj.core.api.Assertions.assertThat(html).contains("class=\"ar-source-badge\"");
        org.assertj.core.api.Assertions.assertThat(html).contains("ar-source-field");
        org.assertj.core.api.Assertions.assertThat(html).contains("lang=\"ko\"");
        org.assertj.core.api.Assertions.assertThat(html).doesNotContain("출처 미분류");
        org.assertj.core.api.Assertions.assertThat(html).doesNotContain("is-violet");
    }

    @Test void folderSearchStatFollowsTypeSourceAndDate() throws Exception {
        when(artistRepository.findAll()).thenReturn(List.of());
        stubFolder(MediaType.IMAGE);
        String html = mvc.perform(get("/archive").param("folder", "s")
                        .param("source", "cosmo_room").param("on", "2026-11-16"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        int start = html.indexOf("ar-filter-summary");
        int end = html.indexOf("</div>", start);
        String summary = html.substring(start, end);
        org.assertj.core.api.Assertions.assertThat(summary).contains(">사진<");
        org.assertj.core.api.Assertions.assertThat(summary).contains("코스모룸");
        org.assertj.core.api.Assertions.assertThat(summary).contains("2026/11/16");
        org.assertj.core.api.Assertions.assertThat(summary).doesNotContain(">전체<");
    }

    @Test void photoCardShowsDateBesideMembers() throws Exception {
        Archive shot = photo();
        shot.setRegisteredOn(java.time.LocalDate.of(2026, 11, 16));
        shot.setSource(ArchiveSource.COSMO_ROOM);
        when(artistRepository.findAll()).thenReturn(List.of());
        stubFolder(MediaType.IMAGE, shot);
        String html = mvc.perform(get("/archive").param("folder", "s")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        int meta = html.indexOf("class=\"ar-meta\"");
        int members = html.indexOf("ar-card-members");
        String row = html.substring(meta, members);
        org.assertj.core.api.Assertions.assertThat(row).contains("ar-card-date");
        org.assertj.core.api.Assertions.assertThat(row).contains("2026/11/16");
        org.assertj.core.api.Assertions.assertThat(html).contains("data-source-label=\"코스모룸\"");
    }

    @Test void guestImageArchiveHidesEditAndDelete() throws Exception {
        when(aboutAdminAuth.isAdmin(any(), any())).thenReturn(false);
        when(artistRepository.findAll()).thenReturn(List.of());
        stubFolder(MediaType.IMAGE, photo());

        mvc.perform(get("/archive").param("folder", "s")).andExpect(status().isOk())
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
        stubFolder(MediaType.VIDEO, video);

        mvc.perform(get("/archive").param("type", "video").param("folder", "s1")).andExpect(status().isOk())
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

        mvc.perform(multipart("/archive/7").file(file).param("title", "새 제목").param("artistNames", "서연").param("registeredOn", "2026-10-01").param("source", "FANCAM"))
                .andExpect(status().isOk());
        verify(archiveService).update(eq(7L), eq("새 제목"), any(), eq("서연"), isNull(), eq(false), eq(java.time.LocalDate.of(2026, 10, 1)), eq(ArchiveSource.FANCAM));
        verify(aboutAdminAuth).requireAdmin(any(), any());

        mvc.perform(delete("/archive/7")).andExpect(status().isOk());
        verify(archiveService).delete(7L);
    }

    @Test void videoUploadAcceptsOptionalThumbnail() throws Exception {
        var video = new MockMultipartFile("file", "video.mp4", "video/mp4", new byte[]{1});
        var thumbnail = new MockMultipartFile("thumbnail", "poster.jfif", "application/octet-stream", new byte[]{2});
        mvc.perform(multipart("/archive/upload/video").file(video).file(thumbnail)
                        .param("title", "영상").param("artistNames", "서연").param("registeredOn", "2026-10-02").param("source", "COSMO_ROOM"))
                .andExpect(redirectedUrl("/archive?type=video&folder=s1"));
        verify(archiveService).uploadVideoOnly(any(), eq("영상"), eq(MediaType.VIDEO), eq("서연"), any(), eq(java.time.LocalDate.of(2026, 10, 2)), eq(ArchiveSource.COSMO_ROOM));
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
        stubFolder(type, first, second);
        mvc.perform(get("/archive").param("type", type == MediaType.IMAGE ? "image" : "video").param("folder", "s1"))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("data-archive-number"))))
                .andExpect(content().string(containsString("--member-color: #ffab62;")))
                .andExpect(content().string(containsString("--member-color: #b9cac4;")))
                .andExpect(content().string(containsString("data-id=\"99\"")))
                .andExpect(content().string(not(containsString(">#99</span>"))));
    }

    @Test void listingOmitsFrontNumbersAndKeepsPageOffset() throws Exception {
        when(artistRepository.findAll()).thenReturn(List.of());
        when(archiveService.pageInFolder(eq(MediaType.IMAGE), anyInt(), anyInt(), nullable(String.class), any(),
                nullable(java.time.LocalDate.class), nullable(java.time.LocalDate.class),
                nullable(ArchiveSource.class), anyBoolean(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(photo()), PageRequest.of(1, 9), 20));
        mvc.perform(get("/archive").param("page", "1").param("folder", "s1"))
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
        when(archiveService.pageByType(eq(MediaType.IMAGE), anyInt(), anyInt(), isNull(), any(), isNull(), isNull()))
                .thenReturn(new PageImpl<>(List.of(group, solo)));
        String html = mvc.perform(get("/archive/all"))
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
        stubFolder(MediaType.IMAGE, photo());
        String html = mvc.perform(get("/archive")
                        .param("folder", "s")
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
        org.assertj.core.api.Assertions.assertThat(html).contains("2026/01/01 ~ 2026/12/31");
        org.assertj.core.api.Assertions.assertThat(html).contains("전체 출처");
        org.assertj.core.api.Assertions.assertThat(html).contains("--filter-count: 5");
        org.assertj.core.api.Assertions.assertThat(html).contains("사진 날짜 시작");
    }

    @Test void exactPhotoDateFilterUsesRegisteredOn() throws Exception {
        when(artistRepository.findAll()).thenReturn(List.of());
        stubFolder(MediaType.IMAGE, photo());
        mvc.perform(get("/archive").param("folder", "s1").param("on", "2026-03-15"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("name=\"on\"")))
                .andExpect(content().string(containsString("value=\"2026-03-15\"")));
        verify(archiveService).pageInFolder(eq(MediaType.IMAGE), anyInt(), anyInt(), any(), any(),
                eq(java.time.LocalDate.of(2026, 3, 15)), eq(java.time.LocalDate.of(2026, 3, 15)),
                isNull(), eq(false), eq("s1"), any(), any());
    }

    @Test void photoBrowsePageListsAllPhotosWithoutCarousel() throws Exception {
        when(artistRepository.findAll()).thenReturn(List.of());
        Archive group = photo();
        group.setArtistNames("윤서연, 정혜린");
        when(archiveService.pageByType(eq(MediaType.IMAGE), anyInt(), anyInt(), isNull(), any(), isNull(), isNull()))
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
        stubFolder(MediaType.IMAGE, photo());
        mvc.perform(get("/archive").param("folder", "s1"))
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
                        .param("title", "사진").param("artistNames", "서연").param("registeredOn", "2026-10-03").param("source", "FANCAM"))
                .andExpect(redirectedUrl("/archive?folder=s1"));
        verify(archiveService).uploadImageOnly(any(), eq("사진"), eq(MediaType.IMAGE), eq("서연"), eq(java.time.LocalDate.of(2026, 10, 3)), eq(ArchiveSource.FANCAM));
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
