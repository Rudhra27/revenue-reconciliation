package com.reconciler.ingest;

import java.util.List;

/**
 * Outcome of parsing one file: the rows that came through cleanly, the ones that were
 * rejected, and how many data rows we looked at in total.
 */
public record ParseResult<T>(List<T> rows, List<RowError> errors, int dataRowCount) {
}
