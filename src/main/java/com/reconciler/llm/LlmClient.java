package com.reconciler.llm;

import com.reconciler.llm.PromptBuilder.ChatMessage;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * The one place that talks to the model. Chat Completions over the OpenAI wire format, which
 * OpenAI-compatible hosts (Groq, OpenRouter, Together, Ollama) also speak.
 */
@Component
class LlmClient {

	// Strict schema — OpenAI honours it, so a well-formed reply is guaranteed. Other hosts
	// mostly don't, which is why the default is the widely-supported json_object mode and the
	// prompt spells out the shape. Either way LlmService validates and falls back gracefully.
	private static final Map<String, Object> JSON_SCHEMA_FORMAT = Map.of(
			"type", "json_schema",
			"json_schema", Map.of(
					"name", "discrepancy_explanation",
					"strict", true,
					"schema", Map.of(
							"type", "object",
							"additionalProperties", false,
							"required", List.of("summary", "likely_cause", "recommended_action", "confidence"),
							"properties", Map.of(
									"summary", Map.of("type", "string"),
									"likely_cause", Map.of("type", "string"),
									"recommended_action", Map.of("type", "string"),
									"confidence", Map.of("type", "string", "enum", List.of("low", "medium", "high"))))));

	private final LlmProperties properties;
	private final RestClient http;

	LlmClient(LlmProperties properties) {
		this.properties = properties;
		SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
		factory.setConnectTimeout(Duration.ofSeconds(10));
		factory.setReadTimeout(Duration.ofSeconds(Math.max(properties.timeoutSeconds(), 1)));
		this.http = RestClient.builder()
				.baseUrl(hasText(properties.baseUrl()) ? properties.baseUrl() : "https://api.openai.com/v1")
				.requestFactory(factory)
				.defaultHeaders(headers -> {
					if (hasText(properties.apiKey())) {
						headers.setBearerAuth(properties.apiKey());
					}
				})
				.build();
	}

	String complete(List<ChatMessage> messages) {
		ChatResponse response = withOneRetry(() -> http.post()
				.uri("/chat/completions")
				.contentType(MediaType.APPLICATION_JSON)
				.body(requestBody(messages))
				.retrieve()
				.body(ChatResponse.class));

		if (response == null || response.choices() == null || response.choices().isEmpty()) {
			throw new LlmCallException("The model returned an empty response.");
		}
		ChatResponse.Message message = response.choices().get(0).message();
		if (message.refusal() != null && !message.refusal().isBlank()) {
			throw new LlmCallException("The model declined to answer.");
		}
		if (message.content() == null || message.content().isBlank()) {
			throw new LlmCallException("The model returned no content.");
		}
		return message.content();
	}

	private Map<String, Object> requestBody(List<ChatMessage> messages) {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("model", properties.model());
		body.put("temperature", properties.temperature());
		body.put("max_tokens", properties.maxTokens());
		responseFormat().ifPresent(format -> body.put("response_format", format));
		body.put("messages", messages.stream()
				.map(m -> Map.of("role", m.role(), "content", m.content()))
				.toList());
		return body;
	}

	private Optional<Object> responseFormat() {
		String configured = properties.responseFormat() == null ? "json_object"
				: properties.responseFormat().trim().toLowerCase();
		return switch (configured) {
			case "json_schema" -> Optional.of(JSON_SCHEMA_FORMAT);
			case "none", "" -> Optional.empty();
			default -> Optional.of(Map.of("type", "json_object"));
		};
	}

	// Retry once on a timeout or a 5xx; surface 4xx and anything else straight away.
	private static <T> T withOneRetry(Supplier<T> call) {
		try {
			return call.get();
		} catch (ResourceAccessException firstTimeout) {
			try {
				Thread.sleep(500);
				return call.get();
			} catch (InterruptedException interrupted) {
				Thread.currentThread().interrupt();
				throw new LlmCallException("The model call was interrupted.");
			} catch (RestClientException retryFailure) {
				throw new LlmCallException("The model call timed out or was unreachable.");
			}
		} catch (RestClientResponseException http) {
			if (http.getStatusCode().is5xxServerError()) {
				try {
					Thread.sleep(500);
					return call.get();
				} catch (InterruptedException interrupted) {
					Thread.currentThread().interrupt();
					throw new LlmCallException("The model call was interrupted.");
				} catch (RestClientException retryFailure) {
					throw new LlmCallException("The model provider is returning errors right now.");
				}
			}
			throw new LlmCallException("The model provider rejected the request (" + http.getStatusCode() + ").");
		} catch (RestClientException other) {
			throw new LlmCallException("The model call failed: " + other.getMessage());
		}
	}

	private static boolean hasText(String value) {
		return value != null && !value.isBlank();
	}

	record ChatResponse(List<Choice> choices) {

		record Choice(Message message) {
		}

		record Message(String content, String refusal) {
		}
	}
}
