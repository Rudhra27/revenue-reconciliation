package com.reconciler.reconciliation;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * One thing that doesn't line up. Either order-anchored (orderId / orderRowId set) or
 * payment-anchored ({@link DiscrepancyType#ORDER_NOT_FOUND}, with only paymentRowIds).
 *
 * @param subtype      "OVER" or "UNDER" for AMOUNT_MISMATCH, otherwise null
 * @param amountImpact the money this discrepancy represents, in the direction's terms
 * @param detail       the numbers behind the finding (ordered, JSON-friendly)
 */
public record Discrepancy(
		DiscrepancyType type,
		String subtype,
		Severity severity,
		Direction direction,
		String orderId,
		UUID orderRowId,
		List<UUID> paymentRowIds,
		String currency,
		BigDecimal amountImpact,
		Map<String, Object> detail) {
}
