package com.example.triplesweb.config;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Flyway 이후 남은 스키마 정리.
 * Chemistry memberA/B → Artist FK, article_file.category 제거, unit_member UNIQUE.
 * 테이블/컬럼명은 CamelCaseToUnderscores 및 구 StandardImpl 둘 다 시도한다.
 */
@Slf4j
@Component
@Order(30)
public class SchemaImproveRunner implements ApplicationRunner {

    @PersistenceContext
    private EntityManager em;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        dropArticleFileCategoryIfPresent();
        migrateChemistryToArtistFk();
        ensureUnitMemberUnique();
    }

    private void dropArticleFileCategoryIfPresent() {
        if (!columnExists("article_file", "category")) {
            return;
        }
        try {
            em.createNativeQuery("ALTER TABLE article_file DROP COLUMN category").executeUpdate();
            log.info("Dropped article_file.category");
        } catch (RuntimeException e) {
            log.warn("Drop article_file.category failed: {}", e.getMessage());
        }
    }

    private void migrateChemistryToArtistFk() {
        String chemTable = firstExistingTable("chemistry", "Chemistry");
        String artistTable = firstExistingTable("artist", "Artist");
        if (chemTable == null || artistTable == null) {
            return;
        }

        String artistPk = columnExists(artistTable, "artist_id") ? "artist_id"
                : columnExists(artistTable, "artistId") ? "artistId" : null;
        if (artistPk == null) {
            log.warn("Artist PK column not found on {}", artistTable);
            return;
        }

        boolean hasMemberA = columnExists(chemTable, "member_a") || columnExists(chemTable, "memberA");
        String memberACol = columnExists(chemTable, "member_a") ? "member_a" : "memberA";
        String memberBCol = columnExists(chemTable, "member_b") ? "member_b" : "memberB";
        boolean hasArtistA = columnExists(chemTable, "artist_a_id");

        if (hasMemberA) {
            if (!hasArtistA) {
                em.createNativeQuery(
                        "ALTER TABLE " + chemTable
                                + " ADD COLUMN artist_a_id BIGINT NULL, ADD COLUMN artist_b_id BIGINT NULL"
                ).executeUpdate();
            }
            em.createNativeQuery(
                    "UPDATE " + chemTable + " c"
                            + " INNER JOIN " + artistTable + " a ON a.name = c." + memberACol
                            + " INNER JOIN " + artistTable + " b ON b.name = c." + memberBCol
                            + " SET c.artist_a_id = a." + artistPk + ", c.artist_b_id = b." + artistPk
                            + " WHERE c.artist_a_id IS NULL OR c.artist_b_id IS NULL"
            ).executeUpdate();
            em.createNativeQuery(
                    "DELETE FROM " + chemTable + " WHERE artist_a_id IS NULL OR artist_b_id IS NULL"
            ).executeUpdate();
            try {
                em.createNativeQuery("ALTER TABLE " + chemTable + " DROP INDEX uk_chemistry_pair").executeUpdate();
            } catch (RuntimeException ignored) {
            }
            em.createNativeQuery(
                    "ALTER TABLE " + chemTable + " DROP COLUMN " + memberACol + ", DROP COLUMN " + memberBCol
            ).executeUpdate();
            log.info("Migrated {}.{} → artist_a_id/artist_b_id", chemTable, memberACol);
            hasArtistA = true;
            hasMemberA = false;
        }

        if (hasArtistA && !hasMemberA) {
            try {
                em.createNativeQuery(
                        "ALTER TABLE " + chemTable
                                + " MODIFY COLUMN artist_a_id BIGINT NOT NULL, MODIFY COLUMN artist_b_id BIGINT NOT NULL"
                ).executeUpdate();
            } catch (RuntimeException e) {
                log.debug("Chemistry NOT NULL already applied: {}", e.getMessage());
            }
            if (!indexExists(chemTable, "uk_chemistry_pair")) {
                try {
                    em.createNativeQuery(
                            "ALTER TABLE " + chemTable
                                    + " ADD CONSTRAINT uk_chemistry_pair UNIQUE (artist_a_id, artist_b_id)"
                    ).executeUpdate();
                } catch (RuntimeException e) {
                    log.debug("Chemistry UK: {}", e.getMessage());
                }
            }
            ensureChemistryFk(chemTable, artistTable, artistPk, "fk_chemistry_artist_a", "artist_a_id");
            ensureChemistryFk(chemTable, artistTable, artistPk, "fk_chemistry_artist_b", "artist_b_id");
        }
    }

    private void ensureChemistryFk(
            String chemTable,
            String artistTable,
            String artistPk,
            String baseName,
            String column
    ) {
        // 이미 동일한 컬럼에 올바른 외래 키가 있다면 추가하지 않는다.
        if (foreignKeyExists(chemTable, column, artistTable, artistPk)) {
            log.info(
                    "Chemistry FK already exists: {}.{} -> {}.{}",
                    chemTable, column, artistTable, artistPk
            );
            return;
        }

        // MariaDB 스키마 전체에서 사용되지 않는 제약 조건 이름을 찾는다.
        String constraintName = baseName;
        int suffix = 2;

        while (constraintNameExists(constraintName)) {
            constraintName = baseName + "_" + suffix++;
        }

        // 이름 충돌을 피한 후 외래 키를 추가한다.
        em.createNativeQuery(
                "ALTER TABLE `" + chemTable + "` ADD CONSTRAINT `" + constraintName + "`"
                        + " FOREIGN KEY (`" + column + "`)"
                        + " REFERENCES `" + artistTable + "` (`" + artistPk + "`)"
        ).executeUpdate();

        log.info(
                "Added Chemistry FK: {} on {}.{} -> {}.{}",
                constraintName, chemTable, column, artistTable, artistPk
        );
    }

    private boolean constraintNameExists(String name) {
        Number count = (Number) em.createNativeQuery("""
            SELECT COUNT(*)
            FROM information_schema.TABLE_CONSTRAINTS
            WHERE CONSTRAINT_SCHEMA = DATABASE()
              AND CONSTRAINT_NAME = :name
            """)
                .setParameter("name", name)
                .getSingleResult();

        return count.longValue() > 0;
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


    private void ensureUnitMemberUnique() {
        String um = firstExistingTable("unit_member", "UnitMember");
        if (um == null || indexExists(um, "uk_unit_member")) {
            return;
        }
        String artistCol = columnExists(um, "artist_Id") ? "artist_Id"
                : columnExists(um, "artist_id") ? "artist_id" : null;
        String unitCol = columnExists(um, "unit_id") ? "unit_id" : null;
        if (artistCol == null || unitCol == null) {
            return;
        }
        try {
            em.createNativeQuery(
                    "ALTER TABLE " + um + " ADD CONSTRAINT uk_unit_member UNIQUE (" + artistCol + ", " + unitCol + ")"
            ).executeUpdate();
            log.info("Added uk_unit_member on {}", um);
        } catch (RuntimeException e) {
            log.warn("UnitMember UNIQUE skipped: {}", e.getMessage());
        }
    }

    private String firstExistingTable(String... names) {
        for (String n : names) {
            if (tableExists(n)) {
                return n;
            }
        }
        return null;
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
                WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = :t AND COLUMN_NAME = :c
                """).setParameter("t", table).setParameter("c", column).getSingleResult();
        return n.longValue() > 0;
    }

    private boolean indexExists(String table, String index) {
        Number n = (Number) em.createNativeQuery("""
                SELECT COUNT(*) FROM information_schema.STATISTICS
                WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = :t AND INDEX_NAME = :i
                """).setParameter("t", table).setParameter("i", index).getSingleResult();
        return n.longValue() > 0;
    }

    private boolean constraintExists(String table, String name) {
        Number n = (Number) em.createNativeQuery("""
                SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS
                WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = :t AND CONSTRAINT_NAME = :n
                """).setParameter("t", table).setParameter("n", name).getSingleResult();
        return n.longValue() > 0;
    }
}
