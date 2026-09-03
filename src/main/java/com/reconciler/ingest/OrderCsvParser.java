package com.reconciler.ingest;

import com.reconciler.dataset.DataQualityFlag;
import com.reconciler.dataset.OrderRow;
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
class OrderCsvParser extends CsvRowParser<OrderRow> {

	private static final List<String> HEADER = List.of(
			"order_id", "order_date", "customer_email", "currency", "gross_amount", "discount", "net_amount", "status");
	private static final Set<String> KNOWN_STATUSES = Set.of("completed", "cancelled", "refunded");
	private static final BigDecimal ONE_CENT = new BigDecimal("0.01");

	@Override
	List<String> expectedHeader() {
		return HEADER;
	}

	@Override
	OrderRow toRow(CSVRecord record, UUID datasetId, UUID userId) {
		List<DataQualityFlag> flags = new ArrayList<>();

		String orderId = Fields.key(record.get("order_id"));
		if (orderId == null) {
			throw new RowRejectedException("missing order_id");
		}
		BigDecimal gross = requireMoney(record, "gross_amount");
		BigDecimal net = requireMoney(record, "net_amount");
		BigDecimal discount = optionalMoney(record, "discount", flags, DataQualityFlag.MISSING_DISCOUNT);
		Instant orderDate = optionalOrderDate(record, flags);

		String email = Fields.trimToNull(record.get("customer_email"));
		if (email == null) {
			flags.add(DataQualityFlag.MISSING_EMAIL);
		}
		String currency = Fields.key(record.get("currency"));
		if (currency == null) {
			flags.add(DataQualityFlag.MISSING_CURRENCY);
		}
		String status = Fields.lower(record.get("status"));
		if (status == null || !KNOWN_STATUSES.contains(status)) {
			flags.add(DataQualityFlag.UNKNOWN_STATUS);
		}
		// The spec found net_amount = gross - discount everywhere; flag it if that ever breaks.
		if (discount != null && gross.subtract(discount).subtract(net).abs().compareTo(ONE_CENT) > 0) {
			flags.add(DataQualityFlag.NET_AMOUNT_MISMATCH);
		}

		return OrderRow.builder()
				.datasetId(datasetId)
				.userId(userId)
				.sourceLineNo((int) record.getRecordNumber() + 1)
				.rawJson(record.toMap())
				.orderId(orderId)
				.orderDate(orderDate)
				.customerEmail(email)
				.currency(currency)
				.grossAmount(gross)
				.discount(discount)
				.netAmount(net)
				.status(status)
				.dataQualityFlags(names(flags))
				.build();
	}

	private static BigDecimal requireMoney(CSVRecord record, String column) {
		try {
			BigDecimal value = Fields.money(record.get(column));
			if (value == null) {
				throw new RowRejectedException("missing " + column);
			}
			return value;
		} catch (NumberFormatException e) {
			throw new RowRejectedException(column + " is not a number: '" + record.get(column) + "'");
		}
	}

	private static BigDecimal optionalMoney(CSVRecord record, String column, List<DataQualityFlag> flags,
			DataQualityFlag whenMissing) {
		try {
			BigDecimal value = Fields.money(record.get(column));
			if (value == null) {
				flags.add(whenMissing);
			}
			return value;
		} catch (NumberFormatException e) {
			flags.add(whenMissing);
			return null;
		}
	}

	private static Instant optionalOrderDate(CSVRecord record, List<DataQualityFlag> flags) {
		try {
			return Fields.orderDate(record.get("order_date"));
		} catch (DateTimeParseException e) {
			flags.add(DataQualityFlag.UNPARSEABLE_DATE);
			return null;
		}
	}

	private static List<String> names(List<DataQualityFlag> flags) {
		return flags.stream().map(DataQualityFlag::name).toList();
	}
}
