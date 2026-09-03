package com.reconciler.ingest;

import java.util.List;

/** Shown back to the user after an upload. */
public record UploadSummary(
		String fileKind,
		int rowsRead,
		int rowsStored,
		int rowsFlagged,
		int duplicatesFlagged,
		List<RowError> errors) {

	public int rowsRejected() {
		return errors.size();
	}

	public boolean clean() {
		return errors.isEmpty() && rowsFlagged == 0;
	}
}
