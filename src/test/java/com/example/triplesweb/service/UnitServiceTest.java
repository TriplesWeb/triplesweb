package com.example.triplesweb.service;

import com.example.triplesweb.entity.Album;
import com.example.triplesweb.entity.Artist;
import com.example.triplesweb.entity.Unit;
import com.example.triplesweb.entity.UnitMember;
import jakarta.persistence.EntityManager;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = {
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.jpa.properties.hibernate.generate_statistics=true",
        "logging.level.org.hibernate.engine.internal.StatisticalLoggingSessionEventListener=OFF"
})
@ActiveProfiles("test")
@Import(UnitService.class)
class UnitServiceTest {
    @Autowired EntityManager em;
    @Autowired UnitService service;

    @Test void pageLoadsAllTabsWithAtMostTwoQueries() {
        for (int i = 0; i < 24; i++) {
            Unit unit = unit("unit-" + i, null, true);
            Artist artist = new Artist();
            artist.setName("artist-" + i);
            artist.setEngName("artist-" + i);
            artist.setNationality("KR");
            artist.setBirthDate("2000-01-01");
            artist.setBirthPlace("Seoul");
            artist.setDebutDate("2022-01-01");
            artist.setColor("#fff");
            em.persist(artist);
            em.persist(new UnitMember(null, artist, unit));
            album("album-" + i, unit);
        }
        em.flush();
        em.clear();
        var statistics = em.getEntityManagerFactory().unwrap(SessionFactory.class).getStatistics();
        statistics.clear();

        var units = service.listVisible();
        assertThat(units).hasSize(24);
        // Include every getter used while rendering the page, after the service returns.
        for (Unit unit : units) {
            assertThat(unit.getDisplayAlbums()).hasSize(1);
            unit.getUnitMembers().forEach(member -> member.getArtist().getProfileImage());
        }
        System.out.println("Unit list SQL statements: " + statistics.getPrepareStatementCount());
        assertThat(statistics.getPrepareStatementCount()).isLessThanOrEqualTo(2);
    }

    @Test void unitMembersAreOrderedByArtistId() {
        Unit unit = unit("ordered", null, true);
        Artist s1 = artist("s1", "s1");
        Artist s2 = artist("s2", "s2");
        em.persist(s1);
        em.persist(s2);
        // Insert higher id first so raw join order differs from S order.
        em.persist(new UnitMember(null, s2, unit));
        em.persist(new UnitMember(null, s1, unit));
        Long id1 = s1.getArtistId();
        Long id2 = s2.getArtistId();
        em.flush();
        em.clear();

        var units = service.listVisible();
        assertThat(units).hasSize(1);
        assertThat(units.get(0).getUnitMembers())
                .extracting(member -> member.getArtist().getArtistId())
                .containsExactly(id1, id2);
    }

    private Artist artist(String name, String engName) {
        Artist artist = new Artist();
        artist.setName(name);
        artist.setEngName(engName);
        artist.setNationality("KR");
        artist.setBirthDate("2000-01-01");
        artist.setBirthPlace("Seoul");
        artist.setDebutDate("2022-01-01");
        artist.setColor("#fff");
        return artist;
    }

    @Test void retainsHiddenParentAndChildAlbumsWithoutShowingHiddenTabs() {
        Unit parent = unit("parent", null, false);
        Unit visible = unit("visible", parent, true);
        Unit hidden = unit("hidden-child", visible, false);
        album("parent-album", parent, "2024-01-01");
        album("own-album", visible, "2025-01-01");
        album("child-album", hidden, "2026-01-01");
        em.flush();
        em.clear();

        var units = service.listVisible();
        assertThat(units).extracting(Unit::getUnitName).containsExactly("visible");
        assertThat(units.get(0).getDisplayAlbums()).extracting(Album::getAlbumName)
                .containsExactly("parent-album", "own-album", "child-album");
    }

    @Test void displayAlbumsAreOrderedByReleaseDateAcrossInheritedUnits() {
        Unit parent = unit("msnz", null, false);
        Unit visible = unit("moon", parent, true);
        // Own album is newer but would appear first without a global release-date sort.
        album("Dream Dress", visible, "2025-05-12");
        album("Beyond Beauty", parent, "2024-11-20");
        em.flush();
        em.clear();

        var units = service.listVisible();
        assertThat(units).hasSize(1);
        assertThat(units.get(0).getDisplayAlbums()).extracting(Album::getAlbumName)
                .containsExactly("Beyond Beauty", "Dream Dress");
    }

    private Unit unit(String name, Unit parent, boolean visible) {
        Unit unit = new Unit(null, name, name, null, "2026-10-22", "", parent);
        unit.setVisible(visible);
        em.persist(unit);
        return unit;
    }

    private void album(String name, Unit unit) {
        album(name, unit, "2026-10-22");
    }

    private void album(String name, Unit unit, String releaseDate) {
        Album album = new Album();
        album.setAlbumName(name);
        album.setAlbumInfo("info");
        album.setReleaseDate(releaseDate);
        album.setUnit(unit);
        em.persist(album);
    }
}
