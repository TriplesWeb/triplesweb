package com.example.triplesweb.controller;

import com.example.triplesweb.config.MustacheConfig;
import com.example.triplesweb.repository.ArtistRepository;
import com.example.triplesweb.service.AlbumService;
import com.example.triplesweb.service.ArtistAdminService;
import com.example.triplesweb.service.MemberSpotlightService;
import com.example.triplesweb.service.UnitService;
import com.example.triplesweb.web.TriplesMvcExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest(controllers = TriplesController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({MustacheConfig.class, TriplesMvcExceptionHandler.class})
class TriplesControllerNotFoundTest {

    @Autowired MockMvc mvc;
    @MockitoBean ArtistRepository artistRepository;
    @MockitoBean ArtistAdminService artistAdminService;
    @MockitoBean AlbumService albumService;
    @MockitoBean UnitService unitService;
    @MockitoBean MemberSpotlightService memberSpotlightService;

    @Test
    void missingAlbumReturns404View() throws Exception {
        when(albumService.getDetail(999999999L))
                .thenThrow(new IllegalArgumentException("해당 앨범이 존재하지 않습니다. id=999999999"));

        mvc.perform(get("/triples/album/999999999"))
                .andExpect(status().isNotFound())
                .andExpect(view().name("triples/not-found"));
    }
}
