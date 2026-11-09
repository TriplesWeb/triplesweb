package com.example.triplesweb.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

/**
 * V47은 저장소 밖 스크립트 {@code event and section}이다.
 * 그 체크섬을 설명 범위 스크립트 값으로 덮어쓴 기록만 원래 값으로 되돌린다.
 */
@Slf4j
@Configuration
@ConditionalOnProperty(name = "spring.flyway.enabled", havingValue = "true", matchIfMissing = true)
public class FlywayV47ChecksumAlign {
    static final int EVENT_SECTION_CHECKSUM = -1130856591;
    static final int SCOPE_SCRIPT_CHECKSUM = -1747156949;

    @Bean
    FlywayMigrationStrategy flywayV47ChecksumAlignStrategy(DataSource dataSource) {
        return flyway -> {
            try (Connection connection = dataSource.getConnection()) {
                if (restoreEventSectionChecksum(connection) > 0) {
                    log.info("V47 event and section 체크섬을 원래 값으로 되돌렸습니다.");
                }
            } catch (SQLException ex) {
                throw new IllegalStateException("V47 체크섬을 되돌리지 못했습니다.", ex);
            }
            // 실패 이력(예: 공유 DB에서 event_date 없는 상태)을 지운 뒤 재적용
            flyway.repair();
            flyway.migrate();
        };
    }

    static int restoreEventSectionChecksum(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                UPDATE flyway_schema_history
                   SET checksum = ?
                 WHERE version = '47'
                   AND description = 'event and section'
                   AND checksum = ?
                   AND success = TRUE
                """)) {
            statement.setInt(1, EVENT_SECTION_CHECKSUM);
            statement.setInt(2, SCOPE_SCRIPT_CHECKSUM);
            return statement.executeUpdate();
        } catch (SQLException ex) {
            if (isMissingHistory(ex)) return 0;
            throw ex;
        }
    }

    private static boolean isMissingHistory(SQLException ex) {
        String message = ex.getMessage();
        return message != null && message.toLowerCase().contains("flyway_schema_history");
    }
}
