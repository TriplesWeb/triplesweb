package com.example.triplesweb.controller;

import com.example.triplesweb.config.MustacheConfig;
import com.example.triplesweb.entity.*;
import com.example.triplesweb.repository.*;
import com.example.triplesweb.service.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.*;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;
import java.nio.file.*;
import java.time.LocalDate;
import java.util.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.assertj.core.api.Assertions.*;

@WebMvcTest(ScheduleController.class)
@AutoConfigureMockMvc(addFilters=false)
@Import(MustacheConfig.class)
class ScheduleCategoryControllerTest {
    @Autowired MockMvc mvc;
    @MockBean ScheduleService service;
    @MockBean ScheduleRepository repository;
    @MockBean ArtistRepository artists;
    @MockBean AboutAdminAuth auth;
    Artist artist;
    LocalDate day=LocalDate.now();

    Schedule schedule(long id,String title,ScheduleCategory category) {
        Schedule schedule=new Schedule(title,day,null);schedule.setId(id);schedule.setCategory(category);schedule.setArtists(new LinkedHashSet<>(List.of(artist)));return schedule;
    }
    @BeforeEach void setup() {
        artist=new Artist();artist.setArtistId(1L);artist.setName("윤서연");artist.setEngName("SeoYeon");artist.setColor("#22aeff");
        when(artists.findAll()).thenReturn(List.of(artist));
        when(auth.isAdmin(any(),any())).thenReturn(true);
        when(service.findAll()).thenReturn(List.of(
            schedule(1,"팬사인회",ScheduleCategory.FANSIGN),schedule(2,"방송",ScheduleCategory.BROADCAST),
            schedule(3,"윤서연 생일",ScheduleCategory.ANNIVERSARY),schedule(4,"음원 발매",ScheduleCategory.RELEASE),schedule(5,"행사",ScheduleCategory.EVENT)));
    }
    @Test void rendersColorCategoriesAndExportsAdminPreview() throws Exception {
        String html=mvc.perform(get("/schedule").requestAttr(org.springframework.security.web.csrf.CsrfToken.class.getName(),
                new org.springframework.security.web.csrf.DefaultCsrfToken("X-XSRF-TOKEN","_csrf","fixture-csrf"))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(html).contains("addScheduleCategory","editScheduleCategory","#a78bfa","#fb923c","#22aeff","#f87171","#7dd3fc","🎂 윤서연 생일");
        Files.createDirectories(Path.of("build/filter-pages"));
        Files.writeString(Path.of("build/filter-pages/schedule.html"),html);
    }
    @Test void addAndEditReturnSavedIdAndAuthoritativePresentation() throws Exception {
        when(service.save(eq("윤서연 생일"),eq(day),isNull(),eq("윤서연"),eq(ScheduleCategory.ANNIVERSARY)))
                .thenReturn(schedule(42,"윤서연 생일",ScheduleCategory.ANNIVERSARY));
        mvc.perform(post("/schedule/add").param("title","윤서연 생일").param("start",day.toString()).param("artistNames","윤서연").param("category","ANNIVERSARY"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value("42")).andExpect(jsonPath("$.category").value("ANNIVERSARY"))
                .andExpect(jsonPath("$.displayTitle").value("🎂 윤서연 생일")).andExpect(jsonPath("$.color").value("#22aeff"));
        when(service.update(eq("42"),eq("방송"),eq(day),isNull(),eq("윤서연"),eq(ScheduleCategory.BROADCAST))).thenReturn(schedule(42,"방송",ScheduleCategory.BROADCAST));
        mvc.perform(post("/edit/42").param("title","방송").param("startDate",day.toString()).param("artistNames","윤서연").param("category","BROADCAST"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.color").value("#fb923c")).andExpect(jsonPath("$.displayTitle").value("방송"));
        verify(auth,times(2)).requireAdmin(any(),any());
    }
    @Test void rejectsUnknownCategoryAndNonAdminWrites() throws Exception {
        mvc.perform(post("/schedule/add").param("title","일정").param("start",day.toString()).param("category","INVALID")).andExpect(status().isBadRequest());
        doThrow(new ResponseStatusException(HttpStatus.FORBIDDEN)).when(auth).requireAdmin(any(),any());
        mvc.perform(post("/schedule/add").param("title","일정").param("start",day.toString()).param("category","EVENT")).andExpect(status().isForbidden());
        verify(service,never()).save(anyString(),any(),any(),anyString(),any());
    }
    @Test void titlesCannotBreakOutOfEmbeddedJson() throws Exception {
        when(service.findAll()).thenReturn(List.of(schedule(9,"</script><script>window.scheduleInjected=true</script>",ScheduleCategory.OTHER)));
        String html=mvc.perform(get("/schedule")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(html).doesNotContain("</script><script>window.scheduleInjected=true");
        assertThat(html).contains("\\u003c/script>");
    }
}
