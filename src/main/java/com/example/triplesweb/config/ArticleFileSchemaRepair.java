package com.example.triplesweb.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * MariaDB 1364 가드: article_file에 camelCase(fileType 등)와 snake_case(file_type 등)가
 * 동시에 남아 있으면 Hibernate INSERT가 file_type만 채워 fileType NOT NULL에서 터진다.
 * Flyway V27과 동일 작업을 기동 시에도 한 번 시도한다(이미 없으면 IF EXISTS로 no-op).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ArticleFileSchemaRepair {

    private final JdbcTemplate jdbc;

    private static final String[] TABLES = {"article_file", "shop_image"};
    private static final String[] CAMEL_COLS = {"fileType", "originalName", "savedName", "mediaType"};

    @PostConstruct
    public void dropLegacyCamelCaseDuplicates() {
        for (String table : TABLES) {
            for (String col : CAMEL_COLS) {
                try {
                    jdbc.execute("ALTER TABLE `" + table + "` DROP COLUMN IF EXISTS `" + col + "`");
                } catch (Exception e) {
                    log.warn("Could not drop {}.{}: {}", table, col, e.getMessage());
                }
            }
        }
        log.info("Checked article_file/shop_image for legacy camelCase embed columns");
    }
}
