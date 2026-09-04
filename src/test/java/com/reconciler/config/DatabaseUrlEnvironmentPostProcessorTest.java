package com.reconciler.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.core.io.support.SpringFactoriesLoader;
import org.springframework.mock.env.MockEnvironment;

class DatabaseUrlEnvironmentPostProcessorTest {

	private final DatabaseUrlEnvironmentPostProcessor processor = new DatabaseUrlEnvironmentPostProcessor();

	@Test
	@SuppressWarnings("deprecation")
	void isRegisteredSoSpringActuallyRunsIt() {
		var names = SpringFactoriesLoader.loadFactoryNames(EnvironmentPostProcessor.class, getClass().getClassLoader());

		assertThat(names).contains(DatabaseUrlEnvironmentPostProcessor.class.getName());
	}

	@Test
	void translatesAPostgresUrlIntoJdbcPlusCredentials() {
		MockEnvironment environment = new MockEnvironment()
				.withProperty("DATABASE_URL", "postgres://alice:s3cret@db.example.com:5432/reconciler?sslmode=require");

		processor.postProcessEnvironment(environment, null);

		assertThat(environment.getProperty("spring.datasource.url"))
				.isEqualTo("jdbc:postgresql://db.example.com:5432/reconciler?sslmode=require");
		assertThat(environment.getProperty("spring.datasource.username")).isEqualTo("alice");
		assertThat(environment.getProperty("spring.datasource.password")).isEqualTo("s3cret");
	}

	@Test
	void defaultsThePortWhenItIsMissing() {
		MockEnvironment environment = new MockEnvironment()
				.withProperty("DATABASE_URL", "postgresql://u:p@host/db");

		processor.postProcessEnvironment(environment, null);

		assertThat(environment.getProperty("spring.datasource.url")).isEqualTo("jdbc:postgresql://host:5432/db");
	}

	@Test
	void leavesThingsAloneWhenDatabaseUrlIsAbsentOrAlreadyJdbc() {
		MockEnvironment absent = new MockEnvironment();
		processor.postProcessEnvironment(absent, null);
		assertThat(absent.getProperty("spring.datasource.url")).isNull();

		MockEnvironment jdbc = new MockEnvironment()
				.withProperty("DATABASE_URL", "jdbc:postgresql://host:5432/db");
		processor.postProcessEnvironment(jdbc, null);
		assertThat(jdbc.getProperty("spring.datasource.url")).isNull();
	}
}
