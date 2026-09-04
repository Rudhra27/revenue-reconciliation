package com.reconciler.reconciliation;

import com.reconciler.dataset.DatasetService;
import com.reconciler.dataset.OrderRow;
import com.reconciler.dataset.OrderRowRepository;
import com.reconciler.dataset.PaymentRow;
import com.reconciler.dataset.PaymentRowRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReconciliationService {

	private final DatasetService datasets;
	private final OrderRowRepository orderRows;
	private final PaymentRowRepository paymentRows;
	private final ReconciliationRunRepository runs;
	private final DiscrepancyRowRepository discrepancies;
	private final ReconciliationEngine engine = new ReconciliationEngine();

	public ReconciliationService(DatasetService datasets, OrderRowRepository orderRows,
			PaymentRowRepository paymentRows, ReconciliationRunRepository runs,
			DiscrepancyRowRepository discrepancies) {
		this.datasets = datasets;
		this.orderRows = orderRows;
		this.paymentRows = paymentRows;
		this.runs = runs;
		this.discrepancies = discrepancies;
	}

	@Transactional
	public ReconciliationRun reconcile(UUID datasetId, UUID userId, Instant asOf) {
		datasets.getOwned(datasetId, userId); // ownership gate

		List<OrderRow> orders = orderRows.findByDatasetIdOrderBySourceLineNo(datasetId);
		List<PaymentRow> payments = paymentRows.findByDatasetIdOrderBySourceLineNo(datasetId);
		if (orders.isEmpty() || payments.isEmpty()) {
			throw new NotReadyToReconcileException();
		}

		ReconciliationResult result = engine.run(orders, payments, asOf);

		// A run is replaced wholesale; delete the child rows first for the FK.
		discrepancies.deleteByDatasetId(datasetId);
		runs.deleteByDatasetId(datasetId);

		ReconciliationRun run = runs.saveAndFlush(toRun(datasetId, userId, asOf, result));
		discrepancies.saveAll(result.discrepancies().stream()
				.map(d -> toRow(run.getId(), datasetId, userId, d))
				.toList());

		datasets.markReconciled(datasetId, userId);
		return run;
	}

	@Transactional(readOnly = true)
	public Optional<ReconciliationRun> latestRun(UUID datasetId, UUID userId) {
		datasets.getOwned(datasetId, userId);
		return runs.findByDatasetId(datasetId);
	}

	private static ReconciliationRun toRun(UUID datasetId, UUID userId, Instant asOf, ReconciliationResult result) {
		ReconciliationSummary s = result.summary();
		return ReconciliationRun.builder()
				.datasetId(datasetId)
				.userId(userId)
				.engineVersion(ReconciliationEngine.VERSION)
				.asOf(asOf)
				.totalOrders(s.totalOrders())
				.totalPayments(s.totalPayments())
				.matchedOrders(s.matchedOrders())
				.discrepancyCount(s.discrepancyCount())
				.valueReconciled(s.valueReconciled())
				.valueInDispute(s.valueInDispute())
				.moneyAtRisk(s.moneyAtRisk())
				.build();
	}

	private static DiscrepancyRow toRow(UUID runId, UUID datasetId, UUID userId, Discrepancy d) {
		return DiscrepancyRow.builder()
				.runId(runId)
				.datasetId(datasetId)
				.userId(userId)
				.type(d.type())
				.subtype(d.subtype())
				.severity(d.severity())
				.direction(d.direction())
				.orderId(d.orderId())
				.orderRowId(d.orderRowId())
				.paymentRowIds(d.paymentRowIds())
				.currency(d.currency())
				.amountImpact(d.amountImpact())
				.detail(d.detail())
				.build();
	}
}
