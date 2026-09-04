package com.reconciler.reconciliation;

import static com.reconciler.reconciliation.ReconciliationFixtures.charge;
import static com.reconciler.reconciliation.ReconciliationFixtures.order;
import static com.reconciler.reconciliation.ReconciliationFixtures.pendingCharge;
import static com.reconciler.reconciliation.ReconciliationFixtures.refund;
import static org.assertj.core.api.Assertions.assertThat;

import com.reconciler.dataset.OrderRow;
import com.reconciler.dataset.PaymentRow;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class ReconciliationEngineTest {

	private static final Instant AS_OF = Instant.parse("2025-05-20T00:00:00Z");

	private final ReconciliationEngine engine = new ReconciliationEngine();

	@Test
	void aCompletedOrderWithOneMatchingSettledChargeReconciles() {
		ReconciliationResult result = run(
				List.of(order("ORD-1", "completed", "100.00")),
				List.of(charge("ORD-1", "settled", "100.00")));

		assertThat(result.discrepancies()).isEmpty();
		assertThat(result.summary().matchedOrders()).isEqualTo(1);
		assertThat(result.summary().valueReconciled()).isEqualByComparingTo("100.00");
	}

	@Test
	void aCentOfRoundingIsWithinTolerance() {
		ReconciliationResult result = run(
				List.of(order("ORD-1", "completed", "135.38")),
				List.of(charge("ORD-1", "settled", "135.39")));

		assertThat(result.discrepancies()).isEmpty();
	}

	@Test
	void aMaterialOverchargeIsFlagged() {
		Discrepancy d = only(run(
				List.of(order("ORD-1", "completed", "92.81")),
				List.of(charge("ORD-1", "settled", "117.81"))));

		assertThat(d.type()).isEqualTo(DiscrepancyType.AMOUNT_MISMATCH);
		assertThat(d.subtype()).isEqualTo("OVER");
		assertThat(d.direction()).isEqualTo(Direction.OWED_BY_US);
		assertThat(d.amountImpact()).isEqualByComparingTo("25.00");
	}

	@Test
	void aMaterialUnderchargeIsFlagged() {
		Discrepancy d = only(run(
				List.of(order("ORD-1", "completed", "127.62")),
				List.of(charge("ORD-1", "settled", "109.12"))));

		assertThat(d.type()).isEqualTo(DiscrepancyType.AMOUNT_MISMATCH);
		assertThat(d.subtype()).isEqualTo("UNDER");
		assertThat(d.direction()).isEqualTo(Direction.OWED_TO_US);
		assertThat(d.amountImpact()).isEqualByComparingTo("18.50");
	}

	@Test
	void aCompletedOrderWithNoChargeIsAMissingPayment() {
		Discrepancy d = only(run(List.of(order("ORD-1", "completed", "50.00")), List.of()));

		assertThat(d.type()).isEqualTo(DiscrepancyType.MISSING_PAYMENT);
		assertThat(d.direction()).isEqualTo(Direction.OWED_TO_US);
		assertThat(d.amountImpact()).isEqualByComparingTo("50.00");
	}

	@Test
	void anOrderWhoseOnlyChargeFailedIsAFailedPayment() {
		Discrepancy d = only(run(
				List.of(order("ORD-1", "completed", "310.00")),
				List.of(charge("ORD-1", "failed", "310.00"))));

		assertThat(d.type()).isEqualTo(DiscrepancyType.FAILED_PAYMENT);
		assertThat(d.direction()).isEqualTo(Direction.OWED_TO_US);
	}

	@Test
	void twoSettledChargesAreADuplicate() {
		Discrepancy d = only(run(
				List.of(order("ORD-1", "completed", "119.84")),
				List.of(charge("ORD-1", "settled", "119.84"), charge("ORD-1", "settled", "119.84"))));

		assertThat(d.type()).isEqualTo(DiscrepancyType.DUPLICATE_PAYMENT);
		assertThat(d.direction()).isEqualTo(Direction.OWED_BY_US);
		assertThat(d.amountImpact()).isEqualByComparingTo("119.84");
	}

	@Test
	void aCancelledOrderThatWasChargedIsFlagged() {
		Discrepancy d = only(run(
				List.of(order("ORD-1", "cancelled", "175.00")),
				List.of(charge("ORD-1", "settled", "175.00"))));

		assertThat(d.type()).isEqualTo(DiscrepancyType.CHARGE_ON_CANCELLED);
		assertThat(d.amountImpact()).isEqualByComparingTo("175.00");
	}

	@Test
	void aCancelledOrderWithNoChargeReconciles() {
		ReconciliationResult result = run(List.of(order("ORD-1", "cancelled", "175.00")), List.of());

		assertThat(result.discrepancies()).isEmpty();
	}

	@Test
	void aRefundedOrderStillHoldingMoneyIsAnIncompleteRefund() {
		Discrepancy d = only(run(
				List.of(order("ORD-1", "refunded", "240.00")),
				List.of(charge("ORD-1", "settled", "240.00"), refund("ORD-1", "settled", "120.00"))));

		assertThat(d.type()).isEqualTo(DiscrepancyType.INCOMPLETE_REFUND);
		assertThat(d.direction()).isEqualTo(Direction.OWED_BY_US);
		assertThat(d.amountImpact()).isEqualByComparingTo("120.00");
	}

	@Test
	void aFullyRefundedOrderReconciles() {
		ReconciliationResult result = run(
				List.of(order("ORD-1", "refunded", "99.00")),
				List.of(charge("ORD-1", "settled", "99.00"), refund("ORD-1", "settled", "99.00")));

		assertThat(result.discrepancies()).isEmpty();
	}

	@Test
	void aCompletedOrderThatNetsToZeroIsAnUnrecordedRefund() {
		Discrepancy d = only(run(
				List.of(order("ORD-1", "completed", "99.00")),
				List.of(charge("ORD-1", "settled", "99.00"), refund("ORD-1", "settled", "99.00"))));

		assertThat(d.type()).isEqualTo(DiscrepancyType.UNRECORDED_REFUND);
		assertThat(d.direction()).isEqualTo(Direction.ALREADY_LOST);
		assertThat(d.amountImpact()).isEqualByComparingTo("99.00");
	}

	@Test
	void aCurrencyMismatchIsFlaggedEvenWhenTheNumbersMatch() {
		Discrepancy d = only(run(
				List.of(order("ORD-1", "completed", "EUR", "210.00")),
				List.of(charge("ORD-1", "settled", "USD", "210.00"))));

		assertThat(d.type()).isEqualTo(DiscrepancyType.CURRENCY_MISMATCH);
		assertThat(d.direction()).isEqualTo(Direction.UNQUANTIFIED);
		assertThat(d.amountImpact()).isEqualByComparingTo("0.00");
	}

	@Test
	void aRecentPendingChargeIsAWatchItem() {
		Discrepancy d = only(engine.run(
				List.of(order("ORD-1", "completed", "67.00")),
				List.of(pendingCharge("ORD-1", "67.00", Instant.parse("2025-05-18T00:00:00Z"))),
				AS_OF));

		assertThat(d.type()).isEqualTo(DiscrepancyType.PENDING_SETTLEMENT);
		assertThat(d.direction()).isEqualTo(Direction.WATCH);
		assertThat(d.severity()).isEqualTo(Severity.LOW);
	}

	@Test
	void aPendingChargeThatHasSatForMonthsStaysAWatchItemButEscalates() {
		Discrepancy d = only(engine.run(
				List.of(order("ORD-1", "completed", "67.00")),
				List.of(pendingCharge("ORD-1", "67.00", Instant.parse("2025-01-01T00:00:00Z"))),
				AS_OF));

		assertThat(d.type()).isEqualTo(DiscrepancyType.PENDING_SETTLEMENT);
		assertThat(d.direction()).isEqualTo(Direction.WATCH);
		assertThat(d.severity()).isEqualTo(Severity.HIGH);
	}

	@Test
	void aPendingChargeNeverCountsAsMoneyAtRisk() {
		ReconciliationResult result = engine.run(
				List.of(order("ORD-1", "completed", "67.00")),
				List.of(pendingCharge("ORD-1", "67.00", Instant.parse("2024-01-01T00:00:00Z"))),
				AS_OF);

		assertThat(result.summary().moneyAtRisk()).isEqualByComparingTo("0.00");
	}

	@Test
	void aPaymentForAnUnknownOrderIsAnOrphan() {
		Discrepancy d = only(run(
				List.of(order("ORD-1", "completed", "50.00")),
				List.of(charge("ORD-1", "settled", "50.00"), charge("ORD-999", "settled", "80.00"))));

		assertThat(d.type()).isEqualTo(DiscrepancyType.ORDER_NOT_FOUND);
		assertThat(d.direction()).isEqualTo(Direction.INVESTIGATION);
		assertThat(d.orderId()).isNull();
		assertThat(d.amountImpact()).isEqualByComparingTo("80.00");
	}

	@Test
	void aByteIdenticalDuplicateOrderRowIsIgnored() {
		OrderRow keep = order("ORD-1", "completed", "30.00");
		OrderRow copy = order("ORD-1", "completed", "30.00");
		copy.markDuplicateOf(keep.getId());

		ReconciliationResult result = run(List.of(keep, copy), List.of(charge("ORD-1", "settled", "30.00")));

		assertThat(result.summary().totalOrders()).isEqualTo(1);
		assertThat(result.discrepancies()).isEmpty();
	}

	@Test
	void moneyAtRiskIsUncollectedRevenuePlusLiability() {
		ReconciliationResult result = run(
				List.of(order("ORD-1", "completed", "100.00"), order("ORD-2", "completed", "50.00")),
				List.of(charge("ORD-2", "settled", "60.00")));

		assertThat(result.summary().moneyAtRisk()).isEqualByComparingTo("110.00");
		assertThat(result.summary().amountByDirection())
				.containsEntry(Direction.OWED_TO_US, new java.math.BigDecimal("100.00"))
				.containsEntry(Direction.OWED_BY_US, new java.math.BigDecimal("10.00"));
	}

	@Test
	void theSameInputAlwaysProducesTheSameResult() {
		List<OrderRow> orders = List.of(order("ORD-2", "completed", "50.00"), order("ORD-1", "completed", "100.00"));
		List<PaymentRow> payments = List.of(charge("ORD-1", "settled", "130.00"));

		ReconciliationResult first = engine.run(orders, payments, AS_OF);
		ReconciliationResult second = engine.run(orders, payments, AS_OF);

		assertThat(first).isEqualTo(second);
	}

	private ReconciliationResult run(List<OrderRow> orders, List<PaymentRow> payments) {
		return engine.run(orders, payments, AS_OF);
	}

	private static Discrepancy only(ReconciliationResult result) {
		assertThat(result.discrepancies()).hasSize(1);
		return result.discrepancies().get(0);
	}
}
