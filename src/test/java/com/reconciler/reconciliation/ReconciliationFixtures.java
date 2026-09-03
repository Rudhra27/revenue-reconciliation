package com.reconciler.reconciliation;

import com.reconciler.dataset.OrderRow;
import com.reconciler.dataset.PaymentRow;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Builders for hand-made rows so the engine tests read as small scenarios. */
final class ReconciliationFixtures {

	static final Instant DEFAULT_PROCESSED_AT = Instant.parse("2025-05-10T00:00:00Z");

	private ReconciliationFixtures() {
	}

	static OrderRow order(String orderId, String status, String netAmount) {
		return order(orderId, status, "USD", netAmount);
	}

	static OrderRow order(String orderId, String status, String currency, String netAmount) {
		return OrderRow.builder()
				.id(UUID.randomUUID())
				.datasetId(UUID.randomUUID())
				.userId(UUID.randomUUID())
				.sourceLineNo(1)
				.rawJson(Map.of())
				.orderId(orderId)
				.orderDate(Instant.parse("2025-05-01T00:00:00Z"))
				.currency(currency)
				.grossAmount(new BigDecimal(netAmount))
				.discount(BigDecimal.ZERO)
				.netAmount(new BigDecimal(netAmount))
				.status(status)
				.dataQualityFlags(List.of())
				.build();
	}

	static PaymentRow charge(String orderReference, String status, String amount) {
		return payment(orderReference, "charge", status, "USD", amount, DEFAULT_PROCESSED_AT);
	}

	static PaymentRow charge(String orderReference, String status, String currency, String amount) {
		return payment(orderReference, "charge", status, currency, amount, DEFAULT_PROCESSED_AT);
	}

	static PaymentRow pendingCharge(String orderReference, String amount, Instant processedAt) {
		return payment(orderReference, "charge", "pending", "USD", amount, processedAt);
	}

	static PaymentRow refund(String orderReference, String status, String amount) {
		return payment(orderReference, "refund", status, "USD", amount, DEFAULT_PROCESSED_AT);
	}

	static PaymentRow payment(String orderReference, String type, String status, String currency, String amount,
			Instant processedAt) {
		BigDecimal value = new BigDecimal(amount);
		return PaymentRow.builder()
				.id(UUID.randomUUID())
				.datasetId(UUID.randomUUID())
				.userId(UUID.randomUUID())
				.sourceLineNo(1)
				.rawJson(Map.of())
				.transactionRef("TXN-" + orderReference + "-" + type + "-" + status)
				.processedAt(processedAt)
				.orderReference(orderReference)
				.currency(currency)
				.amount(value)
				.fee(BigDecimal.ZERO)
				.netSettled(value)
				.type(type)
				.status(status)
				.dataQualityFlags(List.of())
				.build();
	}
}
