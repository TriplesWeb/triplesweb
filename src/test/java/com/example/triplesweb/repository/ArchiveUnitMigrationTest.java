package com.example.triplesweb.repository;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ArchiveUnitMigrationTest {
    @Test
    void flywayCopiesExistingAssignmentsAndKeepsTheLegacyColumn() {
        var source = new DriverManagerDataSource(
                "jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        JdbcTemplate jdbc = new JdbcTemplate(source);
        jdbc.execute("CREATE TABLE archive (id BIGINT PRIMARY KEY, unit_id BIGINT)");
        jdbc.update("INSERT INTO archive VALUES (1, 7), (2, NULL), (3, 8)");
        Flyway.configure().dataSource(source).baselineOnMigrate(true).baselineVersion("202611251159")
                .target("202611251200").locations("classpath:db/migration").load().migrate();

        assertThat(jdbc.queryForList("SELECT archive_id FROM archive_unit ORDER BY archive_id", Long.class))
                .containsExactly(1L, 3L);
        assertThat(jdbc.queryForList("SELECT unit_id FROM archive_unit ORDER BY archive_id", Long.class))
                .containsExactly(7L, 8L);
        assertThat(jdbc.queryForObject("SELECT unit_id FROM archive WHERE id = 1", Long.class)).isEqualTo(7L);

        jdbc.update("INSERT INTO archive_unit VALUES (1, 8)");
        jdbc.update("DELETE FROM archive WHERE id = 1");
        assertThat(jdbc.queryForList("SELECT archive_id FROM archive_unit", Long.class)).containsExactly(3L);
    }
}
