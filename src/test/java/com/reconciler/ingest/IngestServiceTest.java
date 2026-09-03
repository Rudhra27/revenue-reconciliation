package com.reconciler.ingest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.reconciler.TestcontainersConfiguration;
import com.reconciler.dataset.DatasetService;
import com.reconciler.dataset.DatasetStatus;
import com.reconciler.dataset.OrderRow;
import com.reconciler.dataset.OrderRowRepository;
import com.reconciler.dataset.PaymentRowRepository;
import com.reconciler.user.AppUser;
import com.reconciler.user.UserService;
import java.io.InputStream;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class IngestServiceTest {

	@Autowired
	private IngestService ingest;

	@Autowired
	private UserService users;

	@Autowired
	private DatasetService datasets;

	@Autowired
	private OrderRowRepository orderRows;

	@Autowired
	private PaymentRowRepository paymentRows;

	@Test
	void ingestingOrdersStoresRowsFlagsDuplicatesAndAdvancesStatus() {
		Fixture f = fixture();

		UploadSummary summary = ingest.ingestOrders(f.datasetId, f.userId, csv("orders-sample.csv"));

		assertThat(summary.rowsStored()).isEqualTo(6);
		assertThat(summary.rowsRejected()).isEqualTo(2);
		assertThat(summary.duplicatesFlagged()).isEqualTo(1);
		assertThat(orderRows.countByDatasetId(f.datasetId)).isEqualTo(6);
		assertThat(datasets.getOwned(f.datasetId, f.userId).getStatus()).isEqualTo(DatasetStatus.ORDERS_LOADED);

		OrderRow duplicate = orderRows.findByDatasetIdOrderBySourceLineNo(f.datasetId).stream()
				.filter(row -> row.getIsDuplicateOf() != null)
				.findFirst()
				.orElseThrow();
		assertThat(duplicate.getDataQualityFlags()).contains("DUPLICATE_ORDER_ROW");
	}

	@Test
	void uploadingAgainReplacesTheRows() {
		Fixture f = fixture();

		ingest.ingestOrders(f.datasetId, f.userId, csv("orders-sample.csv"));
		ingest.ingestOrders(f.datasetId, f.userId, csv("orders-sample.csv"));

		assertThat(orderRows.countByDatasetId(f.datasetId)).isEqualTo(6);
	}

	@Test
	void paymentsCannotBeUploadedBeforeOrders() {
		Fixture f = fixture();

		assertThatThrownBy(() -> ingest.ingestPayments(f.datasetId, f.userId, csv("payments-sample.csv")))
				.isInstanceOf(OrdersRequiredException.class);
	}

	@Test
	void ingestingPaymentsAfterOrdersAdvancesStatus() {
		Fixture f = fixture();
		ingest.ingestOrders(f.datasetId, f.userId, csv("orders-sample.csv"));

		UploadSummary summary = ingest.ingestPayments(f.datasetId, f.userId, csv("payments-sample.csv"));

		assertThat(summary.rowsStored()).isEqualTo(5);
		assertThat(paymentRows.countByDatasetId(f.datasetId)).isEqualTo(5);
		assertThat(datasets.getOwned(f.datasetId, f.userId).getStatus()).isEqualTo(DatasetStatus.PAYMENTS_LOADED);
	}

	private Fixture fixture() {
		AppUser user = users.register("owner@example.com", "password123");
		UUID datasetId = datasets.create(user.getId(), "Q1").getId();
		return new Fixture(user.getId(), datasetId);
	}

	private InputStream csv(String fixture) {
		return getClass().getResourceAsStream("/csv/" + fixture);
	}

	private record Fixture(UUID userId, UUID datasetId) {
	}
}
