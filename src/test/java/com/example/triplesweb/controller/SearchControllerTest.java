package com.example.triplesweb.controller;

import com.example.triplesweb.config.MustacheConfig;
import com.example.triplesweb.service.SiteSearchService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SearchController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(MustacheConfig.class)
class SearchControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean SiteSearchService search;

    @Test
    void emptyKeywordShowsErrorWithoutDuplicateInputs() throws Exception {
        when(search.search("", null)).thenReturn(new SiteSearchService.Page("", "about", List.of(
                new SiteSearchService.Tab("about", "About 트리플에스", "/search?tab=about", "/triples/members", List.of(), true)
        )));
        mvc.perform(get("/search")).andExpect(status().isOk())
                .andExpect(content().string(containsString("검색어를 입력해주세요.")))
                .andExpect(content().string(containsString("사이트 전체 검색")))
                .andExpect(content().string(not(containsString("site-search-field"))));
    }

    @Test
    void keywordAndTabRenderHitsAndMoreLink() throws Exception {
        var hits = List.of(new SiteSearchService.Hit("HaYeon 323", "/cosmo/objekt/detail/hayeoncc323", "오브젝트", "Cream02"));
        var about = new SiteSearchService.Tab("about", "About 트리플에스",
                "/search?keyword=323&tab=about", "/triples/members", List.of(), false);
        var cosmo = new SiteSearchService.Tab("cosmo", "코스모",
                "/search?keyword=323&tab=cosmo", "/cosmo/objekt?search=323", hits, true);
        var members = List.of(new SiteSearchService.Member("지연", "JiYeon",
                "/images/image-upload-pending.png", "/triples/artist?tab=member-24"));
        when(search.search("323", "cosmo")).thenReturn(
                new SiteSearchService.Page("323", "cosmo", List.of(about, cosmo), members));

        String body = mvc.perform(get("/search").param("keyword", "323").param("tab", "cosmo"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("멤버 검색 결과")))
                .andExpect(content().string(containsString("search-member")))
                .andExpect(content().string(containsString("지연")))
                .andExpect(content().string(containsString("JiYeon")))
                .andExpect(content().string(containsString("/triples/artist?tab=member-24")))
                .andExpect(content().string(containsString("search-tabs")))
                .andExpect(content().string(containsString("HaYeon 323")))
                .andExpect(content().string(containsString("/cosmo/objekt?search=323")))
                .andExpect(content().string(containsString("코스모에서 더 보기")))
                .andExpect(content().string(containsString("About 트리플에스")))
                .andReturn().getResponse().getContentAsString();
        assertThat(body.indexOf("멤버 검색 결과")).isLessThan(body.indexOf("search-tabs"));
        assertThat(body.indexOf("지연")).isLessThan(body.indexOf("HaYeon 323"));
    }
}
