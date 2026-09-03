package com.reconciler.reconciliation;

/**
 * Which way the money points. OWED_TO_US and OWED_BY_US together make up "money at risk";
 * the rest are reported separately because they are not a recoverable or payable exposure.
 */
public enum Direction {

	/** Revenue we should have collected and did not (missing / failed charge, undercharge). */
	OWED_TO_US,

	/** Money we hold that we may have to give back (duplicate charge, overcharge, unfinished refund). */
	OWED_BY_US,

	/** A settled payment with no order behind it. Needs a human to explain, not a reserve. */
	INVESTIGATION,

	/** The cash already left; only the books are wrong now (a refund the order system never recorded). */
	ALREADY_LOST,

	/** Most likely just timing (a recent pending settlement). */
	WATCH,

	/** Real exposure, but not measurable without extra data (a currency mismatch with no FX rate). */
	UNQUANTIFIED
}
