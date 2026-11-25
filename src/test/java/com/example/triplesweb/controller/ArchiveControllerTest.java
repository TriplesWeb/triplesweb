package com.example.triplesweb.controller;

import com.example.triplesweb.config.MustacheConfig;
import com.example.triplesweb.entity.Archive;
import com.example.triplesweb.entity.ArchiveSource;
import com.example.triplesweb.entity.Artist;
import com.example.triplesweb.entity.MediaType;
import com.example.triplesweb.entity.SavedMeta;
import com.example.triplesweb.service.ArtistSnapshotService;
import com.example.triplesweb.dto.ChemistryDto;
import com.example.triplesweb.dto.ChemistryGroupDto;
import com.example.triplesweb.entity.Unit;
import com.example.triplesweb.entity.UnitMember;
import com.example.triplesweb.service.AboutAdminAuth;
import com.example.triplesweb.service.ArchiveService;
import com.example.triplesweb.service.ArchiveSourceService;
import com.example.triplesweb.dto.ArchiveSourceDto;
import com.example.triplesweb.service.ChemistryGroupService;
import com.example.triplesweb.service.ChemistryService;
import com.example.triplesweb.service.IdempotencyService;
import com.example.triplesweb.service.UnitService;
import com.example.triplesweb.util.ArchiveFolderArt;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import java.util.List;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import java.util.Optional;
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
@Import({MustacheConfig.class, ArchiveFolderArt.class})
class ArchiveControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean ArchiveService archiveService;
    @MockitoBean ArchiveSourceService archiveSourceService;
    @MockitoBean AboutAdminAuth aboutAdminAuth;
    @MockitoBean ArtistSnapshotService artistSnapshot;
    @MockitoBean UnitService unitService;
    @MockitoBean ChemistryService chemistryService;
    @MockitoBean ChemistryGroupService chemistryGroupService;
    @MockitoBean IdempotencyService idempotencyService;

    @BeforeEach void stubs() {
        lenient().when(unitService.listVisibleForArchive()).thenReturn(List.of());
        lenient().when(archiveService.index()).thenReturn(List.of());
        lenient().when(archiveService.countTotals())
                .thenReturn(new ArchiveService.TypeTotals(0L, 0L));
        lenient().when(archiveService.countByType(MediaType.IMAGE)).thenReturn(0L);
        lenient().when(archiveService.countByType(MediaType.VIDEO)).thenReturn(0L);
        lenient().when(chemistryService.findAll()).thenReturn(List.of());
        lenient().when(chemistryGroupService.findAll()).thenReturn(List.of());
        lenient().when(idempotencyService.claimOrReplay(anyString(), nullable(String.class)))
                .thenReturn(Optional.empty());
        List<ArchiveSourceDto> sourceDtos = defaultSourceDtos();
        lenient().when(archiveSourceService.listTabs(org.mockito.ArgumentMatchers.<String>anyCollection(), anyBoolean()))
                .thenAnswer(invocation -> {
                    java.util.Collection<String> used = invocation.getArgument(0);
                    boolean admin = invocation.getArgument(1);
                    return sourceDtos.stream()
                            .map(dto -> new ArchiveSourceService.SourceTab(dto, used.contains(dto.code())))
                            .filter(tab -> admin || tab.hasPosts())
                            .toList();
                });
        lenient().when(archiveSourceService.listAll()).thenReturn(sourceDtos);
        lenient().when(archiveSourceService.byCode()).thenReturn(defaultSourceMap());
        lenient().when(archiveSourceService.find(nullable(String.class))).thenAnswer(invocation ->
                resolveSource(invocation.getArgument(0)));
        lenient().when(archiveSourceService.requireExisting(anyString())).thenAnswer(invocation -> {
            ArchiveSource source = resolveSource(invocation.getArgument(0));
            if (source == null) throw new IllegalArgumentException("출처를 선택하세요.");
            return source;
        });
    }

    private static List<ArchiveSourceDto> defaultSourceDtos() {
        return defaultSources().stream().map(ArchiveSourceDto::from).toList();
    }

    private static java.util.Map<String, ArchiveSource> defaultSourceMap() {
        java.util.Map<String, ArchiveSource> map = new java.util.LinkedHashMap<>();
        for (ArchiveSource source : defaultSources()) {
            map.put(source.getCode(), source);
        }
        return map;
    }

    private static List<ArchiveSource> defaultSources() {
        return List.of(
                source(1L, "COSMO_ROOM", "코스모룸", "#00f5d4", 10),
                source(2L, "COSMO_TALK", "코스모톡/프롬", "#a78bfa", 20),
                source(3L, "OFFICIAL_SNS", "공식 SNS", "#60a5fa", 30),
                source(4L, "POLAROID", "폴라로이드", "#5eead4", 40),
                source(5L, "TEASER_BEHIND", "티저/비하인드", "#2dd4bf", 50),
                source(6L, "MAGAZINE", "화보/매거진", "#f472b6", 60),
                source(7L, "EXTERNAL", "외부 컨텐츠/방송", "#38bdf8", 70)
        );
    }

    private static ArchiveSource source(long id, String code, String label, String color, int order) {
        ArchiveSource source = new ArchiveSource();
        source.setId(id);
        source.setCode(code);
        source.setLabel(label);
        source.setColor(color);
        source.setSortOrder(order);
        source.setVisible(true);
        return source;
    }

    private static ArchiveSource resolveSource(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String value = raw.trim();
        if ("teaser".equalsIgnoreCase(value)) value = "TEASER_BEHIND";
        for (ArchiveSource source : defaultSources()) {
            if (source.getCode().equalsIgnoreCase(value) || source.getSlug().equalsIgnoreCase(value)) {
                return source;
            }
        }
        return null;
    }

    private void stubFolder(MediaType type, Archive... items) {
        var sources = java.util.Arrays.stream(items).map(Archive::getSource)
                .filter(java.util.Objects::nonNull).collect(java.util.stream.Collectors.toSet());
        when(archiveService.browseFolder(eq(type), anyInt(), anyInt(), nullable(String.class), any(),
                nullable(java.time.LocalDate.class), nullable(java.time.LocalDate.class),
                nullable(String.class), anyBoolean(), any(), any(), any(), any()))
                .thenReturn(new ArchiveService.FolderBrowse(new PageImpl<>(List.of(items)), sources));
    }

    @Test void folderHomeListsGroupUnitAndSoloFolders() throws Exception {
        String html = mvc.perform(get("/archive")).andExpect(status().isOk())
                .andExpect(content().string(not(containsString("폴더를 선택하세요"))))
                .andExpect(content().string(containsString("ar-folder")))
                .andExpect(content().string(containsString("S1 윤서연")))
                .andExpect(content().string(containsString("S24 지연")))
                .andExpect(content().string(containsString("ar-folder is-group")))
                .andExpect(content().string(containsString("ar-folder-label\">tripleS</span>")))
                .andExpect(content().string(containsString("ar-folder is-parent")))
                .andExpect(content().string(containsString("ar-folder-label\">디멘션</span>")))
                .andExpect(content().string(not(containsString("ar-folder-label\">케미</span>"))))
                .andExpect(content().string(containsString("folder&#x3D;units")))
                .andExpect(content().string(not(containsString("folder&#x3D;combos"))))
                .andExpect(content().string(containsString("members&#x3D;")))
                .andExpect(content().string(not(containsString("data-folder-selection"))))
                .andExpect(content().string(not(containsString(">보기</button>"))))
                .andExpect(content().string(containsString("/archive?folder")))
                .andExpect(content().string(not(containsString("id=\"archiveGrid\""))))
                .andExpect(content().string(not(containsString("site-section-tabs"))))
                .andExpect(content().string(containsString("ar-folder is-solo")))
                .andExpect(content().string(not(containsString("ar-folder-label\">2명 케미</span>"))))
                .andExpect(content().string(not(containsString("ar-folder-label\">3명 케미</span>"))))
                .andExpect(content().string(not(containsString("ar-folder-label\">5명 이상</span>"))))
                .andExpect(content().string(containsString("ar-home-upload")))
                .andExpect(content().string(containsString("사진 추가")))
                .andExpect(content().string(not(containsString("id=\"uploadModal\""))))
                .andExpect(content().string(not(containsString("data-stats-toggle"))))
                .andExpect(content().string(containsString("id=\"archiveStats\"")))
                .andExpect(content().string(not(containsString("is-copy-only"))))
                .andReturn().getResponse().getContentAsString();
        var page = Jsoup.parse(html);
        org.assertj.core.api.Assertions.assertThat(page.select("#archiveStats .ar-stat")).hasSize(3);
        org.assertj.core.api.Assertions.assertThat(page.select(".ar-folder.is-group img.is-illustrated").attr("src"))
                .isEqualTo("/images/archive/folder-triples.png");
        org.assertj.core.api.Assertions.assertThat(page.select(".ar-folder.is-parent img.is-illustrated").attr("src"))
                .isEqualTo("/images/archive/folder-dimension.png");
        var folders = page.select(".ar-folders .ar-folder.is-solo");
        org.assertj.core.api.Assertions.assertThat(folders).hasSize(24);
        for (int i = 0; i < folders.size(); i++) {
            org.assertj.core.api.Assertions.assertThat(folders.get(i).select("img.is-bundled-art").attr("src"))
                    .isEqualTo("/images/archive/member-folders/" + (i + 1) + ".png");
        }
    }

    @Test void folderHomeGroupsNonUnitChemistryByMemberCount() throws Exception {
        String html = mvc.perform(get("/archive")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        org.assertj.core.api.Assertions.assertThat(html).doesNotContain("ar-folder-label\">케미</span>");
        org.assertj.core.api.Assertions.assertThat(html).doesNotContain("folder&#x3D;combos");
        org.assertj.core.api.Assertions.assertThat(html).doesNotContain("ar-folder-label\">2명 케미</span>");
        org.assertj.core.api.Assertions.assertThat(html).contains("ar-folder-label\">S1 윤서연</span>");
        int seoyeon = html.indexOf("ar-folder-label\">S1 윤서연</span>");
        int seoyeonStart = html.lastIndexOf("<a", seoyeon);
        int seoyeonEnd = html.indexOf("</a>", seoyeon);
        org.assertj.core.api.Assertions.assertThat(html.substring(seoyeonStart, seoyeonEnd))
                .contains("members&#x3D;%EC%9C%A4%EC%84%9C%EC%97%B0");
        org.assertj.core.api.Assertions.assertThat(html).doesNotContain("ar-folder-count");

        mvc.perform(get("/archive").param("folder", "combos").param("q", "heart").param("source", "cosmo_room"))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/archive?folder=s&source=cosmo_room&q=heart"));
    }

    @Test void memberFoldersHidePreviewImages() throws Exception {
        String html = mvc.perform(get("/archive")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        org.assertj.core.api.Assertions.assertThat(html).contains("ar-folder is-solo");
        org.assertj.core.api.Assertions.assertThat(html).doesNotContain("ar-folder-preview");
        org.assertj.core.api.Assertions.assertThat(html).doesNotContain("src=\"/uploads/a.jpg\"");
    }

    @Test void comboCountFolderRedirectsToGroupFolder() throws Exception {
        mvc.perform(get("/archive").param("folder", "combo-2").param("type", "video"))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/archive?type=video&folder=s"));
        mvc.perform(get("/archive").param("folder", "combo-6"))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/archive?folder=s"));
    }

    @Test void unitFolderUsesBundledArtWithoutRegisteredImage() throws Exception {
        Unit aura = new Unit();
        aura.setUnitId(17L);
        aura.setUnitName("Aura");
        aura.setVisible(true);
        Unit acidAngel = new Unit();
        acidAngel.setUnitId(1L);
        acidAngel.setUnitName("ACID ANGEL OF ASIA");
        when(unitService.listVisibleForArchive()).thenReturn(List.of(aura, acidAngel));
        String home = mvc.perform(get("/archive")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        org.assertj.core.api.Assertions.assertThat(home).contains("ar-folder-label\">디멘션</span>");
        org.assertj.core.api.Assertions.assertThat(home).doesNotContain("folder&#x3D;unit-17");
        org.assertj.core.api.Assertions.assertThat(home).doesNotContain("Aura");

        String html = mvc.perform(get("/archive").param("folder", "units")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        org.assertj.core.api.Assertions.assertThat(html).contains(">디멘션</h1>");
        org.assertj.core.api.Assertions.assertThat(html).contains("← 목록으로");
        org.assertj.core.api.Assertions.assertThat(html).contains("Aura");
        org.assertj.core.api.Assertions.assertThat(html).contains("ar-folder-art is-bundled-art", "/images/archive/unit-folders/42.png");
        org.assertj.core.api.Assertions.assertThat(html).contains("/images/archive/unit-folders/27.png");
        org.assertj.core.api.Assertions.assertThat(html).doesNotContain("ar-folder-preview");
        org.assertj.core.api.Assertions.assertThat(html).contains("folder&#x3D;unit-17");
        org.assertj.core.api.Assertions.assertThat(html).doesNotContain("image-upload-pending");
        org.assertj.core.api.Assertions.assertThat(html).doesNotContain("이미지 업로드 예정");
    }

    @Test void unitIndexHidesCountsAndArchiveCardShowsBothBadges() throws Exception {
        Unit first = new Unit();
        first.setUnitId(7L);
        first.setUnitName("첫 유닛");
        Unit second = new Unit();
        second.setUnitId(8L);
        second.setUnitName("둘째 유닛");
        when(unitService.listVisibleForArchive()).thenReturn(List.of(first, second));

        String index = mvc.perform(get("/archive").param("folder", "units"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        var folders = Jsoup.parse(index);
        org.assertj.core.api.Assertions.assertThat(folders.select("a[href$=unit-7], a[href$=unit-8]")).hasSize(2);
        org.assertj.core.api.Assertions.assertThat(folders.select(".ar-folder-count")).isEmpty();

        Archive shared = photo();
        shared.setUnitIds(Set.of(8L, 7L));
        stubFolder(MediaType.IMAGE, shared);
        String html = mvc.perform(get("/archive").param("folder", "unit-7"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        var card = Jsoup.parse(html).selectFirst(".ar-card[data-unit-ids='7,8']");
        org.assertj.core.api.Assertions.assertThat(card).isNotNull();
        org.assertj.core.api.Assertions.assertThat(card.select(".ar-unit-badge").eachText())
                .containsExactly("첫 유닛", "둘째 유닛");
    }

    @Test void unitFolderOpensByStoredUnitId() throws Exception {
        Artist seoyeon = new Artist();
        seoyeon.setName("윤서연");
        Artist hyerin = new Artist();
        hyerin.setName("정혜린");
        UnitMember later = new UnitMember();
        later.setArtist(hyerin);
        UnitMember earlier = new UnitMember();
        earlier.setArtist(seoyeon);
        Unit aura = new Unit();
        aura.setUnitId(17L);
        aura.setUnitName("Aura");
        aura.setVisible(true);
        aura.setUnitMembers(List.of(later, earlier));
        when(unitService.listVisibleForArchive()).thenReturn(List.of(aura));

        String html = mvc.perform(get("/archive").param("folder", "units")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        org.assertj.core.api.Assertions.assertThat(html).contains("folder&#x3D;unit-17");
        org.assertj.core.api.Assertions.assertThat(html).doesNotContain("members&#x3D;%EC%9C%A4%EC%84%9C%EC%97%B0");

        stubFolder(MediaType.VIDEO);
        String view = mvc.perform(get("/archive").param("folder", "unit-17").param("type", "video"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        org.assertj.core.api.Assertions.assertThat(view)
                .contains("ar-back-folders\" href=\"/archive?folder&#x3D;units\"")
                .contains(">Aura</h1>");
    }

    @Test void comboFolderFiltersPhotosByChemistryTreePair() throws Exception {
        ChemistryDto pair = ChemistryDto.builder()
                .id(3L).memberA("윤서연").memberB("정혜린").label("시선강탈").build();
        when(chemistryService.findAll()).thenReturn(List.of(pair));
        mvc.perform(get("/archive").param("folder", "combo-2").param("chem", "pair-3").param("on", "2026-03-15"))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/archive?folder=s&on=2026-03-15&members=%EC%9C%A4%EC%84%9C%EC%97%B0&members=%EC%A0%95%ED%98%9C%EB%A6%B0"));
    }

    @Test void comboThreeFolderRedirectsWithGroupMembers() throws Exception {
        ChemistryGroupDto trio = ChemistryGroupDto.builder()
                .id(8L).members(List.of("윤서연", "정혜린", "이지우")).label("트리오").build();
        when(chemistryGroupService.findAll()).thenReturn(List.of(trio));
        mvc.perform(get("/archive").param("folder", "combos").param("chem", "group-8"))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/archive?folder=s&members=%EC%9C%A4%EC%84%9C%EC%97%B0&members=%EC%A0%95%ED%98%9C%EB%A6%B0&members=%EC%9D%B4%EC%A7%80%EC%9A%B0"));
    }

    @Test void archivePageSendsNamedChemistryLabelsWithoutTheDropdown() throws Exception {
        ChemistryDto pair = ChemistryDto.builder()
                .id(3L).memberA("주빈").memberB("마유").label("심장\n마빈즈").build();
        ChemistryDto unlabeled = ChemistryDto.builder()
                .id(4L).memberA("윤서연").memberB("정혜린").label("  ").build();
        ChemistryGroupDto duplicate = ChemistryGroupDto.builder()
                .id(8L).members(List.of("마유", "주빈")).label("다른이름").build();
        ChemistryGroupDto trio = ChemistryGroupDto.builder()
                .id(9L).members(List.of("지연", "마유", "주빈")).label("A<b>셋").build();
        when(chemistryService.findAll()).thenReturn(List.of(pair, unlabeled));
        when(chemistryGroupService.findAll()).thenReturn(List.of(duplicate, trio));
        when(artistSnapshot.findAll()).thenReturn(List.of());
        stubFolder(MediaType.IMAGE, photo());

        String html = mvc.perform(get("/archive").param("folder", "s").param("members", "주빈").param("members", "마유"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        org.assertj.core.api.Assertions.assertThat(html).contains("id=\"archiveChemistryNames\"");
        org.assertj.core.api.Assertions.assertThat(html).contains("심장 · 마빈즈");
        org.assertj.core.api.Assertions.assertThat(html).contains("A\\u003cb>셋");
        org.assertj.core.api.Assertions.assertThat(html).contains("/triples/chemistry?pair=3", "/triples/chemistry?group=9");
        org.assertj.core.api.Assertions.assertThat(html).doesNotContain("다른이름");
        org.assertj.core.api.Assertions.assertThat(html).doesNotContain("윤서연");
        org.assertj.core.api.Assertions.assertThat(html).doesNotContain("id=\"archiveChem\"");
    }

    @Test void groupFolderWithMembersRequiresEverySelectedMember() throws Exception {
        when(artistSnapshot.findAll()).thenReturn(List.of());
        stubFolder(MediaType.IMAGE, photo());
        String html = mvc.perform(get("/archive").param("folder", "s").param("members", "윤서연").param("members", "정혜린"))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("전체 사진"))))
                .andExpect(content().string(not(containsString("ar-folder-title"))))
                .andExpect(content().string(not(containsString("ar-folder-label\">tripleS</span>"))))
                // 멤버가 고정된 폴더라 멤버 필터/피커는 숨긴다.
                .andExpect(content().string(not(containsString("id=\"archive-member-picker\""))))
                .andExpect(content().string(not(containsString("id=\"archiveChem\""))))
                .andReturn().getResponse().getContentAsString();
        // 정렬·검색 제출 때 members가 빠지지 않도록 숨은 필드로 남긴다.
        org.assertj.core.api.Assertions.assertThat(html)
                .contains("name=\"members\" value=\"윤서연\"")
                .contains("name=\"members\" value=\"정혜린\"");
        verify(archiveService).browseFolder(eq(MediaType.IMAGE), anyInt(), org.mockito.AdditionalMatchers.lt(Integer.MAX_VALUE), nullable(String.class),
                eq(List.of()),
                nullable(java.time.LocalDate.class), nullable(java.time.LocalDate.class),
                nullable(String.class), anyBoolean(), eq("s"),
                eq(List.of("윤서연", "정혜린")), any(), any());
    }

    @Test void cardMemberBadgesFollowVisibleCoverImage() throws Exception {
        Artist seoyeon = new Artist();
        seoyeon.setName("윤서연");
        seoyeon.setColor("#111111");
        Artist hyerin = new Artist();
        hyerin.setName("정혜린");
        hyerin.setColor("#222222");
        when(artistSnapshot.findAll()).thenReturn(List.of(seoyeon, hyerin));

        Archive shot = photo();
        shot.setFile(SavedMeta.builder().url("/uploads/cover-hyerin.jpg").mediaType(MediaType.IMAGE).build());
        shot.setImagesJson("[\"/uploads/first-seoyeon.jpg\",\"/uploads/cover-hyerin.jpg\"]");
        shot.setImageArtistsJson("[\"윤서연\",\"정혜린\"]");
        shot.setArtistNames("윤서연,정혜린");
        stubFolder(MediaType.IMAGE, shot);

        String html = mvc.perform(get("/archive").param("folder", "s")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        int meta = html.indexOf("class=\"ar-meta\"");
        int casts = html.indexOf("ar-card-members");
        org.assertj.core.api.Assertions.assertThat(meta).isGreaterThanOrEqualTo(0);
        org.assertj.core.api.Assertions.assertThat(casts).isGreaterThan(meta);
        String visibleBadges = html.substring(meta, casts);
        org.assertj.core.api.Assertions.assertThat(visibleBadges).contains("정혜린");
        org.assertj.core.api.Assertions.assertThat(visibleBadges).doesNotContain("윤서연");
        org.assertj.core.api.Assertions.assertThat(html).contains("/uploads/cover-hyerin.jpg?w");
    }

    @Test void imageUploadOfTwoMembersOpensTheCountFolder() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "a.jpg", "image/jpeg", new byte[]{1, 2});
        mvc.perform(multipart("/archive/upload/image").file(file)
                        .param("title", "사진").param("artistNames", "윤서연").param("artistNames", "정혜린")
                        .param("registeredOn", "2026-10-03").param("source", "COSMO_ROOM"))
                .andExpect(redirectedUrl("/archive?folder=s&members=%EC%9C%A4%EC%84%9C%EC%97%B0&members=%EC%A0%95%ED%98%9C%EB%A6%B0"));
    }

    @Test void imageUploadOfSixMembersOpensTheSixPlusFolder() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "a.jpg", "image/jpeg", new byte[]{1, 2});
        mvc.perform(multipart("/archive/upload/image").file(file)
                        .param("title", "사진")
                        .param("artistNames", "윤서연").param("artistNames", "정혜린").param("artistNames", "이지우")
                        .param("artistNames", "김채연").param("artistNames", "김유연").param("artistNames", "김수민")
                        .param("registeredOn", "2026-10-03").param("source", "COSMO_ROOM"))
                .andExpect(redirectedUrl("/archive?folder=s&members=%EC%9C%A4%EC%84%9C%EC%97%B0&members=%EC%A0%95%ED%98%9C%EB%A6%B0&members=%EC%9D%B4%EC%A7%80%EC%9A%B0&members=%EA%B9%80%EC%B1%84%EC%97%B0&members=%EA%B9%80%EC%9C%A0%EC%97%B0&members=%EA%B9%80%EC%88%98%EB%AF%BC"));
    }

    @Test void imageUploadOfThreeMembersOpensTheTrioFolder() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "a.jpg", "image/jpeg", new byte[]{1, 2});
        mvc.perform(multipart("/archive/upload/image").file(file)
                        .param("title", "사진")
                        .param("artistNames", "윤서연").param("artistNames", "정혜린").param("artistNames", "이지우")
                        .param("registeredOn", "2026-10-03").param("source", "COSMO_ROOM"))
                .andExpect(redirectedUrl("/archive?folder=s&members=%EC%9C%A4%EC%84%9C%EC%97%B0&members=%EC%A0%95%ED%98%9C%EB%A6%B0&members=%EC%9D%B4%EC%A7%80%EC%9A%B0"));
    }

    @Test void imageUploadOfFourMembersOpensTheQuartetFolder() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "a.jpg", "image/jpeg", new byte[]{1, 2});
        mvc.perform(multipart("/archive/upload/image").file(file)
                        .param("title", "사진")
                        .param("artistNames", "윤서연").param("artistNames", "정혜린").param("artistNames", "이지우")
                        .param("artistNames", "김채연")
                        .param("registeredOn", "2026-10-03").param("source", "COSMO_ROOM"))
                .andExpect(redirectedUrl("/archive?folder=s&members=%EC%9C%A4%EC%84%9C%EC%97%B0&members=%EC%A0%95%ED%98%9C%EB%A6%B0&members=%EC%9D%B4%EC%A7%80%EC%9A%B0&members=%EA%B9%80%EC%B1%84%EC%97%B0"));
    }

    @Test void filterResponseReusesThePublicResultsMarkup() throws Exception {
        when(artistSnapshot.findAll()).thenReturn(List.of());
        stubFolder(MediaType.IMAGE, photo());
        String full = mvc.perform(get("/archive").param("folder", "s"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String fragment = mvc.perform(get("/archive").param("folder", "s").header("X-Archive-Filter", "1"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        var fullPage = Jsoup.parse(full);
        var filterPage = Jsoup.parse(fragment);
        org.assertj.core.api.Assertions.assertThat(fragment.length()).isLessThan(full.length());
        for (String selector : List.of("#archiveGrid", ".ar-pager", ".ar-type-tabs", ".ar-source-tabs")) {
            org.assertj.core.api.Assertions.assertThat(filterPage.select(selector).html())
                    .isEqualTo(fullPage.select(selector).html());
        }
        org.assertj.core.api.Assertions.assertThat(fragment)
                .doesNotContain("archiveAdminMount", "archiveChemistryNames", "archiveDetail");
        mvc.perform(get("/archive/admin-forms")).andExpect(status().isForbidden());
    }

    @Test void adminImageArchiveShowsEditAndDelete() throws Exception {
        when(aboutAdminAuth.isAdmin(any(), any())).thenReturn(true);
        when(artistSnapshot.findAll()).thenReturn(List.of());
        stubFolder(MediaType.IMAGE, photo());

        mvc.perform(get("/archive").param("folder", "s")).andExpect(status().isOk())
                .andExpect(content().string(containsString("포토")))
                .andExpect(content().string(containsString("<h1>아카이브</h1>")))
                .andExpect(content().string(containsString("site-section-tabs")))
                .andExpect(content().string(containsString("사진 날짜")))
                .andExpect(content().string(containsString("data-date-clear")))
                .andExpect(content().string(containsString(">초기화</button>")))
                .andExpect(content().string(not(containsString(">지우기</button>"))))
                .andExpect(content().string(not(containsString("data-ymd-year"))))
                .andExpect(content().string(not(containsString("aria-label=\"연도\""))))
                .andExpect(content().string(containsString("/images/archive/member-logos/1.png")))
                .andExpect(content().string(containsString("id=\"archive-member-picker\"")))
                .andExpect(content().string(not(containsString("archive-symbol-picker"))))
                .andExpect(content().string(containsString("archive-symbol-chip")))
                .andExpect(content().string(containsString("data-archive-edit")))
                .andExpect(content().string(containsString("data-archive-delete")))
                .andExpect(content().string(containsString("사진 추가")))
                .andExpect(content().string(containsString("멤버 선택")))
                .andExpect(content().string(containsString("data-url=\"/uploads/a.jpg\"")))
                .andExpect(content().string(containsString("data-images=")))
                .andExpect(content().string(not(containsString("목록에서 보이는 모습 · 카드/슬라이드"))))
                .andExpect(content().string(containsString("id=\"archiveDetail\"")))
                .andExpect(content().string(not(containsString("더보기"))))
                .andExpect(content().string(not(containsString("원본 열기"))))
                .andExpect(content().string(containsString("id=\"archiveDetailOriginalLink\" target=\"_blank\" rel=\"noopener\">원본 보기</a>")))
                .andExpect(content().string(not(containsString("class=\"ar-submit\">검색</button>"))))
                .andExpect(content().string(containsString(">초기화</a>")))
                .andExpect(content().string(not(containsString("ar-search-go"))))
                .andExpect(content().string(containsString("멤버번호 낮은순")))
                .andExpect(content().string(containsString("멤버번호 높은순")))
                .andExpect(content().string(not(containsString("제목순"))))
                .andExpect(content().string(not(containsString("첫멤버순"))))
                .andExpect(content().string(containsString("data-member-order=\"0\"")))
                .andExpect(content().string(not(containsString("data-stats-toggle"))))
                .andExpect(content().string(not(containsString("id=\"archiveStats\""))))
                .andExpect(content().string(containsString("is-copy-only")))
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
                .andExpect(content().string(containsString("ar-source-tabs")))
                .andExpect(content().string(containsString("ar-folder-tabs")))
                .andExpect(content().string(containsString("unit-member-chip")));
        mvc.perform(get("/archive/admin-forms"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("실제로 올라온 날짜를 선택하세요.")))
                .andExpect(content().string(containsString("type=\"date\"")))
                .andExpect(content().string(containsString("name=\"registeredOn\"")))
                .andExpect(content().string(containsString("archiveEditForm")))
                .andExpect(content().string(containsString("id=\"uploadModal\" hidden")))
                .andExpect(content().string(containsString("archive-upload-picker")))
                .andExpect(content().string(containsString("data-upload-url=\"/archive/upload/image\"")))
                .andExpect(content().string(containsString("사진 편집")))
                .andExpect(content().string(containsString("파일 선택")))
                .andExpect(content().string(containsString("data-file-drop=\"multi\"")))
                .andExpect(content().string(containsString("multiple")))
                .andExpect(content().string(containsString("버튼으로 순서 변경")))
                .andExpect(content().string(containsString("코스모룸")))
                .andExpect(content().string(containsString("코스모톡/프롬")))
                .andExpect(content().string(containsString("공식 SNS")))
                .andExpect(content().string(containsString("화보/매거진")))
                .andExpect(content().string(containsString("외부 컨텐츠/방송")))
                .andExpect(content().string(containsString("value=\"COSMO_ROOM\"")))
                .andExpect(content().string(containsString("value=\"MAGAZINE\"")))
                .andExpect(content().string(not(containsString("기타/팬메이드"))))
                .andExpect(content().string(not(containsString("value=\"FANCAM\""))))
                .andExpect(content().string(not(containsString("직찍"))))
                .andExpect(content().string(not(containsString(">미분류<"))));
    }

    @Test void sourceTabsOnlyShowSourcesWithPostsAndKeepAllUploadOptions() throws Exception {
        Archive polaroid = photo();
        polaroid.setSource("POLAROID");
        stubFolder(MediaType.IMAGE, polaroid);
        String html = mvc.perform(get("/archive").param("folder", "s"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        org.assertj.core.api.Assertions.assertThat(html)
                .containsAnyOf("source=polaroid", "source&#x3D;polaroid")
                .doesNotContain("source=cosmo_room", "source=magazine",
                        "source&#x3D;cosmo_room", "source&#x3D;magazine")
                .doesNotContain("value=\"POLAROID\"", "value=\"COSMO_ROOM\"");
        when(aboutAdminAuth.isAdmin(any(), any())).thenReturn(true);
        mvc.perform(get("/archive/admin-forms"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("value=\"POLAROID\"")))
                .andExpect(content().string(containsString("value=\"COSMO_ROOM\"")));
    }

    @Test void photoAndVideoTabsUseTheirOwnPostsAndShowEmptySourcesOnlyForAdmin() throws Exception {
        when(archiveService.pageByType(any(), anyInt(), anyInt(), nullable(String.class), any(),
                nullable(java.time.LocalDate.class), nullable(java.time.LocalDate.class), nullable(String.class),
                anyBoolean(), anyBoolean(), any())).thenReturn(Page.empty());
        var start = java.time.LocalDate.of(2026, 11, 22);
        var end = start.plusDays(2);
        when(archiveService.availableSources(eq(MediaType.IMAGE), eq("앨범"), eq(List.of("윤서연")), eq(start), eq(end)))
                .thenReturn(java.util.Set.of("POLAROID"));
        Archive video = photo();
        video.setSource("OFFICIAL_SNS");
        stubFolder(MediaType.VIDEO, video);

        var photos = Jsoup.parse(mvc.perform(get("/archive/all").param("q", "앨범")
                        .param("members", "윤서연").param("from", start.toString()).param("to", end.toString()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        var videos = Jsoup.parse(mvc.perform(get("/archive").param("folder", "s").param("type", "video"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        org.assertj.core.api.Assertions.assertThat(photos.select(".ar-source-tabs a").eachText())
                .containsExactly("전체", "폴라로이드");
        org.assertj.core.api.Assertions.assertThat(videos.select(".ar-source-tabs a").eachText())
                .containsExactly("전체", "공식 SNS");

        when(aboutAdminAuth.isAdmin(any(), any())).thenReturn(true);
        var admin = Jsoup.parse(mvc.perform(get("/archive").param("folder", "s").param("type", "video").header("X-Archive-Filter", "1"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        org.assertj.core.api.Assertions.assertThat(admin.select(".ar-source-tabs a.ar-source-tab-empty").eachText())
                .contains("폴라로이드", "코스모룸").doesNotContain("공식 SNS");
    }

    @Test void allArchiveFiltersResultsBySelectedSource() throws Exception {
        when(archiveService.pageByType(eq(MediaType.IMAGE), anyInt(), anyInt(), isNull(), any(), isNull(), isNull(),
                eq("POLAROID"), anyBoolean(), anyBoolean(), any())).thenReturn(Page.empty());

        mvc.perform(get("/archive/all").param("source", "polaroid")).andExpect(status().isOk());
        verify(archiveService).pageByType(eq(MediaType.IMAGE), anyInt(), anyInt(), isNull(), any(), isNull(), isNull(),
                eq("POLAROID"), anyBoolean(), anyBoolean(), any());
    }

    @Test void oldTeaserLinkSelectsTheTeaserBehindSource() throws Exception {
        Archive teaserBehind = photo();
        teaserBehind.setSource("TEASER_BEHIND");
        stubFolder(MediaType.IMAGE, teaserBehind);

        String html = mvc.perform(get("/archive").param("folder", "s").param("source", "teaser"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        var page = Jsoup.parse(html);
        org.assertj.core.api.Assertions.assertThat(page.select(".ar-source-tabs a[href$=teaser_behind]"))
                .hasSize(1).first().satisfies(tab -> {
                    org.assertj.core.api.Assertions.assertThat(tab.text()).isEqualTo("티저/비하인드");
                    org.assertj.core.api.Assertions.assertThat(tab.hasClass("is-active")).isTrue();
                });
        when(aboutAdminAuth.isAdmin(any(), any())).thenReturn(true);
        var adminForms = Jsoup.parse(mvc.perform(get("/archive/admin-forms"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        org.assertj.core.api.Assertions.assertThat(adminForms.select("option[value=TEASER]")).isEmpty();
        org.assertj.core.api.Assertions.assertThat(adminForms.select("option[value=TEASER_BEHIND]").eachText())
                .isNotEmpty().containsOnly("티저/비하인드");
        verify(archiveService).browseFolder(eq(MediaType.IMAGE), anyInt(), anyInt(), nullable(String.class), any(),
                nullable(java.time.LocalDate.class), nullable(java.time.LocalDate.class),
                eq("TEASER_BEHIND"), anyBoolean(), any(), any(), any(), any());
    }

    @Test void externalSourceOffersPhotoDensityDropdown() throws Exception {
        Archive external = photo();
        external.setSource("EXTERNAL");
        stubFolder(MediaType.IMAGE, external);

        String html = mvc.perform(get("/archive").param("folder", "s").param("source", "external").param("size", "20"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        org.assertj.core.api.Assertions.assertThat(html)
                .contains("class=\"ar-density\"")
                .contains("id=\"archivePageSize\"")
                .contains("value=\"20\" selected")
                .contains(">20개</option>")
                .contains(">10개</option>")
                .contains(">50개</option>");
        org.assertj.core.api.Assertions.assertThat(html).doesNotContain("size=9");
        verify(archiveService).browseFolder(eq(MediaType.IMAGE), eq(0), eq(20), nullable(String.class), any(),
                nullable(java.time.LocalDate.class), nullable(java.time.LocalDate.class),
                eq("EXTERNAL"), anyBoolean(), any(), any(), any(), any());
    }

    @Test void archiveMemberPickerUsesPortraitsNotSymbolLogos() throws Exception {
        var member = new Artist();
        member.setName("윤서연");
        member.setEngName("SeoYeon");
        member.setColor("#22aeff");
        member.setProfileImage("/uploads/portraits/seoyeon.jpg");
        when(artistSnapshot.findAll()).thenReturn(List.of(member));
        stubFolder(MediaType.IMAGE, photo());
        String html = mvc.perform(get("/archive").param("folder", "s")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        int start = html.indexOf("id=\"archive-member-picker\"");
        int end = html.indexOf("</div>", start);
        String picker = html.substring(start, end);
        org.assertj.core.api.Assertions.assertThat(picker).contains("/uploads/portraits/seoyeon.jpg");
        org.assertj.core.api.Assertions.assertThat(picker).doesNotContain("/images/archive/member-logos/1.png");
        org.assertj.core.api.Assertions.assertThat(html).contains("/images/archive/member-logos/1.png");
        org.assertj.core.api.Assertions.assertThat(html).doesNotContain("archive-symbol-picker");
    }

    @Test void folderViewKeepsFolderOnTabsAndHidesSoloMemberFilter() throws Exception {
        when(artistSnapshot.findAll()).thenReturn(List.of());
        stubFolder(MediaType.IMAGE, photo());
        mvc.perform(get("/archive").param("folder", "s1").param("source", "cosmo_room").param("q", "heart"))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/archive?folder=s&source=cosmo_room&q=heart&members=%EC%9C%A4%EC%84%9C%EC%97%B0"));
        String html = mvc.perform(get("/archive").param("folder", "s").param("source", "cosmo_room"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("ar-folder-tabs")))
                .andExpect(content().string(containsString("ar-source-tabs")))
                .andExpect(content().string(containsString("코스모룸")))
                .andExpect(content().string(not(containsString("직찍"))))
                .andExpect(content().string(not(containsString("기타/팬메이드"))))
                .andReturn().getResponse().getContentAsString();
        org.assertj.core.api.Assertions.assertThat(html).containsAnyOf("source=cosmo_room", "source&#x3D;cosmo_room");
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
        org.assertj.core.api.Assertions.assertThat(html).contains("id=\"archive-member-picker\"");
        org.assertj.core.api.Assertions.assertThat(html).contains(">멤버 선택<");
        org.assertj.core.api.Assertions.assertThat(html).contains("멤버번호 낮은순");
        org.assertj.core.api.Assertions.assertThat(html).contains("멤버번호 높은순");
        org.assertj.core.api.Assertions.assertThat(html).doesNotContain("제목순");
    }

    @Test void emptyFolderShowsPageOneAndMemberTitle() throws Exception {
        when(artistSnapshot.findAll()).thenReturn(List.of());
        stubFolder(MediaType.IMAGE);
        mvc.perform(get("/archive").param("folder", "s19"))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/archive?folder=s&members=%EC%A0%95%ED%95%98%EC%97%B0"));
        String html = mvc.perform(get("/archive").param("folder", "s")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        org.assertj.core.api.Assertions.assertThat(html).doesNotContain("전체 사진");
        org.assertj.core.api.Assertions.assertThat(html).doesNotContain("ar-folder-title");
        org.assertj.core.api.Assertions.assertThat(html).doesNotContain("ar-folder-label\">tripleS</span>");
        org.assertj.core.api.Assertions.assertThat(html).doesNotContain("ar-folder-title is-solo");
        org.assertj.core.api.Assertions.assertThat(html).contains(">날짜<");
        org.assertj.core.api.Assertions.assertThat(html).contains("objekt-filter");
        org.assertj.core.api.Assertions.assertThat(html).contains("arrow_drop_down");
        org.assertj.core.api.Assertions.assertThat(html).contains(">전체<");
        org.assertj.core.api.Assertions.assertThat(html).doesNotContain(">이 페이지<");
        org.assertj.core.api.Assertions.assertThat(html).contains("← 목록으로");
        org.assertj.core.api.Assertions.assertThat(html).doesNotContain(">폴더 목록<");
        org.assertj.core.api.Assertions.assertThat(html).doesNotContain("이 페이지에서 조건에 맞는");
        org.assertj.core.api.Assertions.assertThat(html).doesNotContain("id=\"archiveNone\"");
        org.assertj.core.api.Assertions.assertThat(html).contains("ar-page-jump-meta");
        org.assertj.core.api.Assertions.assertThat(html).contains(">1</span>");
        org.assertj.core.api.Assertions.assertThat(html).contains("is-current");
        org.assertj.core.api.Assertions.assertThat(html).doesNotContain("value=\"COSMO_ROOM\"");
        org.assertj.core.api.Assertions.assertThat(html).doesNotContain("직찍");
        org.assertj.core.api.Assertions.assertThat(html).doesNotContain("기타/팬메이드");
        org.assertj.core.api.Assertions.assertThat(html).doesNotContain(">미분류<");
        org.assertj.core.api.Assertions.assertThat(html).contains("수정하기");
        org.assertj.core.api.Assertions.assertThat(html).contains("삭제하기");
        org.assertj.core.api.Assertions.assertThat(html).doesNotContain("ar-stat-filter");
        org.assertj.core.api.Assertions.assertThat(html).contains("class=\"ar-source-badge\"");
        org.assertj.core.api.Assertions.assertThat(html).doesNotContain("ar-source-field");
        org.assertj.core.api.Assertions.assertThat(html).contains("lang=\"ko\"");
        org.assertj.core.api.Assertions.assertThat(html).doesNotContain("출처 미분류");
        org.assertj.core.api.Assertions.assertThat(html).doesNotContain("is-violet");
    }


    @Test void archiveMemberOptionsSurviveProfileImageErrors() throws Exception {
        var artist = new Artist();
        artist.setName("윤서연");
        artist.setProfileImage("/missing-profile.jpg");
        when(artistSnapshot.findAll()).thenReturn(List.of(artist));
        stubFolder(MediaType.IMAGE);
        var html = mvc.perform(get("/archive").param("folder", "s"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        var option = org.jsoup.Jsoup.parse(html).selectFirst("#archiveMemberPanel .mpp-item");
        org.assertj.core.api.Assertions.assertThat(option.attr("data-keep-option")).isEqualTo("true");
    }

    @Test void resetButtonFollowsActiveFiltersAndSearchStatIsRemoved() throws Exception {
        when(artistSnapshot.findAll()).thenReturn(List.of());
        stubFolder(MediaType.IMAGE);
        var baseline = mvc.perform(get("/archive").param("folder", "s"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        var baseDoc = org.jsoup.Jsoup.parse(baseline);
        org.assertj.core.api.Assertions.assertThat(baseDoc.selectFirst(".ar-reset").hasAttr("hidden")).isTrue();
        org.assertj.core.api.Assertions.assertThat(baseDoc.select(".ar-stat-filter")).isEmpty();
        org.assertj.core.api.Assertions.assertThat(baseDoc.select("#archiveStats")).isEmpty();
        for (String[] filter : new String[][] {{"q", "심장"},
                {"from", "2026-11-17"}, {"to", "2026-11-17"}, {"on", "2026-11-17"}, {"source", "cosmo_room"}}) {
            var html = mvc.perform(get("/archive").param("folder", "s").param(filter[0], filter[1]))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            var doc = org.jsoup.Jsoup.parse(html);
            org.assertj.core.api.Assertions.assertThat(doc.selectFirst(".ar-reset").hasAttr("hidden"))
                    .as(filter[0]).isFalse();
            org.assertj.core.api.Assertions.assertThat(doc.selectFirst(".ar-reset").attr("href")).isEqualTo("/archive?folder=s");
        }
    }

    @Test void memberAndUnitFoldersHideMemberFilterAndMemberOnlyReset() throws Exception {
        when(artistSnapshot.findAll()).thenReturn(List.of());
        stubFolder(MediaType.IMAGE);
        String solo = mvc.perform(get("/archive").param("folder", "s").param("members", "이지우"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        var soloDoc = org.jsoup.Jsoup.parse(solo);
        org.assertj.core.api.Assertions.assertThat(soloDoc.select(".ar-member-filter")).isEmpty();
        org.assertj.core.api.Assertions.assertThat(soloDoc.selectFirst(".ar-reset").hasAttr("hidden")).isTrue();

        String filtered = mvc.perform(get("/archive").param("folder", "s").param("members", "이지우").param("q", "심장"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        var filteredDoc = org.jsoup.Jsoup.parse(filtered);
        org.assertj.core.api.Assertions.assertThat(filteredDoc.select(".ar-member-filter")).isEmpty();
        org.assertj.core.api.Assertions.assertThat(filteredDoc.selectFirst(".ar-reset").hasAttr("hidden")).isFalse();
        org.assertj.core.api.Assertions.assertThat(filteredDoc.selectFirst(".ar-reset").attr("href"))
                .isEqualTo("/archive?folder=s&members=%EC%9D%B4%EC%A7%80%EC%9A%B0");

        String group = mvc.perform(get("/archive").param("folder", "s"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        org.assertj.core.api.Assertions.assertThat(org.jsoup.Jsoup.parse(group).select(".ar-member-filter")).isNotEmpty();
    }

    @Test void singleMemberFolderTitleUsesSoloMarkup() throws Exception {
        when(artistSnapshot.findAll()).thenReturn(List.of());
        stubFolder(MediaType.IMAGE, photo());
        String html = mvc.perform(get("/archive").param("folder", "s").param("members", "윤서연"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        org.assertj.core.api.Assertions.assertThat(html).contains("ar-folder-title is-solo");
        org.assertj.core.api.Assertions.assertThat(html).contains("ar-folder-num\">S1</span>");
        org.assertj.core.api.Assertions.assertThat(html).contains("ar-folder-name\">윤서연</span>");
    }

    @Test void photoCardShowsDateBesideMembers() throws Exception {
        Archive shot = photo();
        shot.setRegisteredOn(java.time.LocalDate.of(2026, 11, 16));
        shot.setSource("COSMO_ROOM");
        when(artistSnapshot.findAll()).thenReturn(List.of());
        stubFolder(MediaType.IMAGE, shot);
        String html = mvc.perform(get("/archive").param("folder", "s")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        int body = html.indexOf("class=\"ar-body\"");
        int meta = html.indexOf("class=\"ar-meta\"");
        int members = html.indexOf("ar-card-members");
        String cardBody = html.substring(body, meta);
        String row = html.substring(meta, members);
        org.assertj.core.api.Assertions.assertThat(cardBody).contains("ar-source-badge");
        org.assertj.core.api.Assertions.assertThat(cardBody).contains("코스모룸");
        org.assertj.core.api.Assertions.assertThat(row).contains("ar-card-date");
        org.assertj.core.api.Assertions.assertThat(row).contains("2026/11/16");
        org.assertj.core.api.Assertions.assertThat(html).contains("data-source-label=\"코스모룸\"");
    }

    @Test void guestImageArchiveHidesEditAndDelete() throws Exception {
        when(aboutAdminAuth.isAdmin(any(), any())).thenReturn(false);
        when(artistSnapshot.findAll()).thenReturn(List.of());
        stubFolder(MediaType.IMAGE, photo());

        mvc.perform(get("/archive").param("folder", "s")).andExpect(status().isOk())
                .andExpect(content().string(containsString("포토")))
                .andExpect(content().string(containsString("data-admin-only")))
                .andExpect(content().string(containsString("data-archive-edit")))
                .andExpect(content().string(containsString("사진 추가")));
    }

    @Test void videoArchiveUsesSameLiveFields() throws Exception {
        when(aboutAdminAuth.isAdmin(any(), any())).thenReturn(false);
        when(aboutAdminAuth.isAdmin(any(), any())).thenReturn(true);
        when(artistSnapshot.findAll()).thenReturn(List.of());
        Archive video = photo();
        video.setTitle("무대영상");
        video.setThumbnailUrl("/uploads/poster.webp");
        video.setFile(SavedMeta.builder().url("/uploads/a.mp4").mediaType(MediaType.VIDEO).build());
        stubFolder(MediaType.VIDEO, video);

        String html = mvc.perform(get("/archive").param("type", "video").param("folder", "s")).andExpect(status().isOk())
                .andExpect(content().string(containsString("data-poster=\"/uploads/poster.webp\"")))
                .andExpect(content().string(containsString("src=\"/uploads/poster.webp\"")))
                .andExpect(content().string(containsString("data-archive-video")))
                .andExpect(content().string(containsString("id=\"archiveVideoPlayer\"")))
                .andExpect(content().string(not(containsString("<video controls preload=\"metadata\""))))
                .andExpect(content().string(containsString("무대영상")))
                .andExpect(content().string(containsString("<h1>아카이브</h1>")))
                .andExpect(content().string(containsString("영상 날짜")))
                .andExpect(content().string(containsString("data-admin-only")))
                .andExpect(content().string(containsString("영상 추가")))
                .andExpect(content().string(containsString("data-url=\"/uploads/a.mp4\"")))
                .andReturn().getResponse().getContentAsString();
        int gridStart = html.indexOf("id=\"archiveGrid\"");
        org.assertj.core.api.Assertions.assertThat(html.substring(gridStart, html.indexOf("</section>", gridStart)))
                .doesNotContain("data-archive-edit", "data-archive-delete");
        org.assertj.core.api.Assertions.assertThat(html.substring(html.indexOf("id=\"archiveVideoPlayer\"")))
                .contains("data-archive-edit", "data-archive-delete");
        mvc.perform(get("/archive/admin-forms").param("type", "video"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("name=\"thumbnail\"")))
                .andExpect(content().string(containsString("data-upload-url=\"/archive/upload/video\"")))
                .andExpect(content().string(containsString("archive-upload-picker")))
                .andExpect(content().string(containsString("id=\"editModal\" hidden")))
                .andExpect(content().string(containsString("목록에서 보이는 모습")));
    }

    @Test void adminCanUpdateAndDelete() throws Exception {
        when(archiveService.delete(7L)).thenReturn(true);
        MockMultipartFile file = new MockMultipartFile("file", "a.jpg", "image/jpeg", new byte[]{1, 2});

        mvc.perform(multipart("/archive/7").file(file).param("description", "새 설명").param("artistNames", "서연").param("registeredOn", "2026-10-01").param("source", "COSMO_ROOM"))
                .andExpect(status().isOk());
        verify(archiveService).update(eq(7L), eq(""), anyList(), eq("서연"), isNull(), eq(false), eq(java.time.LocalDate.of(2026, 10, 1)), eq("COSMO_ROOM"), eq("새 설명"), isNull(), isNull(), isNull());
        verify(aboutAdminAuth).requireAdmin(any(), any());

        mvc.perform(delete("/archive/7")).andExpect(status().isOk());
        verify(archiveService).delete(7L);
    }

    @Test void videoUploadAcceptsOptionalThumbnail() throws Exception {
        var video = new MockMultipartFile("file", "video.mp4", "video/mp4", new byte[]{1});
        var thumbnail = new MockMultipartFile("thumbnail", "poster.jfif", "application/octet-stream", new byte[]{2});
        mvc.perform(multipart("/archive/upload/video").file(video).file(thumbnail)
                        .param("title", "영상").param("artistNames", "서연").param("registeredOn", "2026-10-02").param("source", "COSMO_ROOM"))
                .andExpect(redirectedUrl("/archive?type=video&folder=s&members=%EC%9C%A4%EC%84%9C%EC%97%B0"));
        verify(archiveService).uploadVideoOnly(any(), eq("영상"), eq(MediaType.VIDEO), eq("서연"), any(), eq(java.time.LocalDate.of(2026, 10, 2)), eq("COSMO_ROOM"));
        verify(aboutAdminAuth).requireAdmin(any(), any());
    }

    @ParameterizedTest
    @EnumSource(value = MediaType.class, names = {"IMAGE", "VIDEO"})
    void archivesUseDisplayNumbersAndIndividualMemberColors(MediaType type) throws Exception {
        var member = new Artist();
        member.setArtistId(24L);
        member.setName("지연");
        member.setColor("#ffab62");
        when(artistSnapshot.findAll()).thenReturn(List.of(member));
        Archive first = photo(), second = photo();
        first.setId(3L);
        first.setArtistNames("지연, 게스트");
        second.setId(99L);
        stubFolder(type, first, second);
        mvc.perform(get("/archive").param("type", type == MediaType.IMAGE ? "image" : "video").param("folder", "s"))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("data-archive-number"))))
                .andExpect(content().string(containsString("--member-color: #ffab62;")))
                .andExpect(content().string(containsString("--member-color: #b9cac4;")))
                .andExpect(content().string(containsString("data-id=\"99\"")))
                .andExpect(content().string(not(containsString(">#99</span>"))));
    }

    @Test void listingOmitsFrontNumbersAndKeepsPageOffset() throws Exception {
        when(artistSnapshot.findAll()).thenReturn(List.of());
        when(archiveService.browseFolder(eq(MediaType.IMAGE), anyInt(), anyInt(), nullable(String.class), any(),
                nullable(java.time.LocalDate.class), nullable(java.time.LocalDate.class),
                nullable(String.class), anyBoolean(), any(), any(), any(), any()))
                .thenReturn(new ArchiveService.FolderBrowse(
                        new PageImpl<>(List.of(photo()), PageRequest.of(1, 9), 20), java.util.Set.of()));
        mvc.perform(get("/archive").param("page", "1").param("folder", "s"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("data-number-offset=\"9\"")))
                .andExpect(content().string(not(containsString("data-archive-number"))))
                .andExpect(content().string(not(containsString("id=\"archiveTrack\""))));
    }

    @Test void photoCardsShowTitleThenAllMemberLogos() throws Exception {
        when(artistSnapshot.findAll()).thenReturn(List.of());
        Archive group = photo();
        group.setArtistNames("윤서연, 윤서연, 정혜린, 이지우, <김채연>");
        Archive solo = photo();
        solo.setId(8L);
        solo.setTitle("지서연");
        solo.setArtistNames("서연 \"A\"");
        when(archiveService.pageByType(eq(MediaType.IMAGE), anyInt(), anyInt(), isNull(), any(), isNull(), isNull(),
                isNull(), anyBoolean(), anyBoolean(), any()))
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



    @Test void exactPhotoDateFilterUsesRegisteredOn() throws Exception {
        when(artistSnapshot.findAll()).thenReturn(List.of());
        stubFolder(MediaType.IMAGE, photo());
        mvc.perform(get("/archive").param("folder", "s").param("on", "2026-03-15"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("name=\"on\"")))
                .andExpect(content().string(containsString("value=\"2026-03-15\"")));
        verify(archiveService).browseFolder(eq(MediaType.IMAGE), anyInt(), org.mockito.AdditionalMatchers.lt(Integer.MAX_VALUE), any(), any(),
                eq(java.time.LocalDate.of(2026, 3, 15)), eq(java.time.LocalDate.of(2026, 3, 15)),
                isNull(), eq(false), eq("s"), any(), any(), any());
    }

    @Test void photoBrowsePageListsAllPhotosWithoutCarousel() throws Exception {
        when(artistSnapshot.findAll()).thenReturn(List.of());
        Archive group = photo();
        group.setArtistNames("윤서연, 정혜린");
        when(archiveService.pageByType(eq(MediaType.IMAGE), anyInt(), anyInt(), isNull(), any(), isNull(), isNull(),
                isNull(), anyBoolean(), anyBoolean(), any()))
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
        when(artistSnapshot.findAll()).thenReturn(List.of(member));
        stubFolder(MediaType.IMAGE, photo());
        mvc.perform(get("/archive").param("folder", "s"))
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
                        .param("description", "현장 컷").param("artistNames", "서연").param("registeredOn", "2026-10-03").param("source", "COSMO_ROOM"))
                .andExpect(redirectedUrl("/archive?folder=s&members=%EC%9C%A4%EC%84%9C%EC%97%B0"));
        verify(archiveService).uploadImages(any(), eq(""), eq("서연"), eq(java.time.LocalDate.of(2026, 10, 3)), eq("COSMO_ROOM"), eq("현장 컷"), eq(0));
        verify(aboutAdminAuth).requireAdmin(any(), any());
    }

    @Test void imageUploadAllowsEmptyDescription() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "a.jpg", "image/jpeg", new byte[]{1, 2});
        mvc.perform(multipart("/archive/upload/image").file(file)
                        .param("artistNames", "서연").param("registeredOn", "2026-10-03").param("source", "COSMO_ROOM"))
                .andExpect(redirectedUrl("/archive?folder=s&members=%EC%9C%A4%EC%84%9C%EC%97%B0"));
        verify(archiveService).uploadImages(any(), eq(""), eq("서연"), eq(java.time.LocalDate.of(2026, 10, 3)), eq("COSMO_ROOM"), isNull(), eq(0));
        verify(aboutAdminAuth).requireAdmin(any(), any());
    }

    @Test void imageUploadAcceptsMultipleFiles() throws Exception {
        MockMultipartFile first = new MockMultipartFile("file", "a.jpg", "image/jpeg", new byte[]{1, 2});
        MockMultipartFile second = new MockMultipartFile("file", "b.png", "image/png", new byte[]{3, 4});
        mvc.perform(multipart("/archive/upload/image").file(first).file(second)
                        .param("description", "묶음").param("coverIndex", "1").param("artistNames", "서연").param("registeredOn", "2026-10-03").param("source", "COSMO_ROOM"))
                .andExpect(redirectedUrl("/archive?folder=s&members=%EC%9C%A4%EC%84%9C%EC%97%B0"));
        verify(archiveService, times(1)).uploadImages(any(), eq(""), eq("서연"),
                eq(java.time.LocalDate.of(2026, 10, 3)), eq("COSMO_ROOM"), eq("묶음"), eq(1));
        verify(aboutAdminAuth).requireAdmin(any(), any());
    }

    @Test void imageUploadRejectsDuplicatePost() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "a.jpg", "image/jpeg", new byte[]{1, 2});
        org.mockito.Mockito.doThrow(new IllegalArgumentException("같은 날짜·출처·설명의 게시글이 이미 있습니다."))
                .when(archiveService).uploadImages(any(), eq(""), eq("서연"),
                        eq(java.time.LocalDate.of(2026, 10, 3)), eq("COSMO_ROOM"), eq("현장 컷"), eq(0));
        mvc.perform(multipart("/archive/upload/image").file(file)
                        .param("description", "현장 컷").param("artistNames", "서연").param("registeredOn", "2026-10-03").param("source", "COSMO_ROOM"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("같은 날짜·출처·설명")));
    }

    /** 구버전 관리자 탭이 보내는 이미지 업로드 필드 계약. */
    @Test void imageUploadContractAcceptsCurrentFieldSet() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "a.jpg", "image/jpeg", new byte[]{1, 2});
        mvc.perform(multipart("/archive/upload/image").file(file)
                        .param("title", "").param("description", "설명")
                        .param("artistNames", "서연").param("registeredOn", "2026-10-03")
                        .param("source", "COSMO_ROOM").param("coverIndex", "0")
                        .header("Idempotency-Key", "archive-upload-contract-1"))
                .andExpect(redirectedUrl("/archive?folder=s&members=%EC%9C%A4%EC%84%9C%EC%97%B0"));
        verify(idempotencyService).claimOrReplay(eq(IdempotencyService.SCOPE_ARCHIVE_UPLOAD),
                eq("archive-upload-contract-1"));
        verify(idempotencyService).complete(eq(IdempotencyService.SCOPE_ARCHIVE_UPLOAD),
                eq("archive-upload-contract-1"), eq("/archive?folder=s&members=%EC%9C%A4%EC%84%9C%EC%97%B0"));
    }

    @Test void imageUploadStillAcceptsLegacyUnitId() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "a.jpg", "image/jpeg", new byte[]{1, 2});
        when(archiveService.uploadImages(anyList(), anyString(), anyString(), any(), anyString(), isNull(), eq(0)))
                .thenReturn(photo());
        mvc.perform(multipart("/archive/upload/image").file(file)
                        .param("registeredOn", "2026-10-03").param("source", "COSMO_ROOM")
                        .param("unitId", "7"))
                .andExpect(redirectedUrl("/archive?folder=unit-7"));
        verify(archiveService).assignUnitIds(7L, Set.of(7L));
    }

    @Test void updateAcceptsRepeatedUnitIdsAndIgnoresTheLegacyParameterWhenBothArePresent() throws Exception {
        mvc.perform(multipart("/archive/7").param("registeredOn", "2026-10-03")
                        .param("unitIds", "7", "8", "7", "invalid", "0").param("unitId", "99"))
                .andExpect(status().isOk());
        verify(archiveService).assignUnitIds(7L, Set.of(7L, 8L));
    }

    @Test void imageUploadReplaysCompletedIdempotencyKey() throws Exception {
        when(idempotencyService.claimOrReplay(IdempotencyService.SCOPE_ARCHIVE_UPLOAD, "same-key"))
                .thenReturn(Optional.of("/archive?folder=s&members=%EC%9C%A4%EC%84%9C%EC%97%B0"));
        MockMultipartFile file = new MockMultipartFile("file", "a.jpg", "image/jpeg", new byte[]{1, 2});
        mvc.perform(multipart("/archive/upload/image").file(file)
                        .param("registeredOn", "2026-10-03").param("source", "COSMO_ROOM")
                        .header("Idempotency-Key", "same-key"))
                .andExpect(redirectedUrl("/archive?folder=s&members=%EC%9C%A4%EC%84%9C%EC%97%B0"));
        verify(archiveService, times(0)).uploadImages(any(), any(), any(), any(), any(), any(), anyInt());
    }

    /** 구버전 수정 폼 필드 계약 (multipart POST /archive/{id}). */
    @Test void updateContractAcceptsCurrentFieldSet() throws Exception {
        mvc.perform(multipart("/archive/7")
                        .param("title", "").param("description", "새 설명")
                        .param("artistNames", "서연").param("registeredOn", "2026-10-01")
                        .param("source", "COSMO_ROOM")
                        .param("keepImageUrlsJson", "[\"/uploads/a.jpg\"]")
                        .param("imageOrderJson", "[\"/uploads/a.jpg\"]")
                        .param("coverIndex", "0")
                        .param("clearThumbnail", "false"))
                .andExpect(status().isOk());
        verify(archiveService).update(eq(7L), eq(""), nullable(List.class), eq("서연"), isNull(), eq(false),
                eq(java.time.LocalDate.of(2026, 10, 1)), eq("COSMO_ROOM"),
                eq("새 설명"), eq("[\"/uploads/a.jpg\"]"), eq("[\"/uploads/a.jpg\"]"), eq(0));
    }

    @Test void deleteContractReturnsOk() throws Exception {
        when(archiveService.delete(7L)).thenReturn(true);
        mvc.perform(delete("/archive/7")).andExpect(status().isOk());
    }

    private static Archive photo() {
        Archive archive = new Archive();
        archive.setId(7L);
        archive.setTitle("포토");
        archive.setSource("COSMO_ROOM");
        archive.setArtistNames("서연");
        archive.setFile(SavedMeta.builder().url("/uploads/a.jpg").mediaType(MediaType.IMAGE).build());
        archive.setImagesJson("[\"/uploads/a.jpg\"]");
        return archive;
    }
}
