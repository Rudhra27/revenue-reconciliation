package com.reconciler.dataset;

/**
 * Non-fatal problems noticed while importing a row. The row is still stored and still
 * takes part in reconciliation; the flag just surfaces on the drill-down so a user can
 * see why a figure might look off.
 */
public enum DataQualityFlag {

	// order rows
	MISSING_EMAIL,
	MISSING_DISCOUNT,
	MISSING_CURRENCY,
	UNKNOWN_STATUS,
	UNPARSEABLE_DATE,
	NET_AMOUNT_MISMATCH,
	DUPLICATE_ORDER_ROW,

	// payment rows
	MISSING_PROCESSED_AT,
	MISSING_ORDER_REFERENCE,
	ORDER_REFERENCE_ADJUSTED,
	UNKNOWN_PAYMENT_TYPE,
	UNKNOWN_PAYMENT_STATUS,
	NET_SETTLED_MISMATCH
}
