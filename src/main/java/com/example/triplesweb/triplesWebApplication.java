package com.example.triplesweb;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;

/**
 * 트리플에스 웹 애플리케이션 Spring Boot 진입점.
 * 폼 로그인·HTTP Basic을 쓰지 않으므로 Spring 기본 인메모리 사용자(기동 로그에 비밀번호 출력)는 만들지 않는다.
 */
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
public class triplesWebApplication {

	public static void main(String[] args) {
		SpringApplication.run(triplesWebApplication.class, args);
	}

}
