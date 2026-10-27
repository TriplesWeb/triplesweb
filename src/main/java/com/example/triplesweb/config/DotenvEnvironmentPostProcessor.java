package com.example.triplesweb.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * IntelliJ 메인 클래스 실행과 bootRun에서 같은 .env를 읽는다.
 * 명령행·JVM 옵션·실제 환경변수는 .env보다 우선한다. 값은 로그에 남기지 않는다.
 */
public class DotenvEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {
    @Override
    public int getOrder() {
        // .env의 SPRING_PROFILES_ACTIVE도 ConfigData가 읽을 수 있어야 한다.
        return ConfigDataEnvironmentPostProcessor.ORDER - 1;
    }

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        if (!environment.getProperty("app.dotenv.enabled", Boolean.class, true)) return;
        Path file = Path.of(environment.getProperty("app.dotenv.path", ".env"));
        if (!Files.isRegularFile(file)) return;

        Map<String, Object> values;
        try {
            values = parse(Files.readAllLines(file, StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new IllegalStateException(".env 파일을 읽을 수 없습니다. 파일 권한을 확인하세요.", e);
        }
        var source = new SystemEnvironmentPropertySource("projectDotenv", values);
        var sources = environment.getPropertySources();
        if (sources.contains(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME)) {
            sources.addAfter(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME, source);
        } else {
            sources.addLast(source);
        }
        alias(environment, values, "spring.datasource.username", "SPRING_DATASOURCE_USERNAME", "MYSQL_USER");
        alias(environment, values, "spring.datasource.password", "SPRING_DATASOURCE_PASSWORD", "MYSQL_PASSWORD");
        String database = environment.getProperty("MYSQL_DATABASE");
        if (environment.getProperty("spring.datasource.url") == null && database != null && !database.isBlank()) {
            values.put("SPRING_DATASOURCE_URL", "jdbc:mariadb://"
                    + environment.getProperty("MYSQL_HOST", "localhost") + ":"
                    + environment.getProperty("MYSQL_PORT", "3306") + "/" + database + "?useSSL=false");
        }
        // .env가 있는 개발 PC에서만 local을 기본으로 쓴다. 명시한 프로파일은 우선한다.
        sources.addLast(new MapPropertySource("projectDotenvDefaults", Map.of("spring.profiles.default", "local")));
    }

    private static void alias(ConfigurableEnvironment environment, Map<String, Object> values,
                              String property, String target, String legacy) {
        String value = environment.getProperty(legacy);
        if (environment.getProperty(property) == null && value != null) values.put(target, value);
    }

    static Map<String, Object> parse(List<String> lines) {
        Map<String, Object> values = new LinkedHashMap<>();
        for (int index = 0; index < lines.size(); index++) {
            String line = lines.get(index);
            if (index == 0 && line.startsWith("\uFEFF")) line = line.substring(1);
            line = line.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            if (line.startsWith("export ")) line = line.substring(7).trim();
            int equals = line.indexOf('=');
            if (equals < 1) throw invalidLine(index);
            String key = line.substring(0, equals).trim();
            if (!key.matches("[A-Za-z_][A-Za-z0-9_]*")) throw invalidLine(index);
            String value = line.substring(equals + 1).trim();
            if (value.startsWith("\"") || value.startsWith("'")) {
                char quote = value.charAt(0);
                int end = 1;
                while (end < value.length()) {
                    if (value.charAt(end) == quote
                            && (quote == '\'' || value.charAt(end - 1) != '\\')) break;
                    end++;
                }
                if (end == value.length()) throw invalidLine(index);
                String suffix = value.substring(end + 1).trim();
                if (!suffix.isEmpty() && !suffix.startsWith("#")) throw invalidLine(index);
                value = value.substring(1, end);
            } else {
                for (int i = 0; i < value.length(); i++) {
                    if (value.charAt(i) == '#' && (i == 0 || Character.isWhitespace(value.charAt(i - 1)))) {
                        value = value.substring(0, i).stripTrailing();
                        break;
                    }
                }
            }
            values.put(key, value);
        }
        return values;
    }

    private static IllegalStateException invalidLine(int index) {
        return new IllegalStateException(".env " + (index + 1)
                + "번째 줄의 형식이 잘못됐습니다. KEY=value 형식과 따옴표를 확인하세요.");
    }
}
