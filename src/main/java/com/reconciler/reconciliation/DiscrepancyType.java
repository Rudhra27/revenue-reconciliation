package com.reconciler.reconciliation;

/**
 * The kinds of order/payment disagreement the engine recognises. Ordered roughly by how
 * fundamental the problem is; {@link ReconciliationEngine} evaluates them with a fixed
 * priority so one order produces exactly one primary discrepancy.
 */
public enum DiscrepancyType {

	/** A settled payment whose order reference matches no order. */
	ORDER_NOT_FOUND(Severity.HIGH),

	/** A completed order with no charge at all. */
	MISSING_PAYMENT(Severity.HIGH),

	/** A completed order whose only charge failed. */
	FAILED_PAYMENT(Severity.HIGH),

	/** Two or more settled charges for one order. */
	DUPLICATE_PAYMENT(Severity.HIGH),

	/** A cancelled order that was charged anyway. */
	CHARGE_ON_CANCELLED(Severity.HIGH),

	/** An order marked refunded where the charge still exceeds the refund. */
	INCOMPLETE_REFUND(Severity.MEDIUM),

	/** A completed order that nets to zero because of a refund the order system never recorded. */
	UNRECORDED_REFUND(Severity.MEDIUM),

	/** Order and payment are in different currencies. */
	CURRENCY_MISMATCH(Severity.MEDIUM),

	/** What was charged differs from the order total by more than the tolerance. */
	AMOUNT_MISMATCH(Severity.MEDIUM),

	/** A charge that has not settled yet. Drops to LOW while it's still recent (a "watch" item). */
	PENDING_SETTLEMENT(Severity.MEDIUM);

	private final Severity baseSeverity;

	DiscrepancyType(Severity baseSeverity) {
		this.baseSeverity = baseSeverity;
	}

	public Severity baseSeverity() {
		return baseSeverity;
	}
}
