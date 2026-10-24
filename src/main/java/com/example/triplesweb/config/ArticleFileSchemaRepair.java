package com.example.triplesweb.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/**
 * article_file / shop_image 는 엔티티가 legacy camelCase 컬럼(fileType …)에 매핑된다.
 * snake_case 중복 컬럼(file_type …)이 NOT NULL 로 남아 있으면 INSERT가 다시 1364 난다.
 * 기동 시: camel만 남기거나, snake만 있으면 camel으로 이름을 맞춘다.
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
public class ArticleFileSchemaRepair implements ApplicationRunner {

    private final JdbcTemplate jdbc;
    private final DataSource dataSource;

    private static final String[] TABLES = {"article_file", "shop_image"};

    private static final String[][] COLS = {
            {"fileType", "file_type", "VARCHAR(255) NOT NULL"},
            {"originalName", "original_name", "VARCHAR(300) NOT NULL"},
            {"savedName", "saved_name", "VARCHAR(400) NOT NULL"},
            {"mediaType", "media_type", "VARCHAR(50) NOT NULL"},
    };

    @Override
    public void run(ApplicationArguments args) {
        for (String table : TABLES) {
            try {
                repairTable(table);
            } catch (Exception e) {
                log.error("Failed to repair embed columns on {}: {}", table, e.getMessage());
            }
        }
        log.info("article_file/shop_image embed columns aligned to camelCase (fileType, …)");
    }

    private void repairTable(String table) throws Exception {
        Set<String> cols = listColumns(table);
        if (cols.isEmpty()) {
            log.debug("Table {} missing or unreadable — skip", table);
            return;
        }

        for (String[] pair : COLS) {
            String camel = pair[0];
            String snake = pair[1];
            String def = pair[2];

            String actualCamel = findCamel(cols, camel);
            String actualSnake = findSnake(cols, snake);

            if (actualCamel == null && actualSnake != null) {
                jdbc.execute("ALTER TABLE `" + table + "` CHANGE COLUMN `" + actualSnake + "` `" + camel + "` " + def);
                log.info("Renamed {}.{} → {}", table, actualSnake, camel);
                cols = listColumns(table);
            } else if (actualCamel != null && actualSnake != null) {
                jdbc.execute(
                        "UPDATE `" + table + "` SET `" + actualCamel + "` = COALESCE(NULLIF(`"
                                + actualCamel + "`, ''), `" + actualSnake + "`) WHERE `" + actualSnake + "` IS NOT NULL");
                jdbc.execute("ALTER TABLE `" + table + "` DROP COLUMN `" + actualSnake + "`");
                log.info("Merged and dropped {}.{}", table, actualSnake);
                cols = listColumns(table);
            }
        }
    }

    private Set<String> listColumns(String table) throws Exception {
        Set<String> names = new LinkedHashSet<>();
        try (Connection conn = dataSource.getConnection()) {
            DatabaseMetaData meta = conn.getMetaData();
            String catalog = conn.getCatalog();
            for (String tableName : new String[]{table, table.toLowerCase(Locale.ROOT), table.toUpperCase(Locale.ROOT)}) {
                try (ResultSet rs = meta.getColumns(catalog, null, tableName, null)) {
                    while (rs.next()) {
                        names.add(rs.getString("COLUMN_NAME"));
                    }
                }
                if (!names.isEmpty()) break;
            }
        }
        return names;
    }

    private static String findCamel(Set<String> cols, String camel) {
        return cols.stream()
                .filter(c -> !c.contains("_") && c.equalsIgnoreCase(camel))
                .findFirst()
                .orElse(null);
    }

    private static String findSnake(Set<String> cols, String snake) {
        return cols.stream()
                .filter(c -> c.equalsIgnoreCase(snake))
                .findFirst()
                .orElse(null);
    }
}
