package com.reconciler.reconciliation;

import com.reconciler.dataset.OrderRow;
import com.reconciler.dataset.PaymentRow;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Matches orders against payments and classifies every disagreement.
 *
 * <p>Pure and deterministic: no Spring, no database, no wall clock. The only outside input
 * is {@code asOf}, used solely to decide whether a pending settlement is recent enough to
 * be a "watch" item rather than an exposure. Given the same rows and the same {@code asOf}
 * it always produces the same result.
 */
public class ReconciliationEngine {

	private static final BigDecimal TOLERANCE_FLOOR = new BigDecimal("0.05");
	private static final BigDecimal TOLERANCE_RATE = new BigDecimal("0.005"); // 0.5%
	private static final Duration PENDING_WATCH_WINDOW = Duration.ofDays(7);

	private static final String CHARGE = "charge";
	private static final String REFUND = "refund";
	private static final String SETTLED = "settled";
	private static final String PENDING = "pending";
	private static final String FAILED = "failed";
	private static final String COMPLETED = "completed";
	private static final String CANCELLED = "cancelled";
	private static final String REFUNDED = "refunded";

	public ReconciliationResult run(List<OrderRow> orderRows, List<PaymentRow> paymentRows, Instant asOf) {
		// Payments keyed by the normalised order reference. Rows with no reference can't match
		// anything, so they fall through to the orphan pass below.
		Map<String, List<PaymentRow>> paymentsByRef = new LinkedHashMap<>();
		for (PaymentRow payment : sortedPayments(paymentRows)) {
			if (payment.getOrderReference() != null) {
				paymentsByRef.computeIfAbsent(payment.getOrderReference(), k -> new ArrayList<>()).add(payment);
			}
		}

		// One order per order_id: skip the byte-identical repeat rows flagged at import, and if
		// two different rows somehow share an id, keep the first in sort order.
		Map<String, OrderRow> orders = new LinkedHashMap<>();
		for (OrderRow order : sortedOrders(orderRows)) {
			if (order.getIsDuplicateOf() == null) {
				orders.putIfAbsent(order.getOrderId(), order);
			}
		}

		List<Discrepancy> discrepancies = new ArrayList<>();
		BigDecimal valueReconciled = BigDecimal.ZERO;
		BigDecimal valueInDispute = BigDecimal.ZERO;
		int matched = 0;

		for (OrderRow order : orders.values()) {
			List<PaymentRow> forOrder = paymentsByRef.getOrDefault(order.getOrderId(), List.of());
			Optional<Discrepancy> discrepancy = classifyOrder(order, forOrder, asOf);
			if (discrepancy.isEmpty()) {
				matched++;
				valueReconciled = valueReconciled.add(order.getNetAmount());
			} else {
				discrepancies.add(discrepancy.get());
				if (discrepancy.get().direction() != Direction.WATCH) {
					valueInDispute = valueInDispute.add(order.getNetAmount());
				}
			}
		}

		// Orphan pass: a settled/any payment whose reference points at no order.
		for (PaymentRow payment : sortedPayments(paymentRows)) {
			String ref = payment.getOrderReference();
			if (ref == null || !orders.containsKey(ref)) {
				discrepancies.add(orphan(payment));
				valueInDispute = valueInDispute.add(payment.getAmount());
			}
		}

		discrepancies.sort(DISCREPANCY_ORDER);
		ReconciliationSummary summary = summarise(orders.size(), paymentRows.size(), matched,
				money(valueReconciled), money(valueInDispute), discrepancies);
		return new ReconciliationResult(summary, discrepancies);
	}

