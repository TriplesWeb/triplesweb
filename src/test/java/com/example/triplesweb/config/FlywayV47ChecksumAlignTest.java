package com.example.triplesweb.config;

import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FlywayV47ChecksumAlignTest {
    @Test
    void restoresTheEventSectionChecksumOnly() throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:h2:mem:v47restore;DB_CLOSE_DELAY=-1")) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("""
                        CREATE TABLE flyway_schema_history (
                            version VARCHAR(50),
                            description VARCHAR(200),
                            checksum INT,
                            success BOOLEAN
                        )
                        """);
                statement.execute("""
                        INSERT INTO flyway_schema_history (version, description, checksum, success)
                        VALUES ('47', 'event and section', -1747156949, TRUE)
                        """);
                statement.execute("""
                        INSERT INTO flyway_schema_history (version, description, checksum, success)
                        VALUES ('46', 'drop non triples objekt catalog', 10, TRUE)
                        """);
            }

            assertEquals(1, FlywayV47ChecksumAlign.restoreEventSectionChecksum(connection));
            assertEquals(0, FlywayV47ChecksumAlign.restoreEventSectionChecksum(connection));

            try (Statement statement = connection.createStatement();
                 ResultSet rows = statement.executeQuery(
                         "SELECT version, checksum FROM flyway_schema_history ORDER BY version")) {
                rows.next();
                assertEquals("46", rows.getString(1));
                assertEquals(10, rows.getInt(2));
                rows.next();
                assertEquals("47", rows.getString(1));
                assertEquals(-1130856591, rows.getInt(2));
            }
        }
    }

    @Test
    void skipsWhenHistoryIsMissing() throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:h2:mem:v47empty;DB_CLOSE_DELAY=-1")) {
            assertEquals(0, FlywayV47ChecksumAlign.restoreEventSectionChecksum(connection));
        }
    }
}
