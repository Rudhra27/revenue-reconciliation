package com.reconciler.llm;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * app.llm.* — the OpenAI settings. temperature is 0.2: low enough that the same discrepancy
 * gives a stable explanation on a repeat (we cache, and a reviewer may re-open the same row),
 * but not 0, since the output is prose and a little variation reads more naturally.
 */
@ConfigurationProperties(prefix = "app.llm")
public record LlmProperties(
		boolean enabled,
		String apiKey,
		String baseUrl,
		String model,
		double temperature,
		int maxTokens,
		int timeoutSeconds) {

	public boolean configured() {
		return enabled && apiKey != null && !apiKey.isBlank();
	}
}
