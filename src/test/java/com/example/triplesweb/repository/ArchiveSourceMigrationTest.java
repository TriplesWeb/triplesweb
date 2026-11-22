package com.example.triplesweb.repository;

import com.example.triplesweb.entity.ArchiveSource;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import static org.assertj.core.api.Assertions.assertThat;

class ArchiveSourceMigrationTest {
    @Test
    void convertsTeaserToTeaserBehindWithoutChangingOtherSources() {
        var dataSource = new EmbeddedDatabaseBuilder().setType(EmbeddedDatabaseType.H2).generateUniqueName(true).build();
        try {
            var jdbc = new JdbcTemplate(dataSource);
            jdbc.execute("CREATE TABLE archive (id INT PRIMARY KEY, source VARCHAR(32))");
            jdbc.update("INSERT INTO archive VALUES (1, 'TEASER_BEHIND'), (2, 'TEASER'), (3, 'MAGAZINE'), (4, NULL)");

            new ResourceDatabasePopulator(new ClassPathResource(
                    "db/migration/V202611211800__archive_merge_teaser_behind_source.sql")).execute(dataSource);

            var sources = jdbc.queryForList("SELECT source FROM archive ORDER BY id", String.class);
            assertThat(sources).containsExactly("TEASER_BEHIND", "TEASER_BEHIND", "MAGAZINE", null);
            assertThat(ArchiveSource.valueOf(sources.get(1))).isEqualTo(ArchiveSource.TEASER_BEHIND);
        } finally {
            dataSource.shutdown();
        }
    }
}
