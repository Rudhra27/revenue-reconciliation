package com.reconciler.ingest;

import static org.assertj.core.api.Assertions.assertThat;

import com.reconciler.dataset.PaymentRow;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PaymentCsvParserTest {

	private static final UUID DATASET = UUID.randomUUID();
	private static final UUID USER = UUID.randomUUID();

	private final PaymentCsvParser parser = new PaymentCsvParser();

	@Test
	void readsCleanRowsAndCollectsRejections() {
		ParseResult<PaymentRow> result = parse("payments-sample.csv");

		assertThat(result.dataRowCount()).isEqualTo(6);
		assertThat(result.rows()).hasSize(5);
		assertThat(result.errors()).extracting(RowError::line).containsExactly(6); // amount 'bad'
	}

	@Test
	void keepsRawReferenceWhenTheKeyHadToBeAdjusted() {
		PaymentRow row = rowFor("TXN002");

		assertThat(row.getOrderReference()).isEqualTo("ORD-1002");
		assertThat(row.getOrderReferenceRaw()).isEqualTo("ord-1002");
		assertThat(row.getDataQualityFlags()).contains("ORDER_REFERENCE_ADJUSTED");
	}

	@Test
	void flagsAMissingProcessedTimestamp() {
		PaymentRow row = rowFor("TXN003");

		assertThat(row.getProcessedAt()).isNull();
		assertThat(row.getDataQualityFlags()).contains("MISSING_PROCESSED_AT");
	}

	@Test
	void flagsAnUnknownStatusAndASettlementThatDoesNotAddUp() {
		PaymentRow row = rowFor("TXN006");

		assertThat(row.getDataQualityFlags()).contains("UNKNOWN_PAYMENT_STATUS", "NET_SETTLED_MISMATCH");
	}

	private ParseResult<PaymentRow> parse(String fixture) {
		try (InputStream in = getClass().getResourceAsStream("/csv/" + fixture)) {
			return parser.parse(in, DATASET, USER);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	private PaymentRow rowFor(String transactionRef) {
		return parse("payments-sample.csv").rows().stream()
				.filter(row -> row.getTransactionRef().equals(transactionRef))
				.findFirst()
				.orElseThrow();
	}
}
