package com.reconciler.ingest;

/** Payments were uploaded before the orders file. We load orders first so the match key set is known. */
public class OrdersRequiredException extends RuntimeException {

	public OrdersRequiredException() {
		super("Upload the orders file before the payments file.");
	}
}
