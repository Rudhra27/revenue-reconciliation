package com.reconciler.config;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

/**
 * Most hosts (Render, Neon, Railway, Fly) hand the database over as a single
 * {@code postgres://user:pass@host:port/db} URL. Spring's datasource wants a JDBC URL and
 * separate credentials, so translate it here before anything else reads the environment.
 * Does nothing when DATABASE_URL is absent or already a jdbc: URL.
 */
public class DatabaseUrlEnvironmentPostProcessor implements EnvironmentPostProcessor {

	@Override
	public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
		String databaseUrl = environment.getProperty("DATABASE_URL");
		if (databaseUrl == null || !(databaseUrl.startsWith("postgres://") || databaseUrl.startsWith("postgresql://"))) {
			return;
		}

		URI uri = URI.create(databaseUrl);
		int port = uri.getPort() == -1 ? 5432 : uri.getPort();
		StringBuilder jdbcUrl = new StringBuilder("jdbc:postgresql://")
				.append(uri.getHost()).append(':').append(port).append(uri.getPath());
		if (uri.getQuery() != null) {
			jdbcUrl.append('?').append(uri.getQuery());
		}

		Map<String, Object> properties = new LinkedHashMap<>();
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

		environment.getPropertySources().addFirst(new MapPropertySource("databaseUrl", properties));
	}

	private static String decode(String value) {
		return URLDecoder.decode(value, StandardCharsets.UTF_8);
	}
}
