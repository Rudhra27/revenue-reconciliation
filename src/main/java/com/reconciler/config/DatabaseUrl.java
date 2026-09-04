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
		String databaseUrl = System.getenv("DATABASE_URL");
		Map<String, String> properties = toJdbcProperties(databaseUrl);
		properties.forEach(System::setProperty);
		// Deploy-time breadcrumb: without this it's impossible to tell from a crash log whether the
		// platform handed us DATABASE_URL at all, or whether the translation produced a JDBC URL.
		System.out.printf("[DatabaseUrl] DATABASE_URL %s -> datasource url %s%n",
				databaseUrl == null ? "absent" : "present (" + databaseUrl.length() + " chars)",
				properties.getOrDefault("spring.datasource.url", "<unchanged>"));
	}

	static Map<String, String> toJdbcProperties(String databaseUrl) {
		if (databaseUrl == null) {
			return Map.of();
		}
		// Anchor on the scheme wherever it actually starts: a pasted value can carry a stray BOM or
		// non-breaking space that trim() leaves behind, surrounding quotes, or a `psql '...'` wrapper.
		String cleaned = databaseUrl.strip();
		if (cleaned.startsWith("jdbc:")) {
			return Map.of();
		}
		int scheme = cleaned.indexOf("postgresql://");
		if (scheme < 0) {
			scheme = cleaned.indexOf("postgres://");
		}
		if (scheme < 0) {
			return Map.of();
		}
		cleaned = cleaned.substring(scheme);
		while (cleaned.endsWith("\"") || cleaned.endsWith("'")) {
			cleaned = cleaned.substring(0, cleaned.length() - 1);
		}

		URI uri = URI.create(cleaned);
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
