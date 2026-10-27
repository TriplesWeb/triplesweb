package com.example.triplesweb.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

class DotenvEnvironmentPostProcessorTest {
    @TempDir Path directory;

    private StandardEnvironment environment(String content, Map<String, Object> external) throws Exception {
        Path dotenv = directory.resolve(".env");
        Files.writeString(dotenv, content);
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().replace("systemEnvironment",
                new SystemEnvironmentPropertySource("systemEnvironment", external));
        environment.getPropertySources().addFirst(new MapPropertySource("testOptions",
                Map.of("app.dotenv.path", dotenv.toString())));
        return environment;
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class Probe {}

    @Test
    void springApplicationDiscoversLoaderBeforeConfigDataWithoutGradle() throws Exception {
        Path file = directory.resolve(".env");
        Files.writeString(file, """
                MYSQL_HOST=127.0.0.1
                MYSQL_PORT=13306
                MYSQL_DATABASE=ide_probe
                MYSQL_USER=developer
                MYSQL_PASSWORD='quoted # sample'
                SPRING_PROFILES_ACTIVE=local
                """);
        SpringApplication application = new SpringApplication(Probe.class);
        application.setWebApplicationType(WebApplicationType.NONE);
        application.setDefaultProperties(Map.of("app.dotenv.path", file.toString(),
                "spring.main.banner-mode", "off"));
        try (var context = application.run()) {
            assertThat(context.getEnvironment().getProperty("spring.datasource.url"))
                    .isEqualTo("jdbc:mariadb://127.0.0.1:13306/ide_probe?useSSL=false");
            assertThat(context.getEnvironment().getProperty("spring.datasource.password"))
                    .isEqualTo("quoted # sample");
            assertThat(context.getEnvironment().getActiveProfiles()).contains("local");
        }
    }

    @Test
    void nativeMainLoadsComposeDatabaseSettingsAndQuotedValues() throws Exception {
        var env = environment("""
                MYSQL_HOST=100.89.171.59
                MYSQL_PORT=3306
                MYSQL_DATABASE=triples
                MYSQL_USER='developer'
                MYSQL_PASSWORD="sample # password=1"
                COSMO_CREDENTIALS_FILE=C:\\project\\credentials.json
                """, Map.of());
        new DotenvEnvironmentPostProcessor().postProcessEnvironment(env, new SpringApplication());
        assertThat(env.getProperty("spring.datasource.url"))
                .isEqualTo("jdbc:mariadb://100.89.171.59:3306/triples?useSSL=false");
        assertThat(env.getProperty("spring.datasource.username")).isEqualTo("developer");
        assertThat(env.getProperty("spring.datasource.password")).isEqualTo("sample # password=1");
        assertThat(env.getProperty("cosmo.credentials.file")).isEqualTo("C:\\project\\credentials.json");
        assertThat(env.getProperty("spring.profiles.default")).isEqualTo("local");
    }

    @Test
    void explicitDatasourceAndExternalEnvironmentOverrideDotenv() throws Exception {
        var env = environment("""
                MYSQL_DATABASE=ignored
                MYSQL_PASSWORD=ignored
                SPRING_DATASOURCE_URL=jdbc:mariadb://dotenv/db
                SPRING_DATASOURCE_PASSWORD=dotenv
                SPRING_PROFILES_ACTIVE=local
                """, Map.of("SPRING_DATASOURCE_URL", "jdbc:mariadb://configured/db",
                "SPRING_DATASOURCE_PASSWORD", "external", "SPRING_PROFILES_ACTIVE", "production"));
        new DotenvEnvironmentPostProcessor().postProcessEnvironment(env, new SpringApplication());
        assertThat(env.getProperty("spring.datasource.url")).isEqualTo("jdbc:mariadb://configured/db");
        assertThat(env.getProperty("spring.datasource.password")).isEqualTo("external");
        assertThat(env.getProperty("spring.profiles.active")).isEqualTo("production");
    }

    @Test
    void missingDotenvDoesNotChangeServerSettingsOrProfiles() {
        var env = new StandardEnvironment();
        env.getPropertySources().addFirst(new MapPropertySource("testOptions",
                Map.of("app.dotenv.path", directory.resolve("missing").toString())));
        new DotenvEnvironmentPostProcessor().postProcessEnvironment(env, new SpringApplication());
        assertThat(env.getPropertySources().contains("projectDotenv")).isFalse();
        assertThat(env.getPropertySources().contains("projectDotenvDefaults")).isFalse();
    }

    @Test
    void parserPreservesPasswordsWindowsPathsAndHandlesBomExportAndComments() {
        var values = DotenvEnvironmentPostProcessor.parse(List.of(
                "\uFEFF# utf8", "export MYSQL_USER=developer # comment",
                "PASSWORD='pass # word=2'", "EMPTY=", "PATH=C:\\work\\key", "HASH=pass#word"));
        assertThat(values).containsEntry("MYSQL_USER", "developer")
                .containsEntry("PASSWORD", "pass # word=2").containsEntry("EMPTY", "")
                .containsEntry("PATH", "C:\\work\\key").containsEntry("HASH", "pass#word");
        assertThatThrownBy(() -> DotenvEnvironmentPostProcessor.parse(List.of("PASSWORD='not-closed")))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("1번째")
                .hasMessageNotContaining("not-closed");
    }
}
