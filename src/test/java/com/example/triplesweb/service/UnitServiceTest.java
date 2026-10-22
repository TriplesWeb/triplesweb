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

    @Test void retainsHiddenParentAndChildAlbumsWithoutShowingHiddenTabs() {
        Unit parent = unit("parent", null, false);
        Unit visible = unit("visible", parent, true);
        Unit hidden = unit("hidden-child", visible, false);
        album("parent-album", parent);
        album("own-album", visible);
        album("child-album", hidden);
        em.flush();
        em.clear();

        var units = service.listVisible();
        assertThat(units).extracting(Unit::getUnitName).containsExactly("visible");
        assertThat(units.get(0).getDisplayAlbums()).extracting(Album::getAlbumName)
                .containsExactly("own-album", "parent-album", "child-album");
    }

    private Unit unit(String name, Unit parent, boolean visible) {
        Unit unit = new Unit(null, name, name, null, "2026-10-22", "", parent);
        unit.setVisible(visible);
        em.persist(unit);
        return unit;
    }

    private void album(String name, Unit unit) {
        Album album = new Album();
        album.setAlbumName(name);
        album.setAlbumInfo("info");
        album.setReleaseDate("2026-10-22");
        album.setUnit(unit);
        em.persist(album);
    }
}
