package com.example.triplesweb.config;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Flyway 이후 남은 스키마 정리.
 * Chemistry memberA/B → Artist FK, unit_member UNIQUE.
 * {@link com.example.triplesweb.config.DataInitializer} 이후,
 * {@link com.example.triplesweb.service.ChemistrySeedService} 이전에 실행되어야 한다.
 * 테이블/컬럼명은 CamelCaseToUnderscores snake_case만 사용한다.
 */
@Slf4j
@Component
@Order(2)
@Profile("!test")
public class SchemaImproveRunner implements ApplicationRunner {

    @PersistenceContext
    private EntityManager em;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        migrateChemistryToArtistFk();
        ensureUnitMemberUnique();
        ensureSavedMetaColumns("article_file");
        ensureSavedMetaColumns("shop_image");
        dropLegacyCamelColumns("article_file");
        dropLegacyCamelColumns("shop_image");
        relaxUnmappedRequiredColumns("article_file");
        relaxUnmappedRequiredColumns("shop_image");
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
            try {
                em.createNativeQuery(
                        "ALTER TABLE `" + table + "` DROP COLUMN `" + column + "`"
                ).executeUpdate();
                log.info("Dropped legacy column {}.{}", table, column);
            } catch (RuntimeException e) {
                log.warn("Drop {}.{} failed: {}", table, column, e.getMessage());
            }
        }
    }

    /** JPA가 쓰지 않는 NOT NULL 컬럼은 값 없이 INSERT 되도록 NULL 허용으로 바꾼다. */
    private void relaxUnmappedRequiredColumns(String table) {
        if (!tableExists(table)) {
            return;
        }
        @SuppressWarnings("unchecked")
        java.util.List<Object[]> rows = em.createNativeQuery("""
                SELECT COLUMN_NAME, COLUMN_TYPE
                FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME = :t
                  AND IS_NULLABLE = 'NO'
                  AND COLUMN_DEFAULT IS NULL
                  AND EXTRA NOT LIKE '%auto_increment%'
                """).setParameter("t", table).getResultList();
        java.util.Set<String> mapped = java.util.Set.of(
                "id", "article_id", "original_name", "saved_name", "url", "size", "file_type", "media_type", "title");
        for (Object[] row : rows) {
            String column = String.valueOf(row[0]);
            String columnType = String.valueOf(row[1]);
            if (mapped.contains(column)) {
                continue;
            }
            try {
                em.createNativeQuery(
                        "ALTER TABLE `" + table + "` MODIFY COLUMN `" + column + "` " + columnType + " NULL"
                ).executeUpdate();
                log.info("Made unmapped column {}.{} nullable", table, column);
            } catch (RuntimeException e) {
                log.warn("Relax {}.{} failed: {}", table, column, e.getMessage());
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
        em.createNativeQuery(
                "ALTER TABLE `" + table + "` ADD COLUMN `" + column + "` " + definition
        ).executeUpdate();
        log.info("Added {}.{}", table, column);
    }

    private void copyColumnIfPresent(String table, String fromColumn, String toColumn) {
        if (!columnExists(table, fromColumn) || !columnExists(table, toColumn)) {
            return;
        }
        em.createNativeQuery(
                "UPDATE `" + table + "` SET `" + toColumn + "` = `" + fromColumn + "`"
                        + " WHERE `" + fromColumn + "` IS NOT NULL AND `" + fromColumn + "` <> ''"
                        + " AND (`" + toColumn + "` IS NULL OR `" + toColumn + "` = ''"
                        + " OR `" + toColumn + "` = 'application/octet-stream')"
        ).executeUpdate();
    }

    private void copyMediaType(String table) {
        if (!columnExists(table, "mediaType") || !columnExists(table, "media_type")) {
            return;
        }
        em.createNativeQuery(
                "UPDATE `" + table + "` SET media_type = CASE"
                        + " WHEN UPPER(mediaType) IN ('IMAGE','PHOTO') THEN 'IMAGE'"
                        + " WHEN UPPER(mediaType) = 'VIDEO' THEN 'VIDEO'"
                        + " ELSE COALESCE(NULLIF(media_type, ''), 'OTHER') END"
                        + " WHERE mediaType IS NOT NULL AND mediaType <> ''"
        ).executeUpdate();
    }

    private void backfillMediaType(String table) {
        if (!columnExists(table, "media_type") || !columnExists(table, "file_type")
                || !columnExists(table, "saved_name")) {
            return;
        }
        em.createNativeQuery(
                "UPDATE `" + table + "` SET media_type = CASE"
                        + " WHEN file_type LIKE 'image/%'"
                        + " OR LOWER(saved_name) REGEXP '\\\\.(jpg|jpeg|png|gif|webp|bmp)$' THEN 'IMAGE'"
                        + " WHEN file_type LIKE 'video/%'"
                        + " OR LOWER(saved_name) REGEXP '\\\\.(mp4|mov|m4v|webm|mkv|avi)$' THEN 'VIDEO'"
                        + " ELSE media_type END"
                        + " WHERE media_type IS NULL OR media_type = '' OR media_type = 'OTHER'"
        ).executeUpdate();
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
                em.createNativeQuery(
                        "ALTER TABLE chemistry"
                                + " ADD COLUMN artist_a_id BIGINT NULL, ADD COLUMN artist_b_id BIGINT NULL"
                ).executeUpdate();
            }
            em.createNativeQuery(
                    "UPDATE chemistry c"
                            + " INNER JOIN artist a ON a.name = c." + memberACol
                            + " INNER JOIN artist b ON b.name = c." + memberBCol
                            + " SET c.artist_a_id = a.artist_id, c.artist_b_id = b.artist_id"
                            + " WHERE c.artist_a_id IS NULL OR c.artist_b_id IS NULL"
            ).executeUpdate();
            em.createNativeQuery(
                    "DELETE FROM chemistry WHERE artist_a_id IS NULL OR artist_b_id IS NULL"
            ).executeUpdate();
            if (indexExists("chemistry", "uk_chemistry_pair")) {
                em.createNativeQuery("ALTER TABLE chemistry DROP INDEX uk_chemistry_pair").executeUpdate();
            }
            em.createNativeQuery(
                    "ALTER TABLE chemistry DROP COLUMN " + memberACol + ", DROP COLUMN " + memberBCol
            ).executeUpdate();
            log.info("Migrated chemistry.{} → artist_a_id/artist_b_id", memberACol);
            hasArtistA = true;
            hasMemberA = false;
        }

        if (hasArtistA && !hasMemberA) {
            if (columnIsNullable("chemistry", "artist_a_id") || columnIsNullable("chemistry", "artist_b_id")) {
                em.createNativeQuery(
                        "ALTER TABLE chemistry"
                                + " MODIFY COLUMN artist_a_id BIGINT NOT NULL, MODIFY COLUMN artist_b_id BIGINT NOT NULL"
                ).executeUpdate();
            }
            if (!indexExists("chemistry", "uk_chemistry_pair")) {
                em.createNativeQuery(
                        "ALTER TABLE chemistry"
                                + " ADD CONSTRAINT uk_chemistry_pair UNIQUE (artist_a_id, artist_b_id)"
                ).executeUpdate();
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

        em.createNativeQuery(
                "ALTER TABLE `" + chemTable + "` ADD CONSTRAINT `" + constraintName + "`"
                        + " FOREIGN KEY (`" + column + "`)"
                        + " REFERENCES `" + artistTable + "` (`" + artistPk + "`)"
        ).executeUpdate();
        log.info("Added Chemistry FK: {} on {}.{} -> {}.{}", constraintName, chemTable, column, artistTable, artistPk);
    }

    private boolean foreignKeyExists(
            String table,
            String column,
            String referencedTable,
            String referencedColumn
    ) {
        Number count = (Number) em.createNativeQuery("""
                SELECT COUNT(*)
                FROM information_schema.KEY_COLUMN_USAGE
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME = :tableName
                  AND COLUMN_NAME = :columnName
                  AND REFERENCED_TABLE_SCHEMA = DATABASE()
                  AND REFERENCED_TABLE_NAME = :referencedTable
                  AND REFERENCED_COLUMN_NAME = :referencedColumn
                """)
                .setParameter("tableName", table)
                .setParameter("columnName", column)
                .setParameter("referencedTable", referencedTable)
                .setParameter("referencedColumn", referencedColumn)
                .getSingleResult();
        return count.longValue() > 0;
    }

    private boolean constraintNameExists(String name) {
        Number n = (Number) em.createNativeQuery("""
                SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS
                WHERE CONSTRAINT_SCHEMA = DATABASE() AND CONSTRAINT_NAME = :n
                """).setParameter("n", name).getSingleResult();
        return n.longValue() > 0;
    }

    private void ensureUnitMemberUnique() {
        if (!tableExists("unit_member") || indexExists("unit_member", "uk_unit_member")) {
            return;
        }
        if (!columnExists("unit_member", "artist_id") || !columnExists("unit_member", "unit_id")) {
            return;
        }
        em.createNativeQuery(
                "ALTER TABLE unit_member ADD CONSTRAINT uk_unit_member UNIQUE (artist_id, unit_id)"
        ).executeUpdate();
        log.info("Added uk_unit_member on unit_member");
    }

    private boolean tableExists(String table) {
        Number n = (Number) em.createNativeQuery("""
                SELECT COUNT(*) FROM information_schema.TABLES
                WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = :t
                """).setParameter("t", table).getSingleResult();
        return n.longValue() > 0;
    }

    private boolean columnExists(String table, String column) {
        Number n = (Number) em.createNativeQuery("""
                SELECT COUNT(*) FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME = :t
                  AND COLUMN_NAME = :c
                """).setParameter("t", table).setParameter("c", column).getSingleResult();
        return n.longValue() > 0;
    }

    /** information_schema에 실제 저장된 컬럼명(대소문자 포함)을 반환한다. */
    private String resolveColumnName(String table, String column) {
        try {
            Object name = em.createNativeQuery("""
                    SELECT COLUMN_NAME FROM information_schema.COLUMNS
                    WHERE TABLE_SCHEMA = DATABASE()
                      AND TABLE_NAME = :t
                      AND LOWER(COLUMN_NAME) = LOWER(:c)
                    LIMIT 1
                    """).setParameter("t", table).setParameter("c", column).getSingleResult();
            return name != null ? name.toString() : null;
        } catch (RuntimeException e) {
            return null;
        }
    }

    private boolean indexExists(String table, String index) {
        Number n = (Number) em.createNativeQuery("""
                SELECT COUNT(*) FROM information_schema.STATISTICS
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME = :t
                  AND INDEX_NAME = :i
                """).setParameter("t", table).setParameter("i", index).getSingleResult();
        return n.longValue() > 0;
    }

    private boolean constraintExists(String table, String name) {
        Number n = (Number) em.createNativeQuery("""
                SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME = :t
                  AND CONSTRAINT_NAME = :n
                """).setParameter("t", table).setParameter("n", name).getSingleResult();
        return n.longValue() > 0;
    }

    private boolean foreignKeyExistsOnColumn(String table, String column) {
        Number n = (Number) em.createNativeQuery("""
                SELECT COUNT(*) FROM information_schema.KEY_COLUMN_USAGE
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME = :t
                  AND COLUMN_NAME = :c
                  AND REFERENCED_TABLE_NAME IS NOT NULL
                """).setParameter("t", table).setParameter("c", column).getSingleResult();
        return n.longValue() > 0;
    }

    private boolean columnIsNullable(String table, String column) {
        try {
            Object nullable = em.createNativeQuery("""
                    SELECT IS_NULLABLE FROM information_schema.COLUMNS
                    WHERE TABLE_SCHEMA = DATABASE()
                      AND TABLE_NAME = :t
                      AND COLUMN_NAME = :c
                    LIMIT 1
                    """).setParameter("t", table).setParameter("c", column).getSingleResult();
            return nullable != null && "YES".equalsIgnoreCase(nullable.toString());
        } catch (RuntimeException e) {
            return false;
        }
    }
}
