package com.reconciler.ingest;

/** The whole file is unusable (wrong or missing header columns, not a CSV, empty). */
public class InvalidCsvException extends RuntimeException {

	public InvalidCsvException(String message) {
		super(message);
	}
}