	/**
	 * Decides the single primary discrepancy for one order, or empty if it reconciles.
	 *
	 * <p>The checks run in a fixed order so the result is stable and one order never produces
	 * two findings:
	 * <ol>
	 *   <li>currency mismatch first — if the money isn't even in the same currency, nothing
	 *       else about the amount can be trusted;</li>
	 *   <li>then a branch on the order's own status, because "no payment" or "partial payment"
	 *       means completely different things for a completed vs cancelled vs refunded order;</li>
	 *   <li>within a completed order: missing, then failed, then still-pending, then a doubled
	 *       charge, then a refund the order system never recorded, and finally a plain
	 *       amount mismatch;</li>
	 *   <li>anything left over is a clean match.</li>
	 * </ol>
	 */
	private Optional<Discrepancy> classifyOrder(OrderRow order, List<PaymentRow> payments, Instant asOf) {
		BigDecimal net = order.getNetAmount();
		BigDecimal tolerance = tolerance(net);

		List<PaymentRow> charges = payments.stream().filter(p -> CHARGE.equals(p.getType())).toList();
		List<PaymentRow> refunds = payments.stream().filter(p -> REFUND.equals(p.getType())).toList();
		List<PaymentRow> settledCharges = charges.stream().filter(p -> SETTLED.equals(p.getStatus())).toList();
		List<PaymentRow> settledRefunds = refunds.stream().filter(p -> SETTLED.equals(p.getStatus())).toList();

		BigDecimal chargeSum = sumAmounts(settledCharges);
		BigDecimal refundSum = sumAmounts(settledRefunds);
		BigDecimal effectivePaid = chargeSum.subtract(refundSum);

		// 1. Currency mismatch — any charge not in the order's currency.
		Optional<PaymentRow> wrongCurrency = charges.stream()
				.filter(p -> p.getCurrency() != null && order.getCurrency() != null)
				.filter(p -> !p.getCurrency().equals(order.getCurrency()))
				.findFirst();
		if (wrongCurrency.isPresent()) {
			Map<String, Object> detail = detail(order, effectivePaid, chargeSum, refundSum);
			detail.put("orderCurrency", order.getCurrency());
			detail.put("paymentCurrency", wrongCurrency.get().getCurrency());
			return Optional.of(discrepancy(DiscrepancyType.CURRENCY_MISMATCH, null, Direction.UNQUANTIFIED,
					order, charges, refunds, order.getCurrency(), BigDecimal.ZERO, detail));
		}

		String status = order.getStatus();

		// 2a. Cancelled order.
		if (CANCELLED.equals(status)) {
			if (effectivePaid.compareTo(tolerance) > 0) {
				return Optional.of(discrepancy(DiscrepancyType.CHARGE_ON_CANCELLED, null, Direction.OWED_BY_US,
						order, charges, refunds, order.getCurrency(), money(effectivePaid),
						detail(order, effectivePaid, chargeSum, refundSum)));
			}
			return Optional.empty(); // cancelled and not charged — correct
		}

		// 2b. Refunded order.
		if (REFUNDED.equals(status)) {
			BigDecimal outstanding = chargeSum.subtract(refundSum);
			if (outstanding.compareTo(tolerance) > 0) {
				return Optional.of(discrepancy(DiscrepancyType.INCOMPLETE_REFUND, null, Direction.OWED_BY_US,
						order, charges, refunds, order.getCurrency(), money(outstanding),
						detail(order, effectivePaid, chargeSum, refundSum)));
			}
			return Optional.empty(); // fully refunded — correct
		}

		// 3. Completed order (or an unknown status, treated the same way).

		if (charges.isEmpty()) {
			return Optional.of(discrepancy(DiscrepancyType.MISSING_PAYMENT, null, Direction.OWED_TO_US,
					order, charges, refunds, order.getCurrency(), money(net),
					detail(order, effectivePaid, chargeSum, refundSum)));
		}

		if (settledCharges.isEmpty()) {
			Optional<PaymentRow> pending = charges.stream().filter(p -> PENDING.equals(p.getStatus())).findFirst();
			if (pending.isPresent()) {
				boolean watch = isRecent(pending.get(), order, asOf);
				Map<String, Object> detail = detail(order, effectivePaid, chargeSum, refundSum);
				detail.put("pendingSince", String.valueOf(reference(pending.get(), order)));
				return Optional.of(discrepancy(DiscrepancyType.PENDING_SETTLEMENT, null,
						watch ? Direction.WATCH : Direction.OWED_TO_US,
						order, charges, refunds, order.getCurrency(), money(net), detail));
			}
			// Charges exist but none settled and none pending — the money never arrived.
			return Optional.of(discrepancy(DiscrepancyType.FAILED_PAYMENT, null, Direction.OWED_TO_US,
					order, charges, refunds, order.getCurrency(), money(net),
					detail(order, effectivePaid, chargeSum, refundSum)));
		}

		if (settledCharges.size() >= 2) {
			BigDecimal overCollected = chargeSum.subtract(net);
			return Optional.of(discrepancy(DiscrepancyType.DUPLICATE_PAYMENT, null, Direction.OWED_BY_US,
					order, charges, refunds, order.getCurrency(), money(overCollected),
					detail(order, effectivePaid, chargeSum, refundSum)));
		}

		if (!settledRefunds.isEmpty() && effectivePaid.abs().compareTo(tolerance) <= 0) {
			return Optional.of(discrepancy(DiscrepancyType.UNRECORDED_REFUND, null, Direction.ALREADY_LOST,
					order, charges, refunds, order.getCurrency(), money(refundSum),
					detail(order, effectivePaid, chargeSum, refundSum)));
		}

		BigDecimal difference = effectivePaid.subtract(net);
		if (difference.abs().compareTo(tolerance) > 0) {
			boolean over = difference.signum() > 0;
			return Optional.of(discrepancy(DiscrepancyType.AMOUNT_MISMATCH, over ? "OVER" : "UNDER",
					over ? Direction.OWED_BY_US : Direction.OWED_TO_US,
					order, charges, refunds, order.getCurrency(), money(difference.abs()),
					detail(order, effectivePaid, chargeSum, refundSum)));
		}

		return Optional.empty(); // within tolerance, right currency, consistent status
	}

