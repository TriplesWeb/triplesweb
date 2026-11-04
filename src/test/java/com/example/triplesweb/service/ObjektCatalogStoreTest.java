package com.example.triplesweb.service;

import com.example.triplesweb.entity.ObjektCatalog;
import com.example.triplesweb.repository.ObjektCatalogRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect")
@ActiveProfiles("test")
@Import(ObjektCatalogStore.class)
class ObjektCatalogStoreTest {
    @Autowired ObjektCatalogStore store;
    @Autowired ObjektCatalogRepository repository;

    @Test void retainOnlyDeletesArtistsOtherThanTriples() {
        repository.save(row("cream02-hayeon-367z", "Cream02 HaYeon 367Z", "triples"));
        repository.save(row("drop-heejin-101z", "Drop HeeJin 101Z", "artms"));
        repository.save(row("idntt-one-001z", "One IDNTT 001Z", "idntt"));
        store.retainOnly("triples");
        assertThat(repository.findAll()).extracting(ObjektCatalog::getArtist).containsExactly("triples");
        assertThat(store.load("triples")).extracting(ObjektCatalog::getCollectionId)
                .containsExactly("Cream02 HaYeon 367Z");
        assertThat(store.load("artms")).isEmpty();
    }

    @Test void replaceKeepsIncomingTriplesAndDropsOtherArtists() {
        repository.save(row("old-triples", "Cream02 HaYeon 001Z", "triples"));
        repository.save(row("artms-keep-id", "Drop HeeJin 101Z", "artms"));
        ObjektCatalog incoming = row("cream02-hayeon-367z", "Cream02 HaYeon 367Z", "triples");
        store.replace("triples", java.util.List.of(incoming));
        assertThat(repository.findAll()).extracting(ObjektCatalog::getCollectionId)
                .containsExactly("Cream02 HaYeon 367Z");
        assertThat(repository.findAll()).extracting(ObjektCatalog::getArtist).containsExactly("triples");
    }

    private static ObjektCatalog row(String slug, String collectionId, String artist) {
        ObjektCatalog row = new ObjektCatalog();
        row.setSlug(slug);
        row.setCollectionId(collectionId);
        row.setArtist(artist);
        row.setNumberValue(1);
        return row;
    }
}
