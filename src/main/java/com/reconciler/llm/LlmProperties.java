package com.reconciler.llm;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * app.llm.* — the model settings. Works with OpenAI or any OpenAI-compatible endpoint
 * (Groq's free Llama, OpenRouter, Together, a local Ollama), so {@code baseUrl}, {@code model}
 * and {@code responseFormat} are all configurable.
 *
 * <p>temperature is 0.2: low enough that the same discrepancy gives a stable explanation on a
 * repeat (we cache, and a reviewer may re-open the same row), but not 0, since the output is
 * prose and a little variation reads more naturally.
 *
 * @param responseFormat  {@code json_schema} (strict, OpenAI only), {@code json_object}
 *                        (forces valid JSON, widely supported), or {@code none}
 * @param reasoningEffort {@code low} / {@code medium} / {@code high} for reasoning models
 *                        (o-series, gpt-oss). Blank = don't send it, which is right for
 *                        models like gpt-4o-mini that reject the parameter.
 */
@ConfigurationProperties(prefix = "app.llm")
public record LlmProperties(
		boolean enabled,
		String apiKey,
		String baseUrl,
		String model,
		double temperature,
		int maxTokens,
		int timeoutSeconds,
		String responseFormat,
		String reasoningEffort) {

	public boolean configured() {
		return enabled && apiKey != null && !apiKey.isBlank();
	}
}
