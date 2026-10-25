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
@Import(UnitAdminService.class)
class UnitAdminServiceTest {
    @Autowired EntityManager em;
    @Autowired UnitAdminService service;
    @MockitoBean FileStorageService fileStorageService;

    @Test
    void savingUnchangedMembersPreservesExistingLink() {
        Artist artist = artist("retained");
        Unit unit = unit(artist);
        Long linkId = unit.getUnitMembers().get(0).getId();
        em.clear();

        update(unit.getUnitId(), List.of(artist.getArtistId()));

        Unit saved = reload(unit.getUnitId());
        assertThat(saved.getUnitMembers()).extracting(UnitMember::getId).containsExactly(linkId);
        assertThat(saved.getUnitName()).isEqualTo("Updated");
    }

    @Test
    void changingMembersOnlyAddsAndRemovesChangedLinks() {
        Artist retained = artist("retained");
        Artist removed = artist("removed");
        Artist added = artist("added");
        Unit unit = unit(retained, removed);
        Long retainedLinkId = unit.getUnitMembers().get(0).getId();
        Long removedLinkId = unit.getUnitMembers().get(1).getId();
        em.clear();

        update(unit.getUnitId(), List.of(retained.getArtistId(), added.getArtistId(), added.getArtistId()));

        Unit saved = reload(unit.getUnitId());
        assertThat(saved.getUnitMembers()).extracting(link -> link.getArtist().getArtistId())
                .containsExactlyInAnyOrder(retained.getArtistId(), added.getArtistId());
        assertThat(saved.getUnitMembers()).extracting(UnitMember::getId).contains(retainedLinkId);
        assertThat(em.find(UnitMember.class, removedLinkId)).isNull();
    }

    @Test
    void emptySelectionRemovesAllMembers() {
        Unit unit = unit(artist("removed"));
        Long linkId = unit.getUnitMembers().get(0).getId();
        em.clear();

        update(unit.getUnitId(), List.of());

        assertThat(reload(unit.getUnitId()).getUnitMembers()).isEmpty();
        assertThat(em.find(UnitMember.class, linkId)).isNull();
    }

    @Test
    void clearingOnlyImageKeepsMembersAndRemovesImageReference() {
        Artist artist = artist("retained-with-image");
        Unit unit = unit(artist);
        unit.setUnitImage("/uploads/images/acideyes.jpg");
        em.flush();
        Long linkId = unit.getUnitMembers().get(0).getId();
        Long unitId = unit.getUnitId();
        Long artistId = artist.getArtistId();
        em.clear();

        var response = service.update(unitId, "Original", "original", "2026-10-22", "", true,
                null, List.of(artistId), null, null, true);

        assertThat(response.getUnitImage()).isNull();
        Unit saved = reload(unitId);
        assertThat(saved.getUnitImage()).isNull();
        assertThat(saved.getUnitMembers()).extracting(UnitMember::getId).containsExactly(linkId);
    }

    @Test
    void creatingUnitDeduplicatesMembers() {
        Artist artist = artist("new");
        Long id = service.create("New", "new", "2026-10-22", "", true, null,
                List.of(artist.getArtistId(), artist.getArtistId()), null, null).getUnitId();

        assertThat(reload(id).getUnitMembers()).hasSize(1);
    }

    @Test
    void parentCycleIsRejected() {
        Unit parent = unit(artist("parent"));
        Unit child = unit(artist("child"));
        service.update(child.getUnitId(), "Child", "child", "2026-10-22", "", true,
                parent.getUnitId(), List.of(), null, null, false);
        em.flush();
        em.clear();

        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () ->
                service.update(parent.getUnitId(), "Parent", "parent", "2026-10-22", "", true,
                        child.getUnitId(), List.of(), null, null, false));
    }

    private void update(Long unitId, List<Long> artistIds) {
        service.update(unitId, "Updated", "updated", "2026-10-22", "", true,
                null, artistIds, null, null, false);
    }

    private Unit reload(Long id) {
        em.flush();
        em.clear();
        return em.find(Unit.class, id);
    }

    private Unit unit(Artist... artists) {
        Unit unit = new Unit(null, "Original", "original", null, "2026-10-22", "", null);
        for (Artist artist : artists) {
            unit.getUnitMembers().add(new UnitMember(null, artist, unit));
        }
        em.persist(unit);
        em.flush();
        return unit;
    }

    private Artist artist(String name) {
        Artist artist = new Artist();
        artist.setName(name);
        artist.setEngName(name);
        artist.setNationality("KR");
        artist.setBirthDate("2000-01-01");
        artist.setBirthPlace("Seoul");
        artist.setDebutDate("2022-01-01");
        artist.setColor("#ffffff");
        em.persist(artist);
        return artist;
    }
}
