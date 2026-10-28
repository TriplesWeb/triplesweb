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

    @Test void primaryOrderAndAllLegacyDestinationsRemainAvailable() throws Exception {
        assertThat(SiteNavigation.PRIMARY).extracting(SiteNavigation.Section::label)
            .containsExactly("About 트리플에스", "코스모", "정보", "미디어", "일정");
        String html = mvc.perform(get("/sitemap")).andExpect(status().isOk())
            .andExpect(view().name("navigation/sitemap")).andReturn().getResponse().getContentAsString();
        for (String path : List.of("/cosmo/collection",
                "/cosmo/transfers", "/cosmo/objekt/favorites", "/article/free", "/article/chat", "/popular",
                "/article/trade", "/article/sell", "/article/group-orders", "/shop")) {
            assertThat(html).contains("href=\"" + path + "\"");
        }
        assertThat(html).doesNotContain("함께 둘러보기", "href=\"/sns\"", "href=\"/triples/chemistry-en\"");
        verifyNoInteractions(artists, artistAdmin, spotlight);
    }

    @Test void newDestinationsAreReadOnlyPlaceholders() throws Exception {
        for (String path : List.of("/activities/voting", "/media/drive", "/community/suggestions")) {
            mvc.perform(get(path)).andExpect(status().isOk()).andExpect(content().string(containsString("준비 중")));
            mvc.perform(post(path)).andExpect(status().isMethodNotAllowed());
        }
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
        when(artists.findAll(any(Sort.class))).thenReturn(List.of(member));
        mvc.perform(get("/triples/members")).andExpect(status().isOk())
            .andExpect(content().string(containsString("href=\"/triples/artist/1\"")));
        when(artists.existsByArtistId(1L)).thenReturn(true);
        mvc.perform(get("/triples/artist/1")).andExpect(redirectedUrl("/triples/artist?tab=member-1"));
        verifyNoInteractions(spotlight);
    }
}
