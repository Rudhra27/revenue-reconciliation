package com.reconciler.ingest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.reconciler.dataset.OrderRow;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OrderCsvParserTest {

	private static final UUID DATASET = UUID.randomUUID();
	private static final UUID USER = UUID.randomUUID();

	private final OrderCsvParser parser = new OrderCsvParser();

	@Test
	void readsCleanRowsAndCollectsRejections() {
		ParseResult<OrderRow> result = parse("orders-sample.csv");

		assertThat(result.dataRowCount()).isEqualTo(8);
		assertThat(result.rows()).hasSize(6);
		// line 8 = gross_amount 'oops', line 9 = blank order_id
		assertThat(result.errors()).extracting(RowError::line).containsExactlyInAnyOrder(8, 9);
	}

	@Test
	void normalisesTheOrderIdAndCurrency() {
		OrderRow row = rowFor("orders-sample.csv", "ORD-1002");

		assertThat(row.getOrderId()).isEqualTo("ORD-1002");
		assertThat(row.getCurrency()).isEqualTo("USD");
	}

	@Test
	void flagsMissingEmailAndDiscount() {
		OrderRow row = rowFor("orders-sample.csv", "ORD-1003");

		assertThat(row.getDataQualityFlags()).contains("MISSING_EMAIL", "MISSING_DISCOUNT");
	}

	@Test
	void keepsARowWithAnUnparseableDateButFlagsIt() {
		OrderRow row = rowFor("orders-sample.csv", "ORD-1005");

		assertThat(row.getOrderDate()).isNull();
		assertThat(row.getDataQualityFlags()).contains("UNPARSEABLE_DATE");
	}

	@Test
	void rejectsAFileWithTheWrongHeader() {
		assertThatThrownBy(() -> parse("orders-bad-header.csv")).isInstanceOf(InvalidCsvException.class);
	}

	private ParseResult<OrderRow> parse(String fixture) {
		try (InputStream in = getClass().getResourceAsStream("/csv/" + fixture)) {
			return parser.parse(in, DATASET, USER);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	private OrderRow rowFor(String fixture, String orderId) {
		return parse(fixture).rows().stream()
				.filter(row -> row.getOrderId().equals(orderId))
				.findFirst()
				.orElseThrow();
	}
}
