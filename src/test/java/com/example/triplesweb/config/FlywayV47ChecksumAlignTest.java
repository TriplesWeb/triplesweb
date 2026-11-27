package com.example.triplesweb.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

class FlywayV47ChecksumAlignTest {
    @TempDir Path migrationDirectory;

    @Test
    void defaultConfigurationDoesNotMigrateOrRegisterTheRepairStrategy() throws Exception {
        flywayContext(Map.of()).run(context -> {
            assertThat(context).hasNotFailed().doesNotHaveBean(FlywayMigrationStrategy.class);
            assertThat(context.getEnvironment().getProperty("spring.jpa.hibernate.ddl-auto")).isEqualTo("none");
            var jdbc = new JdbcTemplate(context.getBean(DataSource.class));
            assertThat(jdbc.queryForList("SELECT table_name FROM information_schema.tables WHERE table_schema = 'public'",
                    String.class)).isEmpty();
        });
    }

    @Test
    void deploymentEnvironmentEnablesMigrationAndRepairStrategy() throws Exception {
        flywayContext(Map.of("FLYWAY_ENABLED", "true")).run(context -> {
            assertThat(context).hasNotFailed().hasSingleBean(FlywayMigrationStrategy.class);
            var jdbc = new JdbcTemplate(context.getBean(DataSource.class));
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM migration_probe", Long.class)).isZero();
            assertThat(jdbc.queryForObject(
                    "SELECT COUNT(*) FROM flyway_schema_history WHERE version = '1' AND success = TRUE",
                    Long.class)).isEqualTo(1);
        });
    }

    private ApplicationContextRunner flywayContext(Map<String, Object> environment) throws Exception {
        Files.writeString(migrationDirectory.resolve("V1__probe.sql"), "CREATE TABLE migration_probe (id INT);");
        return new ApplicationContextRunner()
                .withInitializer(context -> {
                    String source = StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME;
                    context.getEnvironment().getPropertySources().replace(source,
                            new SystemEnvironmentPropertySource(source, environment));
                    new ConfigDataApplicationContextInitializer().initialize(context);
                })
                .withConfiguration(AutoConfigurations.of(DataSourceAutoConfiguration.class, FlywayAutoConfiguration.class))
                .withUserConfiguration(FlywayV47ChecksumAlign.class)
                .withPropertyValues("spring.datasource.url=jdbc:h2:mem:" + UUID.randomUUID() + ";DATABASE_TO_LOWER=TRUE",
                        "spring.datasource.driver-class-name=org.h2.Driver",
                        "spring.datasource.username=sa", "spring.datasource.password=",
                        "spring.flyway.locations=filesystem:" + migrationDirectory);
    }

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
