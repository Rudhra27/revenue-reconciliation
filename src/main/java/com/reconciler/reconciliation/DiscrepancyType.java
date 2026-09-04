package com.reconciler.reconciliation;

/**
 * The kinds of order/payment disagreement the engine recognises. Ordered roughly by how
 * fundamental the problem is; {@link ReconciliationEngine} evaluates them with a fixed
 * priority so one order produces exactly one primary discrepancy.
 */
public enum DiscrepancyType {

	/** A settled payment whose order reference matches no order. */
	ORDER_NOT_FOUND(Severity.HIGH, "Payment with no order"),

	/** A completed order with no charge at all. */
	MISSING_PAYMENT(Severity.HIGH, "Missing payment"),

	/** A completed order whose only charge failed. */
	FAILED_PAYMENT(Severity.HIGH, "Failed payment"),

	/** Two or more settled charges for one order. */
	DUPLICATE_PAYMENT(Severity.HIGH, "Duplicate charge"),

	/** A cancelled order that was charged anyway. */
	CHARGE_ON_CANCELLED(Severity.HIGH, "Charge on cancelled order"),

	/** An order marked refunded where the charge still exceeds the refund. */
	INCOMPLETE_REFUND(Severity.MEDIUM, "Incomplete refund"),

	/** A completed order that nets to zero because of a refund the order system never recorded. */
	UNRECORDED_REFUND(Severity.MEDIUM, "Unrecorded refund"),

	/** Order and payment are in different currencies. */
	CURRENCY_MISMATCH(Severity.MEDIUM, "Currency mismatch"),

	/** What was charged differs from the order total by more than the tolerance. */
	AMOUNT_MISMATCH(Severity.MEDIUM, "Amount mismatch"),

	/** A charge that has not settled yet. Drops to LOW while it's still recent (a "watch" item). */
	PENDING_SETTLEMENT(Severity.MEDIUM, "Pending settlement");

	private final Severity baseSeverity;
	private final String label;

	DiscrepancyType(Severity baseSeverity, String label) {
		this.baseSeverity = baseSeverity;
		this.label = label;
	}

	public Severity baseSeverity() {
		return baseSeverity;
	}

	public String label() {
		return label;
	}
}
