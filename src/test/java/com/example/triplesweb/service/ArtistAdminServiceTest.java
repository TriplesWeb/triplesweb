package com.example.triplesweb.service;

import com.example.triplesweb.entity.Artist;
import com.example.triplesweb.entity.Unit;
import com.example.triplesweb.entity.UnitMember;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect")
@ActiveProfiles("test")
@Import({ArtistAdminService.class, UnitService.class})
class ArtistAdminServiceTest {
    @Autowired EntityManager em;
    @Autowired ArtistAdminService service;
    @MockitoBean FileStorageService fileStorageService;

    @Test
    void savingUnchangedUnitsPreservesExistingLink() {
        Unit unit = unit("aaa");
        Artist artist = linkedArtist("retained", unit);
        Long linkId = artist.getAffiliatedUnit().get(0).getId();
        em.clear();

        update(artist.getArtistId(), List.of(unit.getUnitId()));

        Artist saved = reload(artist.getArtistId());
        assertThat(saved.getAffiliatedUnit()).extracting(UnitMember::getId).containsExactly(linkId);
        assertThat(saved.getName()).isEqualTo("Updated");
    }

    @Test
    void changingUnitsOnlyAddsAndRemovesChangedLinks() {
        Unit retained = unit("retained");
        Unit removed = unit("removed");
        Unit added = unit("added");
        Artist artist = linkedArtist("member", retained, removed);
        Long retainedLinkId = artist.getAffiliatedUnit().stream()
                .filter(link -> link.getUnit().getUnitId().equals(retained.getUnitId()))
                .findFirst()
                .orElseThrow()
                .getId();
        Long removedLinkId = artist.getAffiliatedUnit().stream()
                .filter(link -> link.getUnit().getUnitId().equals(removed.getUnitId()))
                .findFirst()
                .orElseThrow()
                .getId();
        em.clear();

        update(artist.getArtistId(), List.of(retained.getUnitId(), added.getUnitId(), added.getUnitId()));

        Artist saved = reload(artist.getArtistId());
        assertThat(saved.getAffiliatedUnit()).extracting(link -> link.getUnit().getUnitId())
                .containsExactlyInAnyOrder(retained.getUnitId(), added.getUnitId());
        assertThat(saved.getAffiliatedUnit()).extracting(UnitMember::getId).contains(retainedLinkId);
        assertThat(em.find(UnitMember.class, removedLinkId)).isNull();
    }

    @Test
    void emptySelectionRemovesAllUnits() {
        Unit unit = unit("removed");
        Artist artist = linkedArtist("member", unit);
        Long linkId = artist.getAffiliatedUnit().get(0).getId();
        em.clear();

        update(artist.getArtistId(), List.of());

        assertThat(reload(artist.getArtistId()).getAffiliatedUnit()).isEmpty();
        assertThat(em.find(UnitMember.class, linkId)).isNull();
    }

    @Test
    void preservesParentContainerMembershipWhenUiOmitsIt() {
        Unit parent = unit("msnz");
        Unit child = unit("msnz moon");
        child.setParentUnit(parent);
        Artist artist = linkedArtist("wav", parent, child);
        Long parentLinkId = artist.getAffiliatedUnit().stream()
                .filter(link -> link.getUnit().getUnitId().equals(parent.getUnitId()))
                .findFirst()
                .orElseThrow()
                .getId();
        em.clear();

        // 체크 UI에는 자식만 보이므로 부모 id가 제출되지 않는다.
        update(artist.getArtistId(), List.of(child.getUnitId()));

        Artist saved = reload(artist.getArtistId());
        assertThat(saved.getAffiliatedUnit()).extracting(link -> link.getUnit().getUnitId())
                .containsExactlyInAnyOrder(parent.getUnitId(), child.getUnitId());
        assertThat(saved.getAffiliatedUnit()).extracting(UnitMember::getId).contains(parentLinkId);
    }

    @Test
    void preservesHiddenUnitMembershipWhenUiOmitsIt() {
        Unit hidden = unit("hidden-container");
        hidden.setVisible(false);
        Unit visible = unit("visible-unit");
        Artist artist = linkedArtist("member", hidden, visible);
        Long hiddenLinkId = artist.getAffiliatedUnit().stream()
                .filter(link -> link.getUnit().getUnitId().equals(hidden.getUnitId()))
                .findFirst()
                .orElseThrow()
                .getId();
        em.clear();

        update(artist.getArtistId(), List.of(visible.getUnitId()));

        Artist saved = reload(artist.getArtistId());
        assertThat(saved.getAffiliatedUnit()).extracting(link -> link.getUnit().getUnitId())
                .containsExactlyInAnyOrder(hidden.getUnitId(), visible.getUnitId());
        assertThat(saved.getAffiliatedUnit()).extracting(UnitMember::getId).contains(hiddenLinkId);
    }

    private void update(Long artistId, List<Long> unitIds) {
        service.updateArtist(
                artistId,
                "Updated",
                "updated",
                "KR",
                "2000-01-01",
                "",
                "Seoul",
                "",
                "",
                "2022-01-01",
                "2022-01-02",
                "#ffffff",
                "",
                "",
                unitIds,
                null,
                false,
                null,
                false
        );
    }

    private Artist reload(Long id) {
        em.flush();
        em.clear();
        return em.find(Artist.class, id);
    }

    private Unit unit(String name) {
        Unit unit = new Unit(null, name, name, null, "2026-10-22", "", null);
        em.persist(unit);
        return unit;
    }

    private Artist linkedArtist(String name, Unit... units) {
        Artist artist = new Artist();
        artist.setName(name);
        artist.setEngName(name);
        artist.setNationality("KR");
        artist.setBirthDate("2000-01-01");
        artist.setBirthPlace("Seoul");
        artist.setRevealDate("2022-01-01");
        artist.setDormEntryDate("2022-01-02");
        artist.setColor("#ffffff");
        em.persist(artist);
        for (Unit unit : units) {
            artist.getAffiliatedUnit().add(new UnitMember(null, artist, unit));
        }
        em.flush();
        return artist;
    }
}
