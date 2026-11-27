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
        artist.setArtistId(999L);
        artist.setName("정혜린");
        artist.setEngName("hyerin");
        artist.setBirthDate("2007.04.12");
        artist.setColor("#9200ff");
        when(artists.findAll()).thenReturn(List.of(artist));

        mvc.perform(get("/api/calendar-decorations/birthdays")
                        .param("start", "2026-04-01")
                        .param("end", "2026-04-30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").value("정혜린 생일"))
                .andExpect(jsonPath("$[0].start").value("2026-04-12"))
                .andExpect(jsonPath("$[0].kind").value("BIRTHDAY"))
                .andExpect(jsonPath("$[0].color").value("#9200ff"))
                .andExpect(jsonPath("$[0].imageUrl").value("/images/artist/bite/2.png"));
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
        mvc.perform(get("/api/calendar-decorations/birthdays")
                        .param("start", "2026-01-01")
                        .param("end", "2027-03-01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("생일 조회 기간은 400일 이내여야 합니다."));
    }

    @Test
    void returnsBothMemberBirthdaysWithTheirOwnBiteImagesOnAllThreeSharedDates() throws Exception {
        Artist dahyun = birthdayArtist(10L, "서다현", "dahyun", "2003-01-08");
        Artist yeonji = birthdayArtist(12L, "곽연지", "yeonji", "2008-01-08");
        Artist hyerin = birthdayArtist(2L, "정혜린", "hyerin", "2007-04-12");
        Artist lynn = birthdayArtist(17L, "린", "lynn", "2006-04-12");
        Artist nakyoung = birthdayArtist(7L, "김나경", "nakyoung", "2002-10-13");
        Artist sohyun = birthdayArtist(14L, "박소현", "sohyun", "2002-10-13");
        when(artists.findAll()).thenReturn(List.of(dahyun, yeonji, hyerin, lynn, nakyoung, sohyun));

        mvc.perform(get("/api/calendar-decorations/birthdays")
                        .param("start", "2026-01-01")
                        .param("end", "2026-10-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].start").value("2026-01-08"))
                .andExpect(jsonPath("$[0].title").value("서다현 생일"))
                .andExpect(jsonPath("$[0].imageUrl").value("/images/artist/bite/10.png"))
                .andExpect(jsonPath("$[1].start").value("2026-01-08"))
                .andExpect(jsonPath("$[1].title").value("곽연지 생일"))
                .andExpect(jsonPath("$[1].imageUrl").value("/images/artist/bite/12.png"))
                .andExpect(jsonPath("$[2].start").value("2026-04-12"))
                .andExpect(jsonPath("$[2].title").value("정혜린 생일"))
                .andExpect(jsonPath("$[2].imageUrl").value("/images/artist/bite/2.png"))
                .andExpect(jsonPath("$[3].start").value("2026-04-12"))
                .andExpect(jsonPath("$[3].title").value("린 생일"))
                .andExpect(jsonPath("$[3].imageUrl").value("/images/artist/bite/17.png"))
                .andExpect(jsonPath("$[4].start").value("2026-10-13"))
                .andExpect(jsonPath("$[4].title").value("김나경 생일"))
                .andExpect(jsonPath("$[4].imageUrl").value("/images/artist/bite/7.png"))
                .andExpect(jsonPath("$[5].start").value("2026-10-13"))
                .andExpect(jsonPath("$[5].title").value("박소현 생일"))
                .andExpect(jsonPath("$[5].imageUrl").value("/images/artist/bite/14.png"));
    }

    private Artist birthdayArtist(long id, String name, String engName, String birthDate) {
        Artist artist = new Artist();
        artist.setArtistId(id);
        artist.setName(name);
        artist.setEngName(engName);
        artist.setBirthDate(birthDate);
        artist.setColor("#00f5d4");
        return artist;
    }
}
