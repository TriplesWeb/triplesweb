package com.example.triplesweb.controller;

import com.example.triplesweb.config.MustacheConfig;
import com.example.triplesweb.dto.GroupIntroDto;
import com.example.triplesweb.entity.Artist;
import com.example.triplesweb.navigation.SiteNavigation;
import com.example.triplesweb.repository.ArtistRepository;
import com.example.triplesweb.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.data.domain.Sort;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = {SitePagesController.class, TriplesController.class})
@AutoConfigureMockMvc(addFilters = false)
@Import(MustacheConfig.class)
class SiteNavigationTest {
    @Autowired MockMvc mvc;
    @MockitoBean ArtistRepository artists;
    @MockitoBean ArtistAdminService artistAdmin;
    @MockitoBean AlbumService albums;
    @MockitoBean UnitService units;
    @MockitoBean MemberSpotlightService spotlight;

    @Test void navigationHasRequestedOrderAndDestinations() throws Exception {
        assertThat(SiteNavigation.PRIMARY).extracting(SiteNavigation.Section::label)
            .containsExactly("About 트리플에스", "정보", "코스모", "미디어", "일정");
        assertThat(SiteNavigation.PRIMARY.get(1).items()).extracting(SiteNavigation.Item::label)
            .containsExactly("공연/이벤트", "굿즈리스트", "총공", "기사/그외");
        assertThat(SiteNavigation.PRIMARY.get(2).items()).extracting(SiteNavigation.Item::href)
            .containsExactly("/cosmo/objekt", "/cosmo/collection", "/article/trade", "/cosmo/gravity");
        mvc.perform(get("/sitemap")).andExpect(status().isNotFound());
        mvc.perform(get("/media/drive")).andExpect(status().isNotFound());
        mvc.perform(get("/community/suggestions")).andExpect(status().isNotFound());
        mvc.perform(get("/activities/voting")).andExpect(status().isOk()).andExpect(content().string(containsString("총공")));
        verifyNoInteractions(artists, artistAdmin, spotlight);
    }

    @Test void overviewReusesGroupDataAndDirectoryDoesNotLoadMemberFeeds() throws Exception {
        when(artistAdmin.getGroupIntro()).thenReturn(GroupIntroDto.builder().title("tripleS").body("기존 그룹 설명").imageUrl("/group.jpg").build());
        mvc.perform(get("/triples/overview")).andExpect(status().isOk())
            .andExpect(view().name("triples/artist"))
            .andExpect(content().string(containsString("기존 그룹 설명")))
            .andExpect(content().string(containsString("id=\"groupImageDisplay\"")));
        mvc.perform(get("/cosmo/group-orders")).andExpect(redirectedUrl("/article/group-orders"));
        Artist member = new Artist(); member.setArtistId(1L); member.setName("서연"); member.setEngName("SeoYeon");
        when(artists.findAllWithUnits()).thenReturn(List.of(member));
        mvc.perform(get("/triples/members")).andExpect(status().isOk())
            .andExpect(content().string(containsString("data-target=\"#member-1\"")))
            .andExpect(content().string(org.hamcrest.Matchers.not(containsString("id=\"triples-tab\""))));
        when(artists.existsByArtistId(1L)).thenReturn(true);
        mvc.perform(get("/triples/artist/1")).andExpect(redirectedUrl("/triples/artist?tab=member-1"));
        verifyNoInteractions(spotlight);
    }
}
