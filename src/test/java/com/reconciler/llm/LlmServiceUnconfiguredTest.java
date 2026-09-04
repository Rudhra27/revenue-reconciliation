package com.reconciler.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;

import com.reconciler.TestcontainersConfiguration;
import com.reconciler.dataset.DatasetService;
import com.reconciler.ingest.SampleDataService;
import com.reconciler.reconciliation.DiscrepancyRowRepository;
import com.reconciler.reconciliation.ReconciliationService;
import com.reconciler.user.AppUser;
import com.reconciler.user.UserService;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = "app.llm.enabled=false")
@Import(TestcontainersConfiguration.class)
@Transactional
class LlmServiceUnconfiguredTest {

	@MockitoBean
	private LlmClient client;

	@Autowired
	private LlmService llm;

	@Autowired
	private DiscrepancyRowRepository discrepancies;

	@Autowired
	private UserService users;

	@Autowired
	private DatasetService datasets;

	@Autowired
	private SampleDataService sampleData;

	@Autowired
	private ReconciliationService reconciliation;

	@Test
	void reportsThatTheServiceIsUnavailableAndNeverCallsTheApi() {
		AppUser user = users.register("owner@example.com", "password123");
		UUID datasetId = datasets.create(user.getId(), "Demo").getId();
		sampleData.load(datasetId, user.getId());
		reconciliation.reconcile(datasetId, user.getId(), Instant.parse("2025-05-12T00:00:00Z"));
		UUID discrepancyId = discrepancies.findByDatasetId(datasetId).get(0).getId();

		LlmExplanation explanation = llm.explainDiscrepancy(discrepancyId, user.getId());

		assertThat(explanation.getStatus()).isEqualTo(ExplanationStatus.FAILED);
		assertThat(explanation.getError()).contains("not configured");
		verifyNoInteractions(client);
	}
}
