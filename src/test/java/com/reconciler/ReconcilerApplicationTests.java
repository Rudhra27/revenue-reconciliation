package com.reconciler;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class ReconcilerApplicationTests {

	@Test
	void contextLoads() {
		// Boots the full context against a real Postgres container, so this also
		// proves the Liquibase changelog applies cleanly.
	}

}
