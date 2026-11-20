package com.example.triplesweb.config;

import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.cfg.DateTimeFeature;

/**
 * Boot가 제공하는 {@code JsonMapper}를 ISO-8601 날짜로 맞춘다.
 */
@Configuration
public class JacksonConfig {

    @Bean
    JsonMapperBuilderCustomizer iso8601Dates() {
        return builder -> builder.disable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS);
    }
}
