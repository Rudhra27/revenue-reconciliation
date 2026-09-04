package com.reconciler.reconciliation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.reconciler.TestcontainersConfiguration;
import com.reconciler.dataset.DatasetService;
import com.reconciler.dataset.DatasetStatus;
import com.reconciler.ingest.SampleDataService;
import com.reconciler.user.AppUser;
import com.reconciler.user.UserService;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class ReconciliationServiceTest {

	private static final Instant AS_OF = Instant.parse("2025-05-12T00:00:00Z");

	@Autowired
	private ReconciliationService reconciliation;

	@Autowired
	private SampleDataService sampleData;

	@Autowired
	private UserService users;

	@Autowired
	private DatasetService datasets;

	@Autowired
	private DiscrepancyRowRepository discrepancyRows;

	@Autowired
	private ReconciliationRunRepository runs;

	@Test
	void persistsTheRunAndItsDiscrepancies() {
		Fixture f = loadedFixture();

		ReconciliationRun run = reconciliation.reconcile(f.datasetId, f.userId, AS_OF);

		assertThat(run.getMoneyAtRisk()).isEqualByComparingTo("1349.43");
		assertThat(run.getEngineVersion()).isEqualTo(ReconciliationEngine.VERSION);
		assertThat(discrepancyRows.findByDatasetId(f.datasetId)).hasSize(19);
		assertThat(datasets.getOwned(f.datasetId, f.userId).getStatus()).isEqualTo(DatasetStatus.RECONCILED);
	}

	@Test
	void storesTheNumbersBehindEachDiscrepancy() {
		Fixture f = loadedFixture();
		reconciliation.reconcile(f.datasetId, f.userId, AS_OF);

		DiscrepancyRow duplicate = discrepancyRows.findByDatasetId(f.datasetId).stream()
				.filter(d -> d.getType() == DiscrepancyType.DUPLICATE_PAYMENT)
				.findFirst()
				.orElseThrow();

		assertThat(duplicate.getDirection()).isEqualTo(Direction.OWED_BY_US);
		assertThat(duplicate.getDetail()).containsKey("settledCharges");
		assertThat(duplicate.getPaymentRowIds()).hasSize(2);
	}

	@Test
	void reRunningReplacesThePreviousResult() {
		Fixture f = loadedFixture();

		reconciliation.reconcile(f.datasetId, f.userId, AS_OF);
		reconciliation.reconcile(f.datasetId, f.userId, AS_OF);

		assertThat(runs.findByDatasetId(f.datasetId)).isPresent();
		assertThat(discrepancyRows.findByDatasetId(f.datasetId)).hasSize(19);
	}

	@Test
	void reconcilingBeforeBothFilesAreLoadedFails() {
		AppUser user = users.register("owner@example.com", "password123");
		UUID datasetId = datasets.create(user.getId(), "Empty").getId();

		assertThatThrownBy(() -> reconciliation.reconcile(datasetId, user.getId(), AS_OF))
				.isInstanceOf(NotReadyToReconcileException.class);
	}

	private Fixture loadedFixture() {
		AppUser user = users.register("owner@example.com", "password123");
		UUID datasetId = datasets.create(user.getId(), "Demo").getId();
		sampleData.load(datasetId, user.getId());
		return new Fixture(user.getId(), datasetId);
	}

	private record Fixture(UUID userId, UUID datasetId) {
	}
}
