package com.reconciler.ingest;

import com.reconciler.dataset.Dataset;
import com.reconciler.dataset.DatasetService;
import com.reconciler.dataset.DatasetStatus;
import com.reconciler.dataset.ImportedRow;
import com.reconciler.dataset.OrderRow;
import com.reconciler.dataset.OrderRowRepository;
import com.reconciler.dataset.PaymentRow;
import com.reconciler.dataset.PaymentRowRepository;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IngestService {

	private final DatasetService datasets;
	private final OrderRowRepository orderRows;
	private final PaymentRowRepository paymentRows;
	private final OrderCsvParser orderParser;
	private final PaymentCsvParser paymentParser;

	public IngestService(DatasetService datasets, OrderRowRepository orderRows, PaymentRowRepository paymentRows,
			OrderCsvParser orderParser, PaymentCsvParser paymentParser) {
		this.datasets = datasets;
		this.orderRows = orderRows;
		this.paymentRows = paymentRows;
		this.orderParser = orderParser;
		this.paymentParser = paymentParser;
	}

	@Transactional
	public UploadSummary ingestOrders(UUID datasetId, UUID userId, InputStream csv) {
		datasets.getOwned(datasetId, userId); // ownership gate
		ParseResult<OrderRow> parsed = orderParser.parse(csv, datasetId, userId);

		orderRows.deleteByDatasetId(datasetId); // re-upload replaces
		List<OrderRow> stored = orderRows.saveAll(parsed.rows());
		int duplicates = flagDuplicateRows(stored);

		datasets.markOrdersLoaded(datasetId, userId);
		return summary("orders", parsed, duplicates);
	}

	@Transactional
	public UploadSummary ingestPayments(UUID datasetId, UUID userId, InputStream csv) {
		Dataset dataset = datasets.getOwned(datasetId, userId);
		if (dataset.getStatus() == DatasetStatus.CREATED) {
			throw new OrdersRequiredException();
		}
		ParseResult<PaymentRow> parsed = paymentParser.parse(csv, datasetId, userId);

		paymentRows.deleteByDatasetId(datasetId);
		paymentRows.saveAll(parsed.rows());

		datasets.markPaymentsLoaded(datasetId, userId);
		return summary("payments", parsed, 0);
	}

	/**
	 * Flags rows that repeat an earlier row byte-for-byte (same value in every column).
	 * The first occurrence is left alone; each repeat is pointed back at it and flagged,
	 * so a line that appears twice in the file isn't reconciled as a second order. Runs
	 * after saveAll so the first occurrence already has its generated id. Returns the count.
	 */
	private int flagDuplicateRows(List<OrderRow> rows) {
		Map<Map<String, String>, OrderRow> firstByContent = new HashMap<>();
		int flagged = 0;
		for (OrderRow row : rows) {
			OrderRow first = firstByContent.putIfAbsent(row.getRawJson(), row);
			if (first != null) {
				row.markDuplicateOf(first.getId());
				flagged++;
			}
		}
		return flagged;
	}

	private static UploadSummary summary(String kind, ParseResult<? extends ImportedRow> parsed, int duplicates) {
		int flagged = (int) parsed.rows().stream()
				.filter(row -> !row.getDataQualityFlags().isEmpty())
				.count();
		return new UploadSummary(kind, parsed.dataRowCount(), parsed.rows().size(), flagged, duplicates, parsed.errors());
	}
}
