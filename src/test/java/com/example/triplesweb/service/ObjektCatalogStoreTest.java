package com.example.triplesweb.service;

import com.example.triplesweb.entity.ObjektCatalog;
import com.example.triplesweb.repository.ObjektCatalogRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;

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

    @Test void onlyChainModeRemovesOtherArtistsAtStartup() {
        repository.save(row("triples", "Cream02 HaYeon 001Z", "triples"));
        repository.save(row("artms-keep-id", "Drop HeeJin 101Z", "artms"));
        var catalog = new ObjektService("http://localhost", 3000, store, null);

        catalog.loadStoredCatalog();
        assertThat(repository.findAll()).extracting(ObjektCatalog::getArtist)
                .containsExactlyInAnyOrder("triples", "artms");
        assertThat(catalog.findByKey("triples")).isNotNull();

        ReflectionTestUtils.setField(catalog, "chainMints", true);
        catalog.loadStoredCatalog();
        assertThat(repository.findAll()).extracting(ObjektCatalog::getCollectionId)
                .containsExactly("Cream02 HaYeon 001Z");
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
        assertThat(catalog.findByKey("chain")).containsEntry("backImage", "");

        ReflectionTestUtils.setField(catalog, "chainMints", true);
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

    @Test void pendingCollectionKeepsFirstTimestampUntilStoredCatalogContainsIt() {
        var catalog = new ObjektService("http://localhost", 3000, store, null);
        var first = row("cream02-hayeon-367z", "Cream02 HaYeon 367Z", "triples");
        first.setCreatedAt("2026-11-26T01:00:00Z");
        var later = row(first.getSlug(), "cream02  hayeon 367z ", "triples");
        later.setCreatedAt("2026-11-26T01:01:00Z");

        catalog.publishPendingCollections(List.of(first));
        catalog.publishPendingCollections(List.of());
        catalog.publishPendingCollections(List.of(later));
        assertThat(catalog.findCollections("triples")).hasSize(1);
        assertThat(catalog.findByKey(first.getSlug()))
                .containsEntry("mintPending", true).containsEntry("createdAt", first.getCreatedAt());

        later.setSlug("legacy-hayeon");
        repository.save(later);
        catalog.reloadStoredCatalog();
        catalog.publishPendingCollections(List.of(first));
        assertThat(catalog.findCollections("triples")).hasSize(1);
        assertThat(catalog.findStoredByKey(first.getCollectionId()))
                .containsEntry("slug", "legacy-hayeon").doesNotContainKey("mintPending");
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
