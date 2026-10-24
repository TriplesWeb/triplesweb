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
        ensureSavedMetaFileType("article_file");
        ensureSavedMetaFileType("shop_image");
    }

    /**
     * SavedMeta.fileType 임베드 컬럼({@code file_type})이 없으면 추가한다.
     * Flyway V25와 동일 목적 — 배포 전 스키마 누락으로 article 상세가 500 나는 경우 방어.
     */
    private void ensureSavedMetaFileType(String table) {
        if (!tableExists(table) || columnExists(table, "file_type")) {
            return;
        }
        try {
            em.createNativeQuery(
                    "ALTER TABLE `" + table + "`"
                            + " ADD COLUMN file_type VARCHAR(255) NOT NULL"
                            + " DEFAULT 'application/octet-stream'"
            ).executeUpdate();
            if (columnExists(table, "media_type")) {
                em.createNativeQuery(
                        "UPDATE `" + table + "` SET file_type = CASE"
                                + " WHEN UPPER(COALESCE(media_type,'')) IN ('IMAGE','PHOTO') THEN 'image/jpeg'"
                                + " WHEN UPPER(COALESCE(media_type,'')) = 'VIDEO' THEN 'video/mp4'"
                                + " ELSE 'application/octet-stream' END"
                ).executeUpdate();
            }
            log.info("Added {}.file_type for SavedMeta embed", table);
        } catch (RuntimeException e) {
            log.warn("Ensure {}.file_type failed: {}", table, e.getMessage());
        }
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
