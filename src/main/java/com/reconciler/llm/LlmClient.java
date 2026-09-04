package com.reconciler.llm;

import com.reconciler.llm.PromptBuilder.ChatMessage;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/** The one place that talks to OpenAI. Chat Completions with a strict JSON-schema response. */
@Component
class LlmClient {

	// Asking for structured output means a well-formed reply is the norm; LlmService still
	// handles the case where it isn't (truncation, refusal, an outage mid-stream).
	private static final Map<String, Object> RESPONSE_FORMAT = Map.of(
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
		body.put("max_completion_tokens", properties.maxTokens());
		body.put("response_format", RESPONSE_FORMAT);
		body.put("messages", messages.stream()
				.map(m -> Map.of("role", m.role(), "content", m.content()))
				.toList());
		return body;
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
					throw new LlmCallException("OpenAI is returning errors right now.");
				}
			}
			throw new LlmCallException("OpenAI rejected the request (" + http.getStatusCode() + ").");
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
