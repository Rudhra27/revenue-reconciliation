package com.reconciler.llm;

/** The call to the model failed — network, timeout, API error, or an empty/refused reply. */
class LlmCallException extends RuntimeException {

	LlmCallException(String message) {
		super(message);
	}
}
