package com.reconciler.ingest;

/** Thrown by a parser when a single row can't be imported. Collected into ParseResult.errors. */
class RowRejectedException extends RuntimeException {

	RowRejectedException(String message) {
		super(message);
	}
}
