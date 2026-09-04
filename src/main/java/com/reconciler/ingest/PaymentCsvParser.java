package com.reconciler.ingest;

import com.reconciler.dataset.DataQualityFlag;
import com.reconciler.dataset.PaymentRow;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Component;

@Component
public class PaymentCsvParser extends CsvRowParser<PaymentRow> {

	private static final List<String> HEADER = List.of(
			"transaction_ref", "processed_at", "order_reference", "currency", "amount", "fee", "net_settled", "type",
			"status");
	private static final Set<String> KNOWN_TYPES = Set.of("charge", "refund");
	private static final Set<String> KNOWN_STATUSES = Set.of("settled", "pending", "failed");
	private static final BigDecimal ONE_CENT = new BigDecimal("0.01");

	@Override
	List<String> expectedHeader() {
		return HEADER;
	}

	@Override
	PaymentRow toRow(CSVRecord record, UUID datasetId, UUID userId) {
		List<DataQualityFlag> flags = new ArrayList<>();

		String transactionRef = Fields.trimToNull(record.get("transaction_ref"));
		if (transactionRef == null) {
			throw new RowRejectedException("missing transaction_ref");
		}
		BigDecimal amount = requireAmount(record);
		BigDecimal fee = quietMoney(record.get("fee"));
		BigDecimal netSettled = quietMoney(record.get("net_settled"));

		// Keep both the cleaned match key and, when they differ, what the file literally had.
		String referenceRaw = Fields.trimToNull(record.get("order_reference"));
		String reference = Fields.key(referenceRaw);
		String referenceRawToStore = null;
		if (reference == null) {
			flags.add(DataQualityFlag.MISSING_ORDER_REFERENCE);
		} else if (!reference.equals(referenceRaw)) {
			flags.add(DataQualityFlag.ORDER_REFERENCE_ADJUSTED);
			referenceRawToStore = referenceRaw;
		}

		Instant processedAt = processedAt(record, flags);

		String currency = Fields.key(record.get("currency"));
		if (currency == null) {
			flags.add(DataQualityFlag.MISSING_CURRENCY);
		}
		String type = Fields.lower(record.get("type"));
		if (type == null || !KNOWN_TYPES.contains(type)) {
			flags.add(DataQualityFlag.UNKNOWN_PAYMENT_TYPE);
		}
		String status = Fields.lower(record.get("status"));
		if (status == null || !KNOWN_STATUSES.contains(status)) {
			flags.add(DataQualityFlag.UNKNOWN_PAYMENT_STATUS);
		}
		if (fee != null && netSettled != null
				&& amount.subtract(fee).subtract(netSettled).abs().compareTo(ONE_CENT) > 0) {
			flags.add(DataQualityFlag.NET_SETTLED_MISMATCH);
		}

		return PaymentRow.builder()
				.datasetId(datasetId)
				.userId(userId)
				.sourceLineNo((int) record.getRecordNumber() + 1)
				.rawJson(record.toMap())
				.transactionRef(transactionRef)
				.processedAt(processedAt)
				.orderReference(reference)
				.orderReferenceRaw(referenceRawToStore)
				.currency(currency)
				.amount(amount)
				.fee(fee)
				.netSettled(netSettled)
				.type(type)
				.status(status)
				.dataQualityFlags(flags.stream().map(DataQualityFlag::name).toList())
				.build();
	}

	private static BigDecimal requireAmount(CSVRecord record) {
		try {
			BigDecimal value = Fields.money(record.get("amount"));
			if (value == null) {
				throw new RowRejectedException("missing amount");
			}
			return value;
		} catch (NumberFormatException e) {
			throw new RowRejectedException("amount is not a number: '" + record.get("amount") + "'");
		}
	}

	private static BigDecimal quietMoney(String raw) {
		try {
			return Fields.money(raw);
		} catch (NumberFormatException e) {
			return null;
		}
	}

	private static Instant processedAt(CSVRecord record, List<DataQualityFlag> flags) {
		String raw = Fields.trimToNull(record.get("processed_at"));
		if (raw == null) {
			flags.add(DataQualityFlag.MISSING_PROCESSED_AT);
			return null;
		}
		try {
			return Fields.paymentDate(raw);
		} catch (DateTimeParseException e) {
			flags.add(DataQualityFlag.UNPARSEABLE_DATE);
			return null;
		}
	}
}
