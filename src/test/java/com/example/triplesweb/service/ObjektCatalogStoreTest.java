package com.example.triplesweb.service;

import com.example.triplesweb.entity.ObjektCatalog;
import com.example.triplesweb.repository.ObjektCatalogRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

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

    @Test void dailyEnrichmentFillsOnlyMissingBackImagesForChainCollections() {
        ObjektCatalog chain = row("chain", "Chain collection", "triples");
        chain.setExternalId("chain");
        repository.save(chain);
        ObjektCatalog existing = row("existing", "Existing collection", "triples");
        existing.setExternalId("existing");
        existing.setBackImage("/existing.jpg");
        repository.save(existing);
        repository.save(row("legacy", "Legacy collection", "triples"));

        var external = List.of(chain, existing, row("legacy", "Legacy collection", "triples")).stream()
                .map(item -> {
                    ObjektCatalog update = row("external-" + item.getSlug(), item.getCollectionId(), "triples");
                    update.setBackImage("/from-catalog.jpg");
                    return update;
                }).toList();

        var catalog = new ObjektService("http://localhost", 3000, store, null);
        catalog.reloadStoredCatalog();
        catalog.enrichChainBackImages(external);

        assertThat(catalog.findByKey("chain")).containsEntry("backImage", "/from-catalog.jpg");
        assertThat(store.fillMissingChainBackImages(external)).isZero();
        assertThat(repository.findAll()).filteredOn(item -> item.getSlug().equals("chain"))
                .extracting(ObjektCatalog::getBackImage).containsExactly("/from-catalog.jpg");
        assertThat(repository.findAll()).filteredOn(item -> item.getSlug().equals("existing"))
                .extracting(ObjektCatalog::getBackImage).containsExactly("/existing.jpg");
        assertThat(repository.findAll()).filteredOn(item -> item.getSlug().equals("legacy"))
                .extracting(ObjektCatalog::getBackImage).containsExactly((String) null);
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
