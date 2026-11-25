package com.example.triplesweb.controller;

import com.example.triplesweb.entity.Album;
import com.example.triplesweb.entity.Archive;
import com.example.triplesweb.entity.Article;
import com.example.triplesweb.entity.ArticleFile;
import com.example.triplesweb.entity.Artist;
import com.example.triplesweb.entity.Event;
import com.example.triplesweb.entity.EventStop;
import com.example.triplesweb.entity.MediaType;
import com.example.triplesweb.entity.SavedMeta;
import com.example.triplesweb.entity.Schedule;
import com.example.triplesweb.entity.Track;
import com.example.triplesweb.entity.Unit;
import com.example.triplesweb.entity.UnitMember;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.orm.jpa.support.OpenEntityManagerInViewInterceptor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:detached_pages;MODE=MySQL;DATABASE_TO_LOWER=TRUE")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DetachedPageRenderingTest {
    @Autowired MockMvc mvc;
    @Autowired MainController home;
    @Autowired ApplicationContext context;
    @Autowired EntityManager entities;
    @Autowired TransactionTemplate transactions;

    private Long artistId;
    private Long albumId;
    private Long soloId;
    private Long unitId;
    private Long eventId;
    private Long articleId;
    private final LocalDate scheduleDate = LocalDate.now();

    @BeforeAll
    void seedData() {
        // Commit fixtures before MockMvc so there is no test transaction to supply a persistence context.
        transactions.executeWithoutResult(status -> {
            Artist artist = new Artist();
            artist.setName("렌더링 멤버");
            artist.setEngName("Render member");
            artist.setNationality("한국");
            artist.setBirthDate("2000-01-01");
            artist.setRevealDate("2024-01-01");
            artist.setDormEntryDate("2024-01-01");
            artist.setColor("#143278");
            entities.persist(artist);
            artistId = artist.getArtistId();

            Unit parent = new Unit(null, "렌더링 상위 유닛", null, null, "2024-01-01", null, null);
            Unit child = new Unit(null, "렌더링 하위 유닛", null, null, "2024-01-02", null, parent);
            entities.persist(parent);
            entities.persist(child);
            entities.persist(new UnitMember(null, artist, child));
            unitId = child.getUnitId();

            Album album = new Album();
            album.setAlbumName("렌더링 앨범");
            album.setAlbumInfo("앨범 소개");
            album.setReleaseDate("2024-01-01");
            album.setUnit(parent);
            entities.persist(album);
            entities.persist(new Track(null, 1, "렌더링 트랙", "3:12", true, album));
            albumId = album.getAlbumId();

            Album solo = new Album();
            solo.setAlbumName("렌더링 참여 앨범");
            solo.setAlbumInfo("");
            solo.setReleaseDate("2024-01-02");
            solo.getArtists().add(artist);
            entities.persist(solo);
            soloId = solo.getAlbumId();

            Schedule schedule = new Schedule("렌더링 일정", scheduleDate, scheduleDate);
            schedule.setArtists(Set.of(artist));
            entities.persist(schedule);

            Event event = new Event();
            event.setEventName("렌더링 공연");
            event.setEventInfo("");
            entities.persist(event);
            for (int i = 0; i < 2; i++) {
                EventStop stop = new EventStop();
                stop.setEvent(event);
                stop.setEventDate(scheduleDate.plusDays(i).toString());
                stop.setRegion("서울");
                stop.setVenue("렌더링 공연장");
                stop.setSortOrder(i);
                entities.persist(stop);
            }
            eventId = event.getEventId();

            Article article = new Article();
            article.setDisplayId(1L);
            article.setUserId("render-test");
            article.setTitle("렌더링 게시글");
            article.setContent("본문");
            article.setCategory("trade");
            entities.persist(article);
            SavedMeta attachment = new SavedMeta("attachment.jpg", "attachment.jpg", "/uploads/attachment.jpg", 10L,
                    "image/jpeg", MediaType.IMAGE);
            entities.persist(ArticleFile.builder().article(article).file(attachment).build());
            articleId = article.getId();

            Archive photo = new Archive("렌더링 아카이브", attachment);
            photo.setRegisteredOn(scheduleDate);
            photo.setUnitIds(Set.of(unitId));
            photo.setArtistNames(artist.getName());
            entities.persist(photo);
        });
        home.refreshHomeSnapshot();
    }

    @Test
    void rendersPrimaryPagesWithOpenEntityManagerInViewDisabled() throws Exception {
        assertThat(context.getBeansOfType(OpenEntityManagerInViewInterceptor.class)).isEmpty();
        renders("/", "렌더링 일정");
        renders("/triples/members", "렌더링 하위 유닛");
        renders("/triples/artist", "렌더링 하위 유닛");
        renders("/triples/album", "렌더링 앨범");
        renders("/triples/unit", "렌더링 하위 유닛", "렌더링 멤버", "렌더링 앨범");
        renders("/archive", "<h1>아카이브</h1>");
        renders("/archive/all", "렌더링 아카이브");
        renders("/schedule?date=" + scheduleDate, "렌더링 일정", "렌더링 멤버");
        renders("/gravity", "역대 그래비티");
        renders("/cosmo/objekt", "오브젝트");
        renders("/cosmo/event", "렌더링 공연");
    }

    @Test
    void rendersDetailPagesAndLegacyTabsWithDetachedEntities() throws Exception {
        renders("/triples/album/" + albumId, "렌더링 트랙", "렌더링 하위 유닛");
        renders("/triples/album/" + soloId, "렌더링 멤버");
        mvc.perform(get("/triples/unit/{id}", unitId))
                .andExpect(redirectedUrl("/triples/unit?tab=unit-" + unitId));
        renders("/triples/unit?tab=unit-" + unitId, "렌더링 하위 유닛", "렌더링 멤버");
        mvc.perform(get("/triples/artist/{id}", artistId))
                .andExpect(redirectedUrl("/triples/artist?tab=member-" + artistId));
        renders("/triples/artist?tab=member-" + artistId, "렌더링 멤버", "렌더링 하위 유닛");
        renders("/triples/artist/" + artistId + "/feeds", "렌더링 일정");
        renders("/cosmo/event/" + eventId, "렌더링 공연장");
        renders("/article/trade/" + articleId + "/edit", "attachment.jpg");
    }

    private void renders(String path, String... expectedText) throws Exception {
        var response = mvc.perform(get(path)).andExpect(status().isOk());
        for (String text : expectedText) {
            response.andExpect(content().string(containsString(text)));
        }
    }
}
