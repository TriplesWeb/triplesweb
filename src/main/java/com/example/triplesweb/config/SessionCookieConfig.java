package com.example.triplesweb.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.session.autoconfigure.DefaultCookieSerializerCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring Session 쿠키 이름을 기존 {@code JSESSIONID}로 유지한다.
 * Boot 4에서는 {@code server.servlet.session.cookie.*}가 Session Serializer에 자동 반영되지 않는다.
 */
@Configuration
public class SessionCookieConfig {

    @Bean
    DefaultCookieSerializerCustomizer sessionCookieCustomizer(
            @Value("${server.servlet.session.cookie.name:JSESSIONID}") String cookieName,
            @Value("${server.servlet.session.cookie.same-site:lax}") String sameSite,
            @Value("${server.servlet.session.cookie.http-only:true}") boolean httpOnly,
            @Value("${server.servlet.session.cookie.secure:false}") boolean secure) {
        return serializer -> {
            serializer.setCookieName(cookieName);
            serializer.setSameSite(sameSite);
            serializer.setUseHttpOnlyCookie(httpOnly);
            serializer.setUseSecureCookie(secure);
        };
    }
}