	private Discrepancy orphan(PaymentRow payment) {
		Map<String, Object> detail = new LinkedHashMap<>();
		detail.put("orderReference", payment.getOrderReference());
		detail.put("transactionRef", payment.getTransactionRef());
		detail.put("amount", payment.getAmount());
		detail.put("type", payment.getType());
		detail.put("status", payment.getStatus());
		return new Discrepancy(DiscrepancyType.ORDER_NOT_FOUND, null, Severity.HIGH, Direction.INVESTIGATION,
				null, null, List.of(payment.getId()), payment.getCurrency(), money(payment.getAmount()), detail);
	}

	private Discrepancy discrepancy(DiscrepancyType type, String subtype, Direction direction, OrderRow order,
			List<PaymentRow> charges, List<PaymentRow> refunds, String currency, BigDecimal impact,
			Map<String, Object> detail) {
		List<UUID> paymentIds = new ArrayList<>();
		charges.forEach(p -> paymentIds.add(p.getId()));
		refunds.forEach(p -> paymentIds.add(p.getId()));
		Severity severity = (type == DiscrepancyType.PENDING_SETTLEMENT && direction == Direction.WATCH)
				? Severity.LOW
				: type.baseSeverity();
		return new Discrepancy(type, subtype, severity, direction, order.getOrderId(), order.getId(),
				List.copyOf(paymentIds), currency, impact, detail);
	}

	private static Map<String, Object> detail(OrderRow order, BigDecimal effectivePaid, BigDecimal chargeSum,
			BigDecimal refundSum) {
		Map<String, Object> detail = new LinkedHashMap<>();
		detail.put("orderStatus", order.getStatus());
		detail.put("orderNet", order.getNetAmount());
		detail.put("settledCharges", money(chargeSum));
		detail.put("settledRefunds", money(refundSum));
		detail.put("effectivePaid", money(effectivePaid));
		detail.put("difference", money(effectivePaid.subtract(order.getNetAmount())));
		return detail;
	}

	private static BigDecimal tolerance(BigDecimal net) {
		return net.abs().multiply(TOLERANCE_RATE).max(TOLERANCE_FLOOR);
	}

	private static BigDecimal sumAmounts(List<PaymentRow> rows) {
		return rows.stream().map(PaymentRow::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
	}

	private static boolean isRecent(PaymentRow pending, OrderRow order, Instant asOf) {
		Instant since = reference(pending, order);
		return since != null && Duration.between(since, asOf).compareTo(PENDING_WATCH_WINDOW) < 0;
	}

	private static Instant reference(PaymentRow payment, OrderRow order) {
		return payment.getProcessedAt() != null ? payment.getProcessedAt() : order.getOrderDate();
	}

	private static BigDecimal money(BigDecimal value) {
		return value.setScale(2, java.math.RoundingMode.HALF_UP);
	}

	private static List<OrderRow> sortedOrders(List<OrderRow> rows) {
		return rows.stream()
				.sorted(Comparator.comparing(OrderRow::getOrderId, Comparator.nullsLast(Comparator.naturalOrder()))
						.thenComparing(o -> String.valueOf(o.getId())))
				.toList();
	}

	private static List<PaymentRow> sortedPayments(List<PaymentRow> rows) {
		return rows.stream()
				.sorted(Comparator.comparing(PaymentRow::getTransactionRef,
						Comparator.nullsLast(Comparator.naturalOrder())))
				.toList();
	}

	// Most urgent first, then biggest money, then a stable tiebreak.
	private static final Comparator<Discrepancy> DISCREPANCY_ORDER = Comparator
			.comparing(Discrepancy::severity)
			.thenComparing(Discrepancy::amountImpact, Comparator.reverseOrder())
			.thenComparing(Discrepancy::type)
			.thenComparing(d -> d.orderId() != null ? d.orderId() : String.valueOf(d.paymentRowIds()));

	private static ReconciliationSummary summarise(int totalOrders, int totalPayments, int matched,
			BigDecimal valueReconciled, BigDecimal valueInDispute, List<Discrepancy> discrepancies) {
		Map<Direction, BigDecimal> byDirection = new EnumMap<>(Direction.class);
		Map<DiscrepancyType, ReconciliationSummary.TypeBreakdown> byType = new EnumMap<>(DiscrepancyType.class);

		for (Discrepancy d : discrepancies) {
			byDirection.merge(d.direction(), d.amountImpact(), BigDecimal::add);
			byType.merge(d.type(), new ReconciliationSummary.TypeBreakdown(1, d.amountImpact()),
					(a, b) -> new ReconciliationSummary.TypeBreakdown(a.count() + b.count(),
							a.amountImpact().add(b.amountImpact())));
		}

		BigDecimal moneyAtRisk = byDirection.getOrDefault(Direction.OWED_TO_US, BigDecimal.ZERO)
				.add(byDirection.getOrDefault(Direction.OWED_BY_US, BigDecimal.ZERO));

		return new ReconciliationSummary(totalOrders, totalPayments, matched, discrepancies.size(),
				valueReconciled, valueInDispute, money(moneyAtRisk), byDirection, byType);
	}
}
