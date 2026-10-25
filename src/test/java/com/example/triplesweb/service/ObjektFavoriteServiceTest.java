package com.example.triplesweb.service;

import com.example.triplesweb.entity.ObjektFavorite;
import com.example.triplesweb.repository.ObjektFavoriteRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect")
@ActiveProfiles("test")
@Import(ObjektFavoriteService.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ObjektFavoriteServiceTest {
    @Autowired ObjektFavoriteService service;
    @Autowired ObjektFavoriteRepository repository;
    private final Map<String, Object> item = Map.of("id", "uuid", "slug", "cream02-hayeon-367z", "collectionId", "Cream02 HaYeon 367Z");

    @Test void repeatedSetAndRemoveAreIdempotentAndDeletionHasOwnTransaction() {
        String visitor = "repeat";
        service.set(visitor, item, true);
        service.set(visitor, item, true);
        assertThat(repository.findByVisitorKeyOrderByCreatedAtDesc(visitor)).hasSize(1)
                .extracting(ObjektFavorite::getObjektKey).containsExactly("uuid");
        service.set(visitor, item, false);
        service.set(visitor, item, false);
        assertThat(service.contains(visitor, item)).isFalse();
    }

    @Test void removesAllLegacyAliasKeysOnlyForCurrentVisitor() {
        for (String key : service.aliases(item)) {
            ObjektFavorite favorite = new ObjektFavorite();
            favorite.setVisitorKey("legacy"); favorite.setObjektKey(key); repository.saveAndFlush(favorite);
        }
        service.set("other", item, true);
        assertThat(service.contains("legacy", item)).isTrue();
        service.set("legacy", item, false);
        assertThat(repository.findByVisitorKeyOrderByCreatedAtDesc("legacy")).isEmpty();
        assertThat(service.contains("other", item)).isTrue();
    }

    @Test void removesFavoritesEvenWhenCatalogItemHasDisappeared() {
        service.set("gone", item, true);
        service.remove("gone", "uuid");
        assertThat(service.contains("gone", item)).isFalse();
    }
}
