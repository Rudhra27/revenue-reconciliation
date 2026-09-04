package com.reconciler.reconciliation;

/** Reconcile was requested before both files were loaded. */
public class NotReadyToReconcileException extends RuntimeException {

	public NotReadyToReconcileException() {
		super("Load both the orders and payments files before reconciling.");
	}
}
