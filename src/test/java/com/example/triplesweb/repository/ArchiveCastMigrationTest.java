package com.example.triplesweb.repository;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ArchiveCastMigrationTest {
    @Test
    void flywayBackfillsLegacyAliasesBeforeCreatingTheIndexes() {
        var source = new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        JdbcTemplate jdbc = new JdbcTemplate(source);
        jdbc.execute("CREATE TABLE archive (id BIGINT PRIMARY KEY, artist_names VARCHAR(200), media_type VARCHAR(20))");
        jdbc.update("INSERT INTO archive VALUES (1, 'S1XS9', 'IMAGE'), (2, 'Kaede', 'IMAGE'), (3, NULL, 'IMAGE')");
        Flyway.configure().dataSource(source).baselineOnMigrate(true).baselineVersion("202611241399")
                .target("202611241420").locations("classpath:db/migration").load().migrate();

        assertThat(jdbc.queryForList("SELECT cast_member_mask FROM archive ORDER BY id", Integer.class)).containsExactly(257, 256, 0);
        assertThat(jdbc.queryForList("SELECT cast_member_count FROM archive ORDER BY id", Integer.class)).containsExactly(2, 1, 0);
        assertThat(jdbc.queryForList("SELECT cast_member_rank FROM archive ORDER BY id", Integer.class)).containsExactly(0, 8, 24);
    }
}
