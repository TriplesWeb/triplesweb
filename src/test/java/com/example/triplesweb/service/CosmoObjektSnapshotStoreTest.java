package com.example.triplesweb.service;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class CosmoObjektSnapshotStoreTest {
    @Test void unchangedCardsDoNotRewriteAndRemovedCardsAreScopedToAccount() {
        var dataSource = new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        new ResourceDatabasePopulator(new ClassPathResource("db/migration/V35__cosmo_owned_objekt_snapshot.sql")).execute(dataSource);
        var jdbc = new JdbcTemplate(dataSource);
        var store = new CosmoObjektSnapshotStore(jdbc, new DataSourceTransactionManager(dataSource));
        var card = new CosmoObjektSnapshotStore.Card("100", "collection", 7L, "", "owned", "{}");
        assertThat(store.save("a", 1, List.of(card))).isEqualTo(1);
        assertThat(store.save("a", 1, List.of(card))).isZero();
        store.save("b", 1, List.of(card));
        assertThat(store.save("a", 0, List.of())).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM cosmo_owned_objekt WHERE scope_key='b'", Integer.class)).isEqualTo(1);
    }
    @Test void failedWriteRollsBackEarlierChanges() {
        var dataSource = new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        new ResourceDatabasePopulator(new ClassPathResource("db/migration/V35__cosmo_owned_objekt_snapshot.sql")).execute(dataSource);
        var jdbc = new JdbcTemplate(dataSource);
        var store = new CosmoObjektSnapshotStore(jdbc, new DataSourceTransactionManager(dataSource));
        var good = new CosmoObjektSnapshotStore.Card("100", "collection", 7L, "", "owned", "{}");
        var bad = new CosmoObjektSnapshotStore.Card("101", "x".repeat(121), 8L, "", "owned", "{}");
        assertThatThrownBy(() -> store.save("a", 2, List.of(good, bad))).isInstanceOf(RuntimeException.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM cosmo_owned_objekt", Integer.class)).isZero();
    }
}
