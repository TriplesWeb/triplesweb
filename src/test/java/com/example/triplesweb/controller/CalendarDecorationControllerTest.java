package com.example.triplesweb.controller;

import com.example.triplesweb.entity.Artist;
import com.example.triplesweb.repository.ArtistRepository;
import com.example.triplesweb.service.AboutAdminAuth;
import com.example.triplesweb.service.CalendarDecorationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CalendarDecorationController.class)
@AutoConfigureMockMvc(addFilters = false)
class CalendarDecorationControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean CalendarDecorationService decorations;
    @MockitoBean ArtistRepository artists;
    @MockitoBean AboutAdminAuth adminAuth;

    @Test
    void repeatsMemberBirthdayInsideTheRequestedRange() throws Exception {
        Artist artist = new Artist();
        artist.setArtistId(7L);
        artist.setName("윤서연");
        artist.setBirthDate("2002.08.06");
        artist.setColor("#22aeff");
        when(artists.findAll()).thenReturn(List.of(artist));

        mvc.perform(get("/api/calendar-decorations/birthdays")
                        .param("start", "2026-08-01")
                        .param("end", "2026-08-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").value("윤서연 생일"))
                .andExpect(jsonPath("$[0].start").value("2026-08-06"))
                .andExpect(jsonPath("$[0].kind").value("BIRTHDAY"))
                .andExpect(jsonPath("$[0].color").value("#22aeff"));
    }

    @Test
    void skipsFebruary29InANonLeapYear() throws Exception {
        Artist artist = new Artist();
        artist.setArtistId(8L);
        artist.setName("김나경");
        artist.setBirthDate("2000-02-29");
        when(artists.findAll()).thenReturn(List.of(artist));

        mvc.perform(get("/api/calendar-decorations/birthdays")
                        .param("start", "2026-02-01")
                        .param("end", "2026-02-28"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void rejectsABirthdayRangeLongerThan400Days() throws Exception {
        assertThatThrownBy(() -> mvc.perform(get("/api/calendar-decorations/birthdays")
                        .param("start", "2026-01-01")
                        .param("end", "2027-03-01"))
                .andReturn())
                .hasCauseInstanceOf(IllegalArgumentException.class)
                .cause()
                .hasMessageContaining("400일");
    }
}
