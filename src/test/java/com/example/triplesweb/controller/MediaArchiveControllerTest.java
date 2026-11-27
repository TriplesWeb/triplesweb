package com.example.triplesweb.controller;

import com.example.triplesweb.config.MustacheConfig;
import com.example.triplesweb.dto.MediaDto;
import com.example.triplesweb.entity.Artist;
import com.example.triplesweb.media.MediaTab;
import com.example.triplesweb.repository.ArtistRepository;
import com.example.triplesweb.repository.ScheduleRepository;
import com.example.triplesweb.service.AboutAdminAuth;
import com.example.triplesweb.service.ChemistryGroupService;
import com.example.triplesweb.service.ChemistryService;
import com.example.triplesweb.service.MediaChannelSyncService;
import com.example.triplesweb.service.MediaService;
import com.example.triplesweb.service.MediaViewCountService;
import com.example.triplesweb.service.MediaYoutubeCastService;
import com.example.triplesweb.service.UnitService;
import com.example.triplesweb.util.MemberPortraitOptions;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MediaArchiveController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(MustacheConfig.class)
class MediaArchiveControllerTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper objectMapper;
    @MockitoBean MediaService media;
    @MockitoBean MediaViewCountService mediaViewCounts;
    @MockitoBean MediaYoutubeCastService youtubeCast;
    @MockitoBean MediaChannelSyncService youtubeSync;
    @MockitoBean ArtistRepository artistRepository;
    @MockitoBean ScheduleRepository scheduleRepository;
    @MockitoBean AboutAdminAuth aboutAdminAuth;
    @MockitoBean UnitService unitService;
    @MockitoBean ChemistryService chemistryService;
    @MockitoBean ChemistryGroupService chemistryGroupService;

    @BeforeEach
    void stubs() {
        lenient().when(artistRepository.findAll()).thenReturn(List.of());
        lenient().when(aboutAdminAuth.isAdmin(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(false);
        List<MediaDto> videos = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            videos.add(video(i, i < 5 ? "shorts" : "contents", "영상 " + i, i < 5 ? "0:40" : "3:11", "2024-05-06"));
        }
        when(media.listAll()).thenReturn(videos);
    }

    @Test
    void showsPlaylistAndSuggestionsOnlyOnTheAllTab() throws Exception {
        for (String tab : List.of("mv", "signal", "shorts", "music", "contents", "external")) {
            String html = mvc.perform(get("/media").param("tab", tab))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();
            assertThat(Jsoup.parse(html).select("#mediaPlaylist, #mediaSuggest, [data-playlist-add]")).isEmpty();
        }
    }

    @Test
    void recommendsFiveDistinctVideosFromTheFirstItemsActualTab() throws Exception {
        MediaDto anchor = video(0, "shorts", "서연 지우", "0:37", "2026-11-07");
        List<MediaDto> candidates = new ArrayList<>();
        candidates.add(anchor);
        candidates.add(video(1, "mv", "뮤직비디오", "4:00", "2026-11-07"));
        for (int i = 2; i <= 10; i++) {
            candidates.add(video(i, "shorts", "쇼츠 " + i, "0:38", "2026-11-08"));
        }
        candidates.add(candidates.get(2));
        when(media.findOne(MediaTab.MV, anchor.getYoutubeId())).thenReturn(Optional.of(anchor));
        when(media.allByTab("shorts")).thenReturn(candidates);

        String response = mvc.perform(get("/media/recommendations.json")
                        .param("youtubeId", anchor.getYoutubeId()).param("tab", "mv"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(5))
                .andReturn().getResponse().getContentAsString();
        var rows = objectMapper.readTree(response);
        List<String> ids = new ArrayList<>();
        for (var row : rows) {
            assertThat(row.get("tab").asText()).isEqualTo("shorts");
            ids.add(row.get("youtubeId").asText());
        }
        assertThat(ids).doesNotContain(anchor.getYoutubeId()).doesNotHaveDuplicates();
    }

    @Test
    void fiveColumnsPageThirtyVideosAndPreserveLayoutInNavigation() throws Exception {
        List<MediaDto> entries = new ArrayList<>();
        for (int i = 0; i < 65; i++) {
            entries.add(video(i, "contents", "공연 " + i, "3:10", "2026-11-23"));
        }
        when(media.allByTab("contents")).thenReturn(entries);
        List<String> seen = new ArrayList<>();
        for (int pageNumber = 1; pageNumber <= 3; pageNumber++) {
            String html = mvc.perform(get("/media").queryParam("tab", "contents")
                            .queryParam("cols", "5").queryParam("q", "공연")
                            .queryParam("page", String.valueOf(pageNumber)))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            var page = Jsoup.parse(html);
            assertThat(page.select(".media-grid").attr("data-cols")).isEqualTo("5");
            var cards = page.select(".media-grid article.media-card a");
            assertThat(cards).hasSize(pageNumber == 3 ? 5 : 30);
            seen.addAll(cards.eachAttr("data-ytid"));
            assertThat(page.select(".ar-pager a[href]").eachAttr("href"))
                    .allSatisfy(href -> assertThat(href).contains("cols=5", "tab=contents", "&q="));
            assertThat(page.select(".ar-page-jump input[name=cols]").val()).isEqualTo("5");
        }
        assertThat(seen).hasSize(65).doesNotHaveDuplicates();
        mvc.perform(get("/media").queryParam("tab", "contents").queryParam("cols", "5").queryParam("go", "2"))
                .andExpect(redirectedUrl("/media?tab=contents&cols=5&page=2"));
    }

    @Test
    void pagesArchiveAndLimitsVisibleCards() throws Exception {
        String first = mvc.perform(get("/media"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("@triplescosmos")))
                .andExpect(content().string(containsString("등록순")))
                .andExpect(content().string(containsString("삭제 하시겠습니까?")))
                .andExpect(content().string(not(containsString("채널 동기화"))))
                .andExpect(content().string(containsString("ar-page-jump")))
                .andExpect(content().string(containsString("name=\"go\"")))
                .andExpect(content().string(containsString("더보기")))
                .andExpect(content().string(containsString("추천 동영상")))
                .andExpect(content().string(not(containsString("쇼츠 추가"))))
                .andExpect(content().string(containsString("Shorts &amp; 릴스")))
                .andExpect(content().string(containsString("직캠")))
                .andExpect(content().string(containsString("음방")))
                .andExpect(content().string(containsString("외부 콘텐츠")))
                .andReturn().getResponse().getContentAsString();
        org.jsoup.nodes.Document document = Jsoup.parse(first);
        assertThat(document.select("article.media-card")).hasSize(24);
        assertThat(document.select(".media-playlist__head .media-playlist-add")).hasSize(1);
        assertThat(document.select("article.media-card .media-card__edit.site-btn--primary[data-admin-only]")).hasSize(24);
        assertThat(document.select("article.media-card .media-card__delete")).isEmpty();
        assertThat(document.select(".media-short__actions, [data-short-add]")).isEmpty();
        assertThat(document.select("#mediaAddModal")).hasSize(1);
        assertThat(document.select("#mediaAddOpen.ar-submit")).hasSize(1);
        assertThat(document.select("#mediaPickModal #mediaPickConfirm").text()).isEqualTo("선택 완료");
        assertThat(document.select("#mediaPickMini")).hasSize(1);
        assertThat(document.select("#mediaMemberPanel")).hasSize(1);
        assertThat(document.select("#mediaPickPager")).hasSize(1);
        assertThat(document.select("#ytModalUnit.ar-unit-badge")).hasSize(1);
        assertThat(document.select("#ytModalDelete[data-confirm-compact]")).hasSize(1);
        assertThat(document.select("#ytModalDelete").attr("data-confirm-accept")).isEqualTo("삭제");
        assertThat(document.select("#mediaEditTab option")).hasSize(9);
        assertThat(document.select("#mediaPickMemberToggle")).hasSize(1);
        assertThat(document.select("#mediaPickMemberModal")).hasSize(1);
        assertThat(document.select(".media-cols-icon span")).hasSize(3);
        assertThat(document.select("[data-media-cols-option]")).hasSize(3);
        assertThat(document.select("[data-media-cols-option]").eachText()).containsExactly("3줄", "4줄", "5줄");
        assertThat(document.select(".media-feature__head h2").text()).isEqualTo("재생목록");
        assertThat(document.select(".media-channel").attr("aria-label")).isEqualTo("YouTube 채널");
        assertThat(document.select(".media-channel").text().trim()).isEmpty();
        assertThat(document.select(".media-short__caption small")).isEmpty();
        assertThat(document.select(".media-archive .media-card__title").eachText())
                .noneMatch(title -> title.equals("영상 0") || title.equals("영상 1"));
        verify(youtubeSync).ensureExternalSearch();

        String second = mvc.perform(get("/media").param("page", "2"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        org.jsoup.nodes.Document secondPage = Jsoup.parse(second);
        assertThat(secondPage.select("article.media-card")).hasSize(1);
        assertThat(secondPage.select(".ar-page-jump-meta").text()).isEqualTo("2/2");
    }

    @Test
    void ajaxSavesReturnNoNavigationAndTraditionalAddReturnsToTheOriginalPage() throws Exception {
        String youtubeId = "HwTZTz9j2e0";
        String returnTo = "/media?tab=signal&page=2&sort=oldest&cols=5";
        when(media.updateDetails(youtubeId, null, null, "", "external")).thenReturn(true);
        mvc.perform(post("/media_update").param("youtubeId", youtubeId).param("tab", "external")
                        .param("returnTo", returnTo).header("X-Requested-With", "XMLHttpRequest"))
                .andExpect(status().isNoContent());
        mvc.perform(post("/media_new").param("youtubeId", youtubeId).param("tab", "external")
                        .param("title", "테스트 영상").param("returnTo", returnTo)
                        .header("X-Requested-With", "XMLHttpRequest"))
                .andExpect(status().isNoContent());
        mvc.perform(post("/media_new").param("youtubeId", youtubeId).param("tab", "external")
                        .param("title", "테스트 영상").param("returnTo", returnTo))
                .andExpect(redirectedUrl(returnTo));
    }

    @Test
    void updatingMediaKeepsTheOriginalTabAndFilters() throws Exception {
        String returnTo = "/media?tab=all&page=2&sort=oldest&members=%EC%9C%A4%EC%84%9C%EC%97%B0";
        mvc.perform(post("/media_update").param("youtubeId", "HwTZTz9j2e0")
                        .param("tab", "behind").param("returnTo", returnTo)
                        .param("description", "설명").param("publishedOn", "2026-11-23").param("artistNames", "윤서연"))
                .andExpect(redirectedUrl(returnTo));
        verify(media).updateDetails("HwTZTz9j2e0", "설명", "2026-11-23", "윤서연", "behind");

        mvc.perform(post("/media_update").param("youtubeId", "HwTZTz9j2e0")
                        .param("tab", "behind").param("returnTo", "//example.com/media"))
                .andExpect(redirectedUrl("/media?tab=behind"));
    }

    @Test
    void partialMemberFiltersExcludeTheFullLineupButKeepIndividualVideos() throws Exception {
        List<String> lineup = MemberPortraitOptions.koreanOrder();
        MediaDto full = video(1, "contents", "단체 무대", "3:20", "2026-11-02");
        full.setArtistNames(String.join(",", lineup));
        MediaDto duo = video(2, "contents", "두 명 영상", "3:30", "2026-11-01");
        duo.setArtistNames("윤서연,정혜린");
        when(media.allByTab("contents")).thenReturn(List.of(full, duo));

        for (int memberCount : List.of(0, 2, 23, 24)) {
            var request = get("/media").queryParam("tab", "contents");
            if (memberCount > 0) {
                request.queryParam("members", lineup.subList(0, memberCount).toArray(String[]::new));
            }
            var html = mvc.perform(request).andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();
            var page = Jsoup.parse(html);
            assertThat(page.select(".media-card__title").eachText()).contains("두 명 영상");
            assertThat(page.select(".media-card__members")).isEmpty();
            assertThat(page.select(".media-card__title").eachText().contains("단체 무대"))
                    .isEqualTo(memberCount == 0 || memberCount == 24);
        }
    }

    @Test
    void shortsTabUsesStoredFormatAndShowsMetadata() throws Exception {
        MediaDto shortClip = video(20, "shorts",
                "트리플에스가 말아주는 폭풍 칼군무 퍼포먼스 #MCOUNTDOWN #엠카운트다운 #tripleS",
                "1:39", "2026-10-24");
        shortClip.setArtistNames("코토네");
        MediaDto stage = video(21, "music", "tripleS - Baby Flower 260614 @ INKIGAYO",
                "0:44", "2026-06-14");
        when(media.listAll()).thenReturn(List.of(shortClip, stage));
        when(media.allByTab("music")).thenReturn(List.of(stage, shortClip));
        when(media.allByTab("shorts")).thenReturn(List.of(shortClip, stage));

        String shorts = mvc.perform(get("/media").param("tab", "shorts"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        org.jsoup.nodes.Document shortsPage = Jsoup.parse(shorts);
        assertThat(shortsPage.select(".media-short__thumb .media-card__time").text()).isEqualTo("1:39");
        assertThat(shortsPage.select(".media-short__meta").text()).contains("조회수 10", "2026.10.24").doesNotContain("1:39");
        assertThat(shortsPage.select(".media-short__meta time").attr("datetime")).isEqualTo("2026-10-24");
        assertThat(shortsPage.select(".media-short__caption .media-card__members")).isEmpty();
        assertThat(shortsPage.select(".ar-reset[hidden]")).hasSize(1);
        String searched = mvc.perform(get("/media").param("tab", "shorts").param("q", "말아주는"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(Jsoup.parse(searched).select(".ar-reset:not([hidden])")).hasSize(1);
        assertThat(shortsPage.select(".media-short__caption strong").eachText())
                .anyMatch(title -> title.contains("말아주는"));
        assertThat(shortsPage.select(".media-short__caption strong").eachText())
                .noneMatch(title -> title.contains("Baby Flower"));

        String music = mvc.perform(get("/media").param("tab", "music"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        org.jsoup.nodes.Document musicPage = Jsoup.parse(music);
        assertThat(musicPage.select(".media-archive .media-card__title").eachText())
                .anyMatch(title -> title.contains("Baby Flower"))
                .noneMatch(title -> title.contains("말아주는"));
        assertThat(musicPage.select(".media-shorts")).isEmpty();
    }

    @Test
    void signalEraSubtabsFilterByHausPeriod() throws Exception {
        MediaDto gapyeong = video(1, "signal", "SIGNAL 221130", "8:00", "2022-11-30");
        MediaDto debut = video(2, "signal", "SIGNAL 240520", "8:00", "2024-05-20");
        when(media.allByTab("signal")).thenReturn(List.of(gapyeong, debut));

        String html = mvc.perform(get("/media").param("tab", "signal").param("era", "gapyeong"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        org.jsoup.nodes.Document document = Jsoup.parse(html);
        assertThat(document.select(".media-era-tabs .site-section-tab").eachText())
                .contains("가평 HAUS", "성수/여의도/남산", "모두 HAUS", "24명 데뷔 이후");
        assertThat(document.select(".media-tabs > a[href*=tab=signal] + .media-era-tabs")).hasSize(1);
        assertThat(document.select(".media-era-tabs a[aria-current=page]").text()).isEqualTo("가평 HAUS");
        assertThat(document.select("input[name=era]").attr("value")).isEqualTo("gapyeong");
        assertThat(document.select(".media-archive .media-card__title").eachText())
                .anyMatch(title -> title.contains("221130"))
                .noneMatch(title -> title.contains("240520"));
    }

    @Test
    void labelsEachSignalEraOnlyInTheSignalTab() throws Exception {
        MediaDto old = video(1, "signal", "SIGNAL 230630", "8:00", "");
        MediaDto recent = video(2, "signal", "SIGNAL 261124", "8:00", "2026-11-24");
        when(media.listAll()).thenReturn(List.of(old, recent));
        when(media.allByTab("signal")).thenReturn(List.of(old, recent));

        String signal = mvc.perform(get("/media").param("tab", "signal"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(Jsoup.parse(signal).select(".media-card__kind").eachText())
                .containsExactlyInAnyOrder("SIGNAL · 성수/여의도/남산", "SIGNAL · 24명 데뷔 이후");

        String all = mvc.perform(get("/media")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(Jsoup.parse(all).select(".media-card__kind").eachText()).containsExactly("SIGNAL", "SIGNAL");
    }

    @Test
    void addsHashtagBadgesWithoutDuplicatingAliasesOrChangingSavedCast() throws Exception {
        MediaDto video = video(1, "external", "Episode", "8:00", "2026-11-24");
        video.setArtistNames("SeoYeon");
        Artist seoyeon = new Artist();
        seoyeon.setArtistId(1L);
        seoyeon.setName("윤서연");
        seoyeon.setEngName("SeoYeon");
        Artist hayeon = new Artist();
        hayeon.setArtistId(19L);
        hayeon.setName("정하연");
        when(artistRepository.findAll()).thenReturn(List.of(seoyeon, hayeon));
        when(media.findOne(MediaTab.EXTERNAL, video.getYoutubeId())).thenReturn(Optional.of(video));
        when(youtubeCast.findMembers(video.getYoutubeId())).thenReturn(List.of("윤서연", "정하연"));

        mvc.perform(get("/media/youtube-members.json")
                        .param("youtubeId", video.getYoutubeId()).param("tab", "external"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].artistId").value(1))
                .andExpect(jsonPath("$[1].name").value("정하연"))
                .andExpect(jsonPath("$[1].artistId").value(19));
        assertThat(video.getArtistNames()).isEqualTo("SeoYeon");
    }

    @Test
    void skipsYoutubeForUnknownVideosAndCompleteLineups() throws Exception {
        MediaDto video = video(1, "contents", "Group video", "8:00", "2026-11-24");
        video.setArtistNames(String.join(",", MemberPortraitOptions.koreanOrder()));
        when(media.findOne(MediaTab.CONTENTS, video.getYoutubeId())).thenReturn(Optional.of(video));

        for (String id : List.of(video.getYoutubeId(), "abcdefghijk")) {
            mvc.perform(get("/media/youtube-members.json").param("youtubeId", id).param("tab", "contents"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        }
        verifyNoInteractions(youtubeCast);
    }

    @Test
    void signalFiltersKeepTheSelectedTabAndHausPeriod() throws Exception {
        MediaDto earlier = video(1, "signal", "코토네 SIGNAL 221130", "8:00", "2022-11-30");
        MediaDto oldest = video(2, "signal", "코토네 SIGNAL 230720", "8:00", "2023-07-20");
        MediaDto newer = video(3, "signal", "코토네 SIGNAL 240420", "8:00", "2024-04-20");
        MediaDto otherTab = video(4, "contents", "코토네 SIGNAL 230730", "8:00", "2023-07-30");
        oldest.setArtistNames("코토네");
        newer.setArtistNames("코토네");
        earlier.setArtistNames("코토네");
        otherTab.setArtistNames("코토네");
        when(media.allByTab("signal")).thenReturn(List.of(newer, otherTab, earlier, oldest));

        String html = mvc.perform(get("/media").param("tab", "signal").param("era", "allHaus")
                        .param("sort", "oldest").param("q", "코토네").param("members", "코토네")
                        .param("from", "2022-01-01").param("to", "2024-12-31").param("kind", "shorts"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        var document = Jsoup.parse(html);
        assertThat(document.select(".media-archive .media-card__title").eachText())
                .containsExactly(oldest.getTitle(), newer.getTitle());
        assertThat(document.select("#mediaFilterForm input[name=tab]").val()).isEqualTo("signal");
        assertThat(document.select("#mediaFilterForm input[name=era]").val()).isEqualTo("allHaus");
        assertThat(document.select("#mediaFilterForm input[name=kind], .media-shorts")).isEmpty();
        assertThat(document.getElementById("mediaCatalog").data()).contains("\"signalEra\":\"allHaus\"");
    }

    @Test
    void shortsKindKeepsOnlyShortVideos() throws Exception {
        String html = mvc.perform(get("/media").param("kind", "shorts"))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("YouTube Shorts"))))
                .andExpect(content().string(not(containsString("짧게 만나는"))))
                .andExpect(content().string(not(containsString("페이지 1 / 2"))))
                .andReturn().getResponse().getContentAsString();
        org.jsoup.nodes.Document document = Jsoup.parse(html);
        assertThat(document.select("section.media-archive")).isEmpty();
        assertThat(document.select(".media-short")).hasSize(5);
    }

    @Test
    void initialStageStaysCollapsedWithoutSeededVideo() throws Exception {
        when(media.listAll()).thenReturn(List.of(
                video(1, "mv", "ANDLESS Official MV", "3:40", "2026-11-01"),
                video(2, "mv", "Other Official MV", "3:20", "2026-11-02"),
                video(3, "contents", "ANDLESS behind", "4:00", "2026-11-03"),
                video(4, "contents", "ANDLESS teaser", "0:40", "2026-11-04"),
                video(6, "shorts", "ANDLESS official short", "0:35", "2026-11-04"),
                video(5, "mv", "andless live clip", "2:10", "2026-10-01")));

        String html = mvc.perform(get("/media"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        org.jsoup.nodes.Document document = Jsoup.parse(html);
        assertThat(document.select("section.media-feature")).isNotEmpty();
        assertThat(document.select("section.media-feature[hidden]")).isEmpty();
        assertThat(document.select(".media-feature.is-collapsed")).isEmpty();
        assertThat(document.select(".media-feature__hero.is-empty")).isNotEmpty();
        assertThat(document.select(".media-feature__still[data-no-placeholder]")).isNotEmpty();
        assertThat(document.select("#mediaPlaylist")).isNotEmpty();
        assertThat(document.select(".media-suggest.is-collapsed")).isNotEmpty();
        assertThat(document.select(".media-playlist__item")).isEmpty();
        assertThat(document.select(".media-feature__hero").attr("data-ytid")).isEmpty();
        assertThat(document.select(".media-suggest__card")).isEmpty();
        assertThat(document.select(".media-card__views")).isNotEmpty();
        assertThat(document.select(".media-archive .media-card__title").eachText())
                .contains("ANDLESS teaser").doesNotContain("ANDLESS official short");
        assertThat(document.select(".media-short")).hasSize(1);
    }

    @Test
    void pickCatalogJsonOmitsShortsAndLoadsOnDemand() throws Exception {
        String json = mvc.perform(get("/media/pick-catalog.json"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        org.assertj.core.api.Assertions.assertThat(json).contains("영상 5");
        org.assertj.core.api.Assertions.assertThat(json).doesNotContain("영상 0");
        org.assertj.core.api.Assertions.assertThat(json).doesNotContain("\"description\"");
    }

    @Test
    void mediaCardsHideMemberBadgesButKeepThemForThePlayer() throws Exception {
        Artist kotone = new Artist();
        kotone.setArtistId(11L);
        kotone.setName("코토네");
        kotone.setEngName("Kotone");
        kotone.setColor("#ffde00");
        kotone.setProfileImage("/portrait-kotone.png");
        when(artistRepository.findAll()).thenReturn(List.of(kotone));
        MediaDto video = video(10, "mv", "코토네 영상", "3:11", "2024-05-06");
        video.setArtistNames("코토네");
        when(media.listAll()).thenReturn(List.of(video));

        String html = mvc.perform(get("/media"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        org.jsoup.nodes.Document document = Jsoup.parse(html);
        assertThat(document.select(".media-card__members")).isEmpty();
        String catalog = document.select("#mediaCatalog").html();
        assertThat(catalog).contains("\"hasSymbol\":true");
        assertThat(catalog).contains("/images/archive/member-logos/11.png");
        assertThat(catalog).contains("/portrait-kotone.png");
    }

    @Test
    void newMediaFormRedirectsToAddModal() throws Exception {
        mvc.perform(get("/media_new").param("tab", "signal"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/media?tab=signal&add=1"));
    }

    @Test
    void pageJumpKeepsFiltersAndUsesOneBasedPage() throws Exception {
        mvc.perform(get("/media").param("tab", "signal").param("go", "2").param("q", "코토네"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/media?tab=signal&q=%EC%BD%94%ED%86%A0%EB%84%A4&page=2"));
    }

    private static MediaDto video(int index, String duration) {
        return video(index, "contents", "영상 " + index, duration, "2024-05-06");
    }

    private static MediaDto video(int index, String tab, String title, String duration, String publishedOn) {
        return MediaDto.builder()
                .id((long) index + 1)
                .tab(tab)
                .youtubeId(String.format("%011d", index))
                .title(title)
                .duration(duration)
                .views(10L)
                .viewCountFmt("10")
                .publishedOn(publishedOn)
                .build();
    }
}
