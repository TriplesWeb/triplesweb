package com.example.triplesweb.config;

import com.samskivert.mustache.Mustache;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Mustache 컴파일러 빈: 누락·null 템플릿 변수는 빈 문자열로 렌더링한다.
 */
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
