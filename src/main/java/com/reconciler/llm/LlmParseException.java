package com.reconciler.llm;

/** The model replied but the content wasn't the JSON we asked for. Carries the raw text for storage. */
class LlmParseException extends RuntimeException {

	private final String rawResponse;

	LlmParseException(String rawResponse, String message) {
		super(message);
		this.rawResponse = rawResponse;
	}

	String rawResponse() {
		return rawResponse;
	}
}
