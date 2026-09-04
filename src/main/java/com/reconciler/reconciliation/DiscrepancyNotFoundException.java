package com.reconciler.reconciliation;

import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

// 404, not 403: we don't confirm that a discrepancy belonging to someone else exists.
@ResponseStatus(HttpStatus.NOT_FOUND)
public class DiscrepancyNotFoundException extends RuntimeException {

	public DiscrepancyNotFoundException(UUID id) {
		super("Discrepancy not found: " + id);
	}
}
