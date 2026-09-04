package com.reconciler.config;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Most hosts (Render, Neon, Railway, Fly) hand the database over as a single
 * {@code postgres://user:pass@host:port/db} URL in DATABASE_URL. Spring's datasource wants a
 * JDBC URL and separate credentials. This is applied from {@code main()} as system properties
 * (rather than an EnvironmentPostProcessor) so it can't miss, whatever the packaging does.
 */
public final class DatabaseUrl {

	private DatabaseUrl() {
	}

	/** Read DATABASE_URL from the environment and, if it's a postgres URL, set the spring.datasource.* system properties. */
	public static void applyToSystemProperties() {
		toJdbcProperties(System.getenv("DATABASE_URL")).forEach(System::setProperty);
	}

	static Map<String, String> toJdbcProperties(String databaseUrl) {
		if (databaseUrl == null) {
			return Map.of();
		}
		String trimmed = databaseUrl.trim();
		if (!(trimmed.startsWith("postgres://") || trimmed.startsWith("postgresql://"))) {
			return Map.of();
		}

		URI uri = URI.create(trimmed);
		int port = uri.getPort() == -1 ? 5432 : uri.getPort();
		StringBuilder jdbcUrl = new StringBuilder("jdbc:postgresql://")
				.append(uri.getHost()).append(':').append(port).append(uri.getPath());
		if (uri.getQuery() != null) {
			jdbcUrl.append('?').append(uri.getQuery());
		}

		Map<String, String> properties = new LinkedHashMap<>();
		properties.put("spring.datasource.url", jdbcUrl.toString());
		String userInfo = uri.getUserInfo();
		if (userInfo != null) {
			int separator = userInfo.indexOf(':');
			String user = separator >= 0 ? userInfo.substring(0, separator) : userInfo;
			properties.put("spring.datasource.username", decode(user));
			if (separator >= 0) {
				properties.put("spring.datasource.password", decode(userInfo.substring(separator + 1)));
			}
		}
		return properties;
	}

	private static String decode(String value) {
		return URLDecoder.decode(value, StandardCharsets.UTF_8);
	}
}
