package com.reconciler.llm;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ExplanationResultTest {

	@Test
	void acceptsACompleteReply() {
		ExplanationResult result = new ExplanationResult("s", "cause", "action", "high");

		assertThat(result.valid()).isTrue();
	}

	@Test
	void rejectsAMissingField() {
		assertThat(new ExplanationResult("s", null, "action", "high").valid()).isFalse();
		assertThat(new ExplanationResult("s", "cause", "  ", "high").valid()).isFalse();
	}

	@Test
	void rejectsAConfidenceOutsideTheAllowedValues() {
		assertThat(new ExplanationResult("s", "cause", "action", "very sure").valid()).isFalse();
	}

	@Test
	void acceptsConfidenceRegardlessOfCase() {
		assertThat(new ExplanationResult("s", "cause", "action", "HIGH").valid()).isTrue();
	}
}
