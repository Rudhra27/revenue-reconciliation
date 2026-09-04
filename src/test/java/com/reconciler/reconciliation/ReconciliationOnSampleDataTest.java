package com.reconciler.reconciliation;

import static com.reconciler.reconciliation.DiscrepancyType.AMOUNT_MISMATCH;
import static com.reconciler.reconciliation.DiscrepancyType.CHARGE_ON_CANCELLED;
import static com.reconciler.reconciliation.DiscrepancyType.CURRENCY_MISMATCH;
import static com.reconciler.reconciliation.DiscrepancyType.DUPLICATE_PAYMENT;
import static com.reconciler.reconciliation.DiscrepancyType.FAILED_PAYMENT;
import static com.reconciler.reconciliation.DiscrepancyType.INCOMPLETE_REFUND;
import static com.reconciler.reconciliation.DiscrepancyType.MISSING_PAYMENT;
import static com.reconciler.reconciliation.DiscrepancyType.ORDER_NOT_FOUND;
import static com.reconciler.reconciliation.DiscrepancyType.PENDING_SETTLEMENT;
import static com.reconciler.reconciliation.DiscrepancyType.UNRECORDED_REFUND;
import static org.assertj.core.api.Assertions.assertThat;

import com.reconciler.dataset.OrderRow;
import com.reconciler.dataset.PaymentRow;
import com.reconciler.ingest.OrderCsvParser;
import com.reconciler.ingest.PaymentCsvParser;
import java.io.InputStream;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * The correctness proof: runs the engine over the two bundled files and pins every headline
 * figure and every discrepancy to what the README documents. asOf is fixed for determinism;
 * it only affects the urgency of the one pending charge, never any of the money figures.
 */
class ReconciliationOnSampleDataTest {

	private static final Instant AS_OF = Instant.parse("2025-05-12T00:00:00Z");

	private final OrderCsvParser orderParser = new OrderCsvParser();
	private final PaymentCsvParser paymentParser = new PaymentCsvParser();
	private final ReconciliationEngine engine = new ReconciliationEngine();

	@Test
	void headlineFigures() {
		ReconciliationSummary s = reconcileSampleData().summary();

		assertThat(s.totalOrders()).isEqualTo(184);
		assertThat(s.totalPayments()).isEqualTo(187);
		assertThat(s.matchedOrders()).isEqualTo(168);
		assertThat(s.discrepancyCount()).isEqualTo(19);
		assertThat(s.moneyAtRisk()).isEqualByComparingTo("1349.43");
	}

	@Test
	void exposureBuckets() {
		var byDirection = reconcileSampleData().summary().amountByDirection();

		assertThat(byDirection.get(Direction.OWED_TO_US)).isEqualByComparingTo("720.85");
		assertThat(byDirection.get(Direction.OWED_BY_US)).isEqualByComparingTo("628.58");
		assertThat(byDirection.get(Direction.INVESTIGATION)).isEqualByComparingTo("308.00");
		assertThat(byDirection.get(Direction.ALREADY_LOST)).isEqualByComparingTo("99.00");
		assertThat(byDirection.get(Direction.WATCH)).isEqualByComparingTo("67.00");
	}

	@Test
	void everyDiscrepancyIsTheExpectedOrder() {
		ReconciliationResult r = reconcileSampleData();

		assertThat(ordersOf(r, MISSING_PAYMENT))
				.containsExactlyInAnyOrder("ORD-1201", "ORD-1202", "ORD-1203", "ORD-1204");
		assertThat(ordersOf(r, FAILED_PAYMENT)).containsExactly("ORD-2001");
		assertThat(ordersOf(r, PENDING_SETTLEMENT)).containsExactly("ORD-2002");
		assertThat(ordersOf(r, DUPLICATE_PAYMENT)).containsExactlyInAnyOrder("ORD-1501", "ORD-1502");
		assertThat(ordersOf(r, CHARGE_ON_CANCELLED)).containsExactly("ORD-1701");
		assertThat(ordersOf(r, INCOMPLETE_REFUND)).containsExactly("ORD-1702");
		assertThat(ordersOf(r, UNRECORDED_REFUND)).containsExactly("ORD-1703");
		assertThat(ordersOf(r, CURRENCY_MISMATCH)).containsExactlyInAnyOrder("ORD-1601", "ORD-1602");
		assertThat(ordersOf(r, AMOUNT_MISMATCH)).containsExactlyInAnyOrder("ORD-1401", "ORD-1402", "ORD-1403");
		assertThat(r.discrepancies().stream().filter(d -> d.type() == ORDER_NOT_FOUND).count()).isEqualTo(3);
	}

	@Test
	void theRecentPendingChargeIsAWatchItem() {
		Discrepancy pending = ordersOfDiscrepancy(reconcileSampleData(), PENDING_SETTLEMENT).get(0);

		assertThat(pending.direction()).isEqualTo(Direction.WATCH);
		assertThat(pending.amountImpact()).isEqualByComparingTo("67.00");
	}

	private ReconciliationResult reconcileSampleData() {
		UUID dataset = UUID.randomUUID();
		UUID user = UUID.randomUUID();
		List<OrderRow> orders = orderParser.parse(resource("orders.csv"), dataset, user).rows();
		List<PaymentRow> payments = paymentParser.parse(resource("payments.csv"), dataset, user).rows();
		return engine.run(orders, payments, AS_OF);
	}

	private static InputStream resource(String name) {
		return ReconciliationOnSampleDataTest.class.getResourceAsStream("/sample-data/" + name);
	}

	private static List<String> ordersOf(ReconciliationResult result, DiscrepancyType type) {
		return result.discrepancies().stream()
				.filter(d -> d.type() == type)
				.map(Discrepancy::orderId)
				.toList();
	}

	private static List<Discrepancy> ordersOfDiscrepancy(ReconciliationResult result, DiscrepancyType type) {
		return result.discrepancies().stream().filter(d -> d.type() == type).toList();
	}
}
