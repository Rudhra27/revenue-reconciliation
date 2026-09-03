package com.reconciler.reconciliation;

import java.math.BigDecimal;
import java.util.Map;

/**
 * The headline figures.
 *
 * @param valueReconciled sum of net_amount for orders that matched cleanly
 * @param valueInDispute  notional value of every record touched by a discrepancy (excluding watch items) — how broad the problem is
 * @param moneyAtRisk     OWED_TO_US + OWED_BY_US — the actionable exposure
 * @param amountByDirection total impact per {@link Direction}
 * @param byType          count and impact per {@link DiscrepancyType} (only types that occurred)
 */
public record ReconciliationSummary(
		int totalOrders,
		int totalPayments,
		int matchedOrders,
		int discrepancyCount,
		BigDecimal valueReconciled,
		BigDecimal valueInDispute,
		BigDecimal moneyAtRisk,
		Map<Direction, BigDecimal> amountByDirection,
		Map<DiscrepancyType, TypeBreakdown> byType) {

	public record TypeBreakdown(int count, BigDecimal amountImpact) {
	}
}
