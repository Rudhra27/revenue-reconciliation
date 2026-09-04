package com.reconciler.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class DatabaseUrlTest {

	@Test
	void translatesAPostgresUrlIntoJdbcPlusCredentials() {
		var props = DatabaseUrl.toJdbcProperties(
				"postgres://alice:s3cret@db.example.com:5432/reconciler?sslmode=require");

		assertThat(props)
				.containsEntry("spring.datasource.url", "jdbc:postgresql://db.example.com:5432/reconciler?sslmode=require")
				.containsEntry("spring.datasource.username", "alice")
				.containsEntry("spring.datasource.password", "s3cret");
	}

	@Test
	void keepsEveryQueryParameterNeonSends() {
		var props = DatabaseUrl.toJdbcProperties(
				"postgresql://u:p@ep-x.aws.neon.tech/neondb?sslmode=require&channel_binding=require");

		assertThat(props).containsEntry("spring.datasource.url",
				"jdbc:postgresql://ep-x.aws.neon.tech:5432/neondb?sslmode=require&channel_binding=require");
	}

	@Test
	void defaultsThePortWhenItIsMissing() {
		var props = DatabaseUrl.toJdbcProperties("postgresql://u:p@host/db");

		assertThat(props).containsEntry("spring.datasource.url", "jdbc:postgresql://host:5432/db");
	}

	@Test
	void doesNothingForAnAbsentOrAlreadyJdbcUrl() {
		assertThat(DatabaseUrl.toJdbcProperties(null)).isEmpty();
		assertThat(DatabaseUrl.toJdbcProperties("jdbc:postgresql://host:5432/db")).isEmpty();
	}

	@Test
	void toleratesQuotesAndLeadingJunkAroundTheUrl() {
		// a BOM, surrounding quotes, and a psql-style wrapper are all things a pasted value can carry
		var props = DatabaseUrl.toJdbcProperties("﻿ psql 'postgresql://u:p@host/db?sslmode=require'");

		assertThat(props).containsEntry("spring.datasource.url",
				"jdbc:postgresql://host:5432/db?sslmode=require");
	}
}
