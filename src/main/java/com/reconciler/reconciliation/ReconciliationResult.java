package com.reconciler.reconciliation;

import java.util.List;

/** What the engine returns: the headline figures and every individual discrepancy. */
public record ReconciliationResult(ReconciliationSummary summary, List<Discrepancy> discrepancies) {
}
