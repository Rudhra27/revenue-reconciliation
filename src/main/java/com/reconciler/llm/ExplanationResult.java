package com.reconciler.llm;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Set;

/** The structured output we ask the model for. */
public record ExplanationResult(
		String summary,
		@JsonProperty("likely_cause") String likelyCause,
		@JsonProperty("recommended_action") String recommendedAction,
		String confidence) {

	private static final Set<String> CONFIDENCE_VALUES = Set.of("low", "medium", "high");

	/** All four fields present and non-blank, confidence one of the allowed values. */
	public boolean valid() {
		return notBlank(summary)
				&& notBlank(likelyCause)
				&& notBlank(recommendedAction)
				&& confidence != null && CONFIDENCE_VALUES.contains(confidence.toLowerCase());
	}

	private static boolean notBlank(String value) {
		return value != null && !value.isBlank();
	}
}
