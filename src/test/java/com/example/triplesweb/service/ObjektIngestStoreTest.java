package com.example.triplesweb.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import java.util.List;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

class ObjektIngestStoreTest {
    private JdbcTemplate jdbc;
    private ObjektIngestStore store;
    private static final String A = "0x" + "a".repeat(40), B = "0x" + "b".repeat(40), H = "0x" + "c".repeat(64);

    @BeforeEach void setup() {
        var source = new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        new ResourceDatabasePopulator(new ClassPathResource("db/migration/V36__objekt_continuous_observations.sql")).execute(source);
        jdbc = new JdbcTemplate(source);
        store = new ObjektIngestStore(jdbc, new DataSourceTransactionManager(source));
    }

    private ObjektActivityClient.Event event(String id, long time, String owner, Long serial) {
        return new ObjektActivityClient.Event(id, "123", A, owner, H, time, "slug", "C1", "triples", serial);
    }

    @Test void restartAndDuplicatePagesDoNotAdvanceTwice() {
        var first = store.state("live");
        var page = new ObjektActivityClient.Page(List.of(event("z", 10, A, 7L)), new ObjektActivityClient.Cursor("2026-10-26T00:00:00Z", "z"));
        assertThat(store.commit(first, page, 100, 100, 0, false)).isTrue();
        assertThat(store.commit(first, page, 100, 100, 0, false)).isFalse();
        var restarted = new ObjektIngestStore(jdbc, new DataSourceTransactionManager(((DriverManagerDataSource) jdbc.getDataSource())));
        assertThat(restarted.state("live").cursor()).isEqualTo(page.next());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM objekt_observed_transfer", Integer.class)).isEqualTo(1);
    }

    @Test void pageFailureRollsBackRowsAndCursor() {
        var state = store.state("live");
        var bad = new ObjektActivityClient.Event("bad", "x".repeat(90), A, B, H, 10, "slug", "C1", "triples", 7L);
        assertThatThrownBy(() -> store.commit(state, new ObjektActivityClient.Page(List.of(event("ok", 11, B, 7L), bad), null), 100, 100, 0, true))
                .isInstanceOf(RuntimeException.class);
        assertThat(store.state("live").version()).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM objekt_observed_transfer", Integer.class)).isZero();
    }

    @Test void backfillCannotReplaceCurrentOwnerAndConfirmationSurvivesEstimate() {
        var live = store.state("live");
        assertThat(store.commit(live, new ObjektActivityClient.Page(List.of(event("z", 20, B, 9L)), null), 100, 100, 0, true)).isTrue();
        var backfill = store.state("backfill");
        assertThat(store.commit(backfill, new ObjektActivityClient.Page(List.of(event("a", 10, A, 8L)), null), 110, 110, 0, true)).isTrue();
        assertThat(jdbc.queryForObject("SELECT indexed_owner FROM objekt_observed_token WHERE token_id='123'", String.class)).isEqualTo(B);
        store.confirm(List.of(new CosmoObjektSnapshotStore.Card("123", "C1", 7L, "", "owned", "{}")));
        assertThat(jdbc.queryForObject("SELECT confirmed_serial FROM objekt_observed_token WHERE token_id='123'", Long.class)).isEqualTo(7L);
        assertThat(jdbc.queryForObject("SELECT reported_serial FROM objekt_observed_token WHERE token_id='123'", Long.class)).isEqualTo(8L);
        assertThatThrownBy(() -> store.confirm(List.of(new CosmoObjektSnapshotStore.Card("123", "C1", 6L, "", "owned", "{}"))))
                .isInstanceOf(IllegalStateException.class);
    }
}
