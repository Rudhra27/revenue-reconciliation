package com.reconciler.ingest;

/** A single source row that could not be imported, with the file line number for the user. */
public record RowError(int line, String message) {
}
