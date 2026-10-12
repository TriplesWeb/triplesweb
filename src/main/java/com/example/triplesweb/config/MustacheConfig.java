package com.example.triplesweb.config;

import com.samskivert.mustache.Mustache;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MustacheConfig {
    @Bean
    public com.samskivert.mustache.Mustache.Compiler mustacheCompiler(
            com.samskivert.mustache.Mustache.TemplateLoader loader) {
        return com.samskivert.mustache.Mustache.compiler()
                .withLoader(loader)
                .defaultValue("")   // 키가 없거나 null이면 빈 문자열
                .nullValue("");     // (jmustache 지원)
    }
}
