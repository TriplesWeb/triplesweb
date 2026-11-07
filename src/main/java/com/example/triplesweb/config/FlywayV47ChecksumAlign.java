package com.example.triplesweb.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Locale;
import java.util.Set;

/**
 * V47이 적용된 뒤 파일 체크섬만 달라진 데이터베이스를 현재 스크립트에 맞춘다.
 * 테이블 컬럼이 다를 때는 건드리지 않는다.
 */
@Slf4j
@Configuration
@ConditionalOnProperty(name = "spring.flyway.enabled", havingValue = "true", matchIfMissing = true)
public class FlywayV47ChecksumAlign {
    /** 이미 데이터베이스에 기록된 V47 체크섬. */
    static final int APPLIED_CHECKSUM = -1130856591;
    /** 현재 {@code V47__objekt_description_scope.sql} 체크섬. */
    static final int LOCAL_CHECKSUM = -1747156949;

    @Bean
    FlywayMigrationStrategy flywayV47ChecksumAlignStrategy(DataSource dataSource) {
        return flyway -> {
            try (Connection connection = dataSource.getConnection()) {
                if (align(connection) > 0) log.info("V47 Flyway 체크섬을 현재 마이그레이션에 맞췄습니다.");
            } catch (SQLException ex) {
                throw new IllegalStateException("V47 Flyway 체크섬을 맞추지 못했습니다.", ex);
            }
            flyway.migrate();
        };
    }

    static int align(Connection connection) throws SQLException {
        if (!hasColumns(connection, "flyway_schema_history", "checksum", "version")
                || !hasColumns(connection, "objekt_description_scope", "season", "collection_no")) return 0;
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE flyway_schema_history SET checksum = ? WHERE version = '47' AND checksum = ? AND success = TRUE")) {
            statement.setInt(1, LOCAL_CHECKSUM);
            statement.setInt(2, APPLIED_CHECKSUM);
            return statement.executeUpdate();
        }
    }

    static boolean hasColumns(Connection connection, String table, String... columns) throws SQLException {
        DatabaseMetaData meta = connection.getMetaData();
        Set<String> wanted = Set.of(columns);
        for (String name : new String[]{table, table.toUpperCase(Locale.ROOT)}) {
            try (ResultSet rows = meta.getColumns(connection.getCatalog(), null, name, null)) {
                java.util.HashSet<String> found = new java.util.HashSet<>();
                while (rows.next()) found.add(rows.getString("COLUMN_NAME").toLowerCase(Locale.ROOT));
                if (found.containsAll(wanted)) return true;
            }
        }
        return false;
    }
}
