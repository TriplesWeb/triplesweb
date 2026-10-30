package com.example.triplesweb.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Flyway 이후 남은 스키마 정리.
 * Chemistry memberA/B → Artist FK, unit_member UNIQUE.
 * {@link com.example.triplesweb.config.DataInitializer} 이후,
 * {@link com.example.triplesweb.service.ChemistrySeedService} 이전에 실행되어야 한다.
 * 테이블/컬럼명은 CamelCaseToUnderscores snake_case만 사용한다.
 */
@Slf4j
@Component
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name = "schema.improve.enabled", havingValue = "true", matchIfMissing = true)
@Order(2)
@Profile("!test")
public class SchemaImproveRunner implements ApplicationRunner {

    private final JdbcTemplate jdbc;

    public SchemaImproveRunner(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * DDL은 MariaDB에서 암시적 커밋을 일으키고 연결을 끊을 수 있다.
     * JPA 트랜잭션으로 묶으면 롤백이 {@code Connection is closed}로 실패해 앱 기동이 멈춘다.
     */
    @Override
    public void run(ApplicationArguments args) {
        try {
            migrateChemistryToArtistFk();
            ensureUnitMemberUnique();
            ensureSavedMetaColumns("article_file");
            ensureSavedMetaColumns("shop_image");
            dropLegacyCamelColumns("article_file");
            dropLegacyCamelColumns("shop_image");
            relaxUnmappedRequiredColumns("article_file");
            relaxUnmappedRequiredColumns("shop_image");
        } catch (RuntimeException e) {
            log.warn("Schema improve skipped; application will still start: {}", e.getMessage());
        }
    }

    private long count(String sql, Object... args) {
        try {
            Long n = jdbc.queryForObject(sql, Long.class, args);
            return n == null ? 0 : n;
        } catch (RuntimeException e) {
            log.warn("Schema lookup skipped: {}", e.getMessage());
            return 0;
        }
    }

    private static String cell(java.util.Map<String, Object> row, String name) {
        for (var entry : row.entrySet()) {
            if (entry.getKey() != null && entry.getKey().equalsIgnoreCase(name) && entry.getValue() != null) {
                return entry.getValue().toString();
            }
        }
        return "";
    }

    private boolean execute(String sql, Object... args) {
        try {
            jdbc.update(sql, args);
            return true;
        } catch (RuntimeException e) {
            log.warn("Schema statement skipped: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Hibernate는 snake_case만 INSERT 한다. camelCase NOT NULL 컬럼이 남으면
     * {@code Field 'fileType' doesn't have a default value} 로 저장이 실패한다.
     */
    private void dropLegacyCamelColumns(String table) {
        if (!tableExists(table)) {
            return;
        }
        for (String column : new String[] {"fileType", "originalName", "savedName", "mediaType"}) {
            if (!columnExists(table, column)) {
                continue;
            }
            if (execute("ALTER TABLE `" + table + "` DROP COLUMN `" + column + "`")) {
                log.info("Dropped legacy column {}.{}", table, column);
            }
        }
    }

    /** JPA가 쓰지 않는 NOT NULL 컬럼은 값 없이 INSERT 되도록 NULL 허용으로 바꾼다. */
    private void relaxUnmappedRequiredColumns(String table) {
        if (!tableExists(table)) {
            return;
        }
        java.util.List<java.util.Map<String, Object>> rows;
        try {
            rows = jdbc.queryForList("""
                    SELECT COLUMN_NAME, COLUMN_TYPE
                    FROM information_schema.COLUMNS
                    WHERE TABLE_SCHEMA = DATABASE()
                      AND TABLE_NAME = ?
                      AND IS_NULLABLE = 'NO'
                      AND COLUMN_DEFAULT IS NULL
                      AND EXTRA NOT LIKE '%auto_increment%'
                    """, table);
        } catch (RuntimeException e) {
            log.warn("Unmapped column lookup skipped for {}: {}", table, e.getMessage());
            return;
        }
        java.util.Set<String> mapped = java.util.Set.of(
                "id", "article_id", "original_name", "saved_name", "url", "size", "file_type", "media_type", "title");
        for (java.util.Map<String, Object> row : rows) {
            String column = cell(row, "COLUMN_NAME");
            String columnType = cell(row, "COLUMN_TYPE");
            if (mapped.contains(column)) {
                continue;
            }
            if (execute("ALTER TABLE `" + table + "` MODIFY COLUMN `" + column + "` " + columnType + " NULL")) {
                log.info("Made unmapped column {}.{} nullable", table, column);
            }
        }
    }

    /**
     * {@code SavedMeta} snake_case 컬럼이 없으면 추가하고, 남아 있는 camelCase 값을 복사한다.
     * V26 rename이 건너뛰어진 뒤 V27이 mediaType만 지우면 media_type이 없어 상세 조회가 실패한다.
     */
    private void ensureSavedMetaColumns(String table) {
        if (!tableExists(table)) {
            return;
        }
        try {
            addColumnIfMissing(table, "original_name", "VARCHAR(300) NOT NULL DEFAULT ''");
            addColumnIfMissing(table, "saved_name", "VARCHAR(400) NOT NULL DEFAULT ''");
            addColumnIfMissing(table, "url", "VARCHAR(512) NOT NULL DEFAULT ''");
            addColumnIfMissing(table, "size", "BIGINT NOT NULL DEFAULT 0");
            addColumnIfMissing(table, "file_type", "VARCHAR(255) NOT NULL DEFAULT 'application/octet-stream'");
            addColumnIfMissing(table, "media_type", "VARCHAR(255) NOT NULL DEFAULT 'OTHER'");
            copyColumnIfPresent(table, "originalName", "original_name");
            copyColumnIfPresent(table, "savedName", "saved_name");
            copyColumnIfPresent(table, "fileType", "file_type");
            copyMediaType(table);
            backfillMediaType(table);
        } catch (RuntimeException e) {
            log.warn("Ensure SavedMeta columns on {} failed: {}", table, e.getMessage());
        }
    }

    private void addColumnIfMissing(String table, String column, String definition) {
        if (columnExists(table, column)) {
            return;
        }
        execute("ALTER TABLE `" + table + "` ADD COLUMN `" + column + "` " + definition);
        log.info("Added {}.{}", table, column);
    }

    private void copyColumnIfPresent(String table, String fromColumn, String toColumn) {
        if (!columnExists(table, fromColumn) || !columnExists(table, toColumn)) {
            return;
        }
        execute("UPDATE `" + table + "` SET `" + toColumn + "` = `" + fromColumn + "`"
                + " WHERE `" + fromColumn + "` IS NOT NULL AND `" + fromColumn + "` <> ''"
                + " AND (`" + toColumn + "` IS NULL OR `" + toColumn + "` = ''"
                + " OR `" + toColumn + "` = 'application/octet-stream')");
    }

    private void copyMediaType(String table) {
        if (!columnExists(table, "mediaType") || !columnExists(table, "media_type")) {
            return;
        }
        execute("UPDATE `" + table + "` SET media_type = CASE"
                + " WHEN UPPER(mediaType) IN ('IMAGE','PHOTO') THEN 'IMAGE'"
                + " WHEN UPPER(mediaType) = 'VIDEO' THEN 'VIDEO'"
                + " ELSE COALESCE(NULLIF(media_type, ''), 'OTHER') END"
                + " WHERE mediaType IS NOT NULL AND mediaType <> ''");
    }

    private void backfillMediaType(String table) {
        if (!columnExists(table, "media_type") || !columnExists(table, "file_type")
                || !columnExists(table, "saved_name")) {
            return;
        }
        execute("UPDATE `" + table + "` SET media_type = CASE"
                + " WHEN file_type LIKE 'image/%'"
                + " OR LOWER(saved_name) REGEXP '\\\\.(jpg|jpeg|png|gif|webp|bmp)$' THEN 'IMAGE'"
                + " WHEN file_type LIKE 'video/%'"
                + " OR LOWER(saved_name) REGEXP '\\\\.(mp4|mov|m4v|webm|mkv|avi)$' THEN 'VIDEO'"
                + " ELSE media_type END"
                + " WHERE media_type IS NULL OR media_type = '' OR media_type = 'OTHER'");
    }

    private void migrateChemistryToArtistFk() {
        if (!tableExists("chemistry") || !tableExists("artist")) {
            return;
        }

        if (!columnExists("artist", "artist_id")) {
            log.warn("Artist PK column artist_id not found");
            return;
        }

        String memberACol = firstExistingColumn("chemistry", "member_a", "memberA", "membera");
        String memberBCol = firstExistingColumn("chemistry", "member_b", "memberB", "memberb");
        boolean hasMemberA = memberACol != null && memberBCol != null;
        boolean hasArtistA = columnExists("chemistry", "artist_a_id");

        if (hasMemberA) {
            if (!hasArtistA) {
                execute("ALTER TABLE chemistry"
                        + " ADD COLUMN artist_a_id BIGINT NULL, ADD COLUMN artist_b_id BIGINT NULL");
            }
            execute("UPDATE chemistry c"
                    + " INNER JOIN artist a ON a.name = c." + memberACol
                    + " INNER JOIN artist b ON b.name = c." + memberBCol
                    + " SET c.artist_a_id = a.artist_id, c.artist_b_id = b.artist_id"
                    + " WHERE c.artist_a_id IS NULL OR c.artist_b_id IS NULL");
            execute("DELETE FROM chemistry WHERE artist_a_id IS NULL OR artist_b_id IS NULL");
            if (indexExists("chemistry", "uk_chemistry_pair")) {
                execute("ALTER TABLE chemistry DROP INDEX uk_chemistry_pair");
            }
            execute("ALTER TABLE chemistry DROP COLUMN " + memberACol + ", DROP COLUMN " + memberBCol);
            log.info("Migrated chemistry.{} → artist_a_id/artist_b_id", memberACol);
            hasArtistA = true;
            hasMemberA = false;
        }

        if (hasArtistA && !hasMemberA) {
            if (columnIsNullable("chemistry", "artist_a_id") || columnIsNullable("chemistry", "artist_b_id")) {
                execute("ALTER TABLE chemistry"
                        + " MODIFY COLUMN artist_a_id BIGINT NOT NULL, MODIFY COLUMN artist_b_id BIGINT NOT NULL");
            }
            if (!indexExists("chemistry", "uk_chemistry_pair")) {
                execute("ALTER TABLE chemistry"
                        + " ADD CONSTRAINT uk_chemistry_pair UNIQUE (artist_a_id, artist_b_id)");
            }
            ensureChemistryFk("chemistry", "artist", "artist_id", "fk_chemistry_artist_a", "artist_a_id");
            ensureChemistryFk("chemistry", "artist", "artist_id", "fk_chemistry_artist_b", "artist_b_id");
        }
    }

    private String firstExistingColumn(String table, String... names) {
        for (String n : names) {
            String resolved = resolveColumnName(table, n);
            if (resolved != null) {
                return resolved;
            }
        }
        return null;
    }

    private void ensureChemistryFk(
            String chemTable,
            String artistTable,
            String artistPk,
            String baseName,
            String column
    ) {
        if (foreignKeyExists(chemTable, column, artistTable, artistPk)) {
            log.info("Chemistry FK already exists: {}.{} -> {}.{}", chemTable, column, artistTable, artistPk);
            return;
        }

        String constraintName = baseName;
        int suffix = 2;
        while (constraintNameExists(constraintName)) {
            constraintName = baseName + "_" + suffix++;
        }

        execute("ALTER TABLE `" + chemTable + "` ADD CONSTRAINT `" + constraintName + "`"
                + " FOREIGN KEY (`" + column + "`)"
                + " REFERENCES `" + artistTable + "` (`" + artistPk + "`)");
        log.info("Added Chemistry FK: {} on {}.{} -> {}.{}", constraintName, chemTable, column, artistTable, artistPk);
    }

    private boolean foreignKeyExists(
            String table,
            String column,
            String referencedTable,
            String referencedColumn
    ) {
        return count("""
                SELECT COUNT(*)
                FROM information_schema.KEY_COLUMN_USAGE
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME = ?
                  AND COLUMN_NAME = ?
                  AND REFERENCED_TABLE_SCHEMA = DATABASE()
                  AND REFERENCED_TABLE_NAME = ?
                  AND REFERENCED_COLUMN_NAME = ?
                """, table, column, referencedTable, referencedColumn) > 0;
    }

    private boolean constraintNameExists(String name) {
        return count("""
                SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS
                WHERE CONSTRAINT_SCHEMA = DATABASE() AND CONSTRAINT_NAME = ?
                """, name) > 0;
    }

    private void ensureUnitMemberUnique() {
        if (!tableExists("unit_member") || indexExists("unit_member", "uk_unit_member")) {
            return;
        }
        if (!columnExists("unit_member", "artist_id") || !columnExists("unit_member", "unit_id")) {
            return;
        }
        execute("ALTER TABLE unit_member ADD CONSTRAINT uk_unit_member UNIQUE (artist_id, unit_id)");
        log.info("Added uk_unit_member on unit_member");
    }

    private boolean tableExists(String table) {
        return count("""
                SELECT COUNT(*) FROM information_schema.TABLES
                WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ?
                """, table) > 0;
    }

    private boolean columnExists(String table, String column) {
        return count("""
                SELECT COUNT(*) FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME = ?
                  AND COLUMN_NAME = ?
                """, table, column) > 0;
    }

    /** information_schema에 실제 저장된 컬럼명(대소문자 포함)을 반환한다. */
    private String resolveColumnName(String table, String column) {
        try {
            return jdbc.query("""
                    SELECT COLUMN_NAME FROM information_schema.COLUMNS
                    WHERE TABLE_SCHEMA = DATABASE()
                      AND TABLE_NAME = ?
                      AND LOWER(COLUMN_NAME) = LOWER(?)
                    LIMIT 1
                    """, (rs, row) -> rs.getString(1), table, column)
                    .stream().findFirst().orElse(null);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private boolean indexExists(String table, String index) {
        return count("""
                SELECT COUNT(*) FROM information_schema.STATISTICS
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME = ?
                  AND INDEX_NAME = ?
                """, table, index) > 0;
    }

    private boolean constraintExists(String table, String name) {
        return count("""
                SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME = ?
                  AND CONSTRAINT_NAME = ?
                """, table, name) > 0;
    }

    private boolean foreignKeyExistsOnColumn(String table, String column) {
        return count("""
                SELECT COUNT(*) FROM information_schema.KEY_COLUMN_USAGE
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME = ?
                  AND COLUMN_NAME = ?
                  AND REFERENCED_TABLE_NAME IS NOT NULL
                """, table, column) > 0;
    }

    private boolean columnIsNullable(String table, String column) {
        try {
            String nullable = jdbc.query("""
                    SELECT IS_NULLABLE FROM information_schema.COLUMNS
                    WHERE TABLE_SCHEMA = DATABASE()
                      AND TABLE_NAME = ?
                      AND COLUMN_NAME = ?
                    LIMIT 1
                    """, (rs, row) -> rs.getString(1), table, column)
                    .stream().findFirst().orElse(null);
            return nullable != null && "YES".equalsIgnoreCase(nullable);
        } catch (RuntimeException e) {
            return false;
        }
    }
}
