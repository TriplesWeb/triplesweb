package com.example.triplesweb.config;

import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FlywayV47ChecksumAlignTest {
    @Test
    void rewritesTheKnownChecksumWhenTheScopeTableMatches() throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:h2:mem:v47align;DB_CLOSE_DELAY=-1")) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("""
                        CREATE TABLE flyway_schema_history (
                            version VARCHAR(50),
                            checksum INT,
                            success BOOLEAN
                        )
                        """);
                statement.execute("INSERT INTO flyway_schema_history (version, checksum, success) VALUES ('47', -1130856591, TRUE)");
                statement.execute("INSERT INTO flyway_schema_history (version, checksum, success) VALUES ('46', 10, TRUE)");
                statement.execute("""
                        CREATE TABLE objekt_description_scope (
                            season VARCHAR(64) NOT NULL,
                            collection_no VARCHAR(32) NOT NULL,
                            PRIMARY KEY (season, collection_no)
                        )
                        """);
            }

            assertEquals(1, FlywayV47ChecksumAlign.align(connection));
            assertEquals(0, FlywayV47ChecksumAlign.align(connection));

            try (Statement statement = connection.createStatement();
                 ResultSet rows = statement.executeQuery("SELECT version, checksum FROM flyway_schema_history ORDER BY version")) {
                rows.next();
                assertEquals("46", rows.getString(1));
                assertEquals(10, rows.getInt(2));
                rows.next();
                assertEquals("47", rows.getString(1));
                assertEquals(-1747156949, rows.getInt(2));
            }
        }
    }

    @Test
    void leavesTheChecksumWhenTheScopeTableIsMissing() throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:h2:mem:v47missing;DB_CLOSE_DELAY=-1")) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("""
                        CREATE TABLE flyway_schema_history (
                            version VARCHAR(50),
                            checksum INT,
                            success BOOLEAN
                        )
                        """);
                statement.execute("INSERT INTO flyway_schema_history (version, checksum, success) VALUES ('47', -1130856591, TRUE)");
            }

            assertEquals(0, FlywayV47ChecksumAlign.align(connection));
            try (Statement statement = connection.createStatement();
                 ResultSet rows = statement.executeQuery("SELECT checksum FROM flyway_schema_history")) {
                rows.next();
                assertEquals(-1130856591, rows.getInt(1));
            }
        }
    }
}
