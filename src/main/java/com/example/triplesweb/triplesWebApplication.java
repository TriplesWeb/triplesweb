package com.example.triplesweb;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 트리플에스 웹 애플리케이션 Spring Boot 진입점.
 */
@SpringBootApplication
@EnableScheduling
public class triplesWebApplication {

	public static void main(String[] args) {
		SpringApplication.run(triplesWebApplication.class, args);
	}

}
