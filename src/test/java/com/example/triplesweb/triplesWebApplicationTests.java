package com.example.triplesweb;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Spring 애플리케이션 컨텍스트 로드 smoke 테스트.
 * {@code test} 프로파일은 H2 인메모리 DB를 쓰므로 CI에서도 외부 MariaDB 없이 통과한다.
 */
@SpringBootTest
@ActiveProfiles("test")
class triplesWebApplicationTests {

	@Test
	void contextLoads() {
	}

}
