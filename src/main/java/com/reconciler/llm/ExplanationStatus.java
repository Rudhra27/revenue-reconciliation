package com.reconciler.llm;

public enum ExplanationStatus {

	/** The model returned well-formed, complete output. */
	OK,

	/** The model replied, but the payload didn't match the schema (bad JSON, missing field). */
	INVALID,

	/** The call itself failed (network, timeout, API error) or the LLM isn't configured. */
	FAILED
}
