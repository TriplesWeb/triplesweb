package com.example.triplesweb.repository;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ArchiveSourceCatalogMigrationTest {
    @Test
    void seedsSevenArchiveSourcesWithUniqueCodes() {
        var dataSource = new EmbeddedDatabaseBuilder()
                .setType(EmbeddedDatabaseType.H2)
                .generateUniqueName(true)
                .addScript("classpath:db/migration/V202611231100__archive_source_catalog.sql")
                .build();
        try {
            var jdbc = new JdbcTemplate(dataSource);
            // H2는 컬럼명을 대문자로 돌려준다.
            List<Map<String, Object>> rows = jdbc.queryForList(
                    "SELECT code, label, color, sort_order, visible FROM archive_source ORDER BY sort_order, id");
            assertThat(rows).hasSize(7);
            assertThat(rows).extracting(row -> String.valueOf(row.getOrDefault("CODE", row.get("code"))))
                    .containsExactly(
                            "COSMO_ROOM", "COSMO_TALK", "OFFICIAL_SNS", "POLAROID", "TEASER_BEHIND", "MAGAZINE", "EXTERNAL");
            Object color = rows.get(6).containsKey("COLOR") ? rows.get(6).get("COLOR") : rows.get(6).get("color");
            assertThat(color).isEqualTo("#38bdf8");
            assertThat(jdbc.queryForObject(
                    "SELECT COUNT(DISTINCT code) FROM archive_source", Integer.class)).isEqualTo(7);
        } finally {
            dataSource.shutdown();
        }
    }
}
