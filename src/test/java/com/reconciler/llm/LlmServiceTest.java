package com.reconciler.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.reconciler.TestcontainersConfiguration;
import com.reconciler.dataset.DatasetService;
import com.reconciler.ingest.SampleDataService;
import com.reconciler.reconciliation.DiscrepancyNotFoundException;
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

@SpringBootTest(properties = "app.llm.api-key=fake-test-key")
@Import(TestcontainersConfiguration.class)
@Transactional
class LlmServiceTest {

	private static final Instant AS_OF = Instant.parse("2025-05-12T00:00:00Z");
	private static final String VALID_JSON = """
			{"summary":"Two settled charges for one order.",\
			"likely_cause":"A retry after a timeout that had actually succeeded.",\
			"recommended_action":"Refund one charge and check the retry logic.",\
			"confidence":"high"}""";

	@MockitoBean
	private LlmClient client;

	@Autowired
	private LlmService llm;

	@Autowired
	private LlmExplanationRepository explanations;

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
	void storesAValidExplanation() {
		Ctx c = reconciled();
		when(client.complete(anyList())).thenReturn(VALID_JSON);

		LlmExplanation explanation = llm.explainDiscrepancy(c.discrepancyId, c.userId);

		assertThat(explanation.getStatus()).isEqualTo(ExplanationStatus.OK);
		assertThat(explanation.getSummary()).isNotBlank();
		assertThat(explanation.getRecommendedAction()).isNotBlank();
		assertThat(explanation.getConfidence()).isEqualTo("high");
		assertThat(explanations.findById(explanation.getId())).isPresent();
	}

	@Test
	void acceptsJsonWrappedInAMarkdownFence() {
		Ctx c = reconciled();
		when(client.complete(anyList())).thenReturn("```json\n" + VALID_JSON + "\n```");

		LlmExplanation explanation = llm.explainDiscrepancy(c.discrepancyId, c.userId);

		assertThat(explanation.getStatus()).isEqualTo(ExplanationStatus.OK);
		assertThat(explanation.getSummary()).isNotBlank();
	}

	@Test
	void aReplyThatIsNotJsonBecomesInvalid() {
		Ctx c = reconciled();
		when(client.complete(anyList())).thenReturn("Sorry, I can't help with that.");

		LlmExplanation explanation = llm.explainDiscrepancy(c.discrepancyId, c.userId);

		assertThat(explanation.getStatus()).isEqualTo(ExplanationStatus.INVALID);
		assertThat(explanation.getRawResponse()).isEqualTo("Sorry, I can't help with that.");
		assertThat(explanation.getSummary()).isNull();
	}

	@Test
	void aReplyMissingAFieldBecomesInvalid() {
		Ctx c = reconciled();
		when(client.complete(anyList())).thenReturn("{\"summary\":\"x\",\"confidence\":\"high\"}");

		LlmExplanation explanation = llm.explainDiscrepancy(c.discrepancyId, c.userId);

		assertThat(explanation.getStatus()).isEqualTo(ExplanationStatus.INVALID);
	}

	@Test
	void aFailedCallIsRecordedAsFailed() {
		Ctx c = reconciled();
		when(client.complete(anyList())).thenThrow(new LlmCallException("The model call timed out."));

		LlmExplanation explanation = llm.explainDiscrepancy(c.discrepancyId, c.userId);

		assertThat(explanation.getStatus()).isEqualTo(ExplanationStatus.FAILED);
		assertThat(explanation.getError()).contains("timed out");
	}

	@Test
	void aSecondRequestReusesTheCachedExplanation() {
		Ctx c = reconciled();
		when(client.complete(anyList())).thenReturn(VALID_JSON);

		LlmExplanation first = llm.explainDiscrepancy(c.discrepancyId, c.userId);
		LlmExplanation second = llm.explainDiscrepancy(c.discrepancyId, c.userId);

		assertThat(second.getId()).isEqualTo(first.getId());
		verify(client, times(1)).complete(anyList());
	}

	@Test
	void cannotExplainAnotherUsersDiscrepancy() {
		Ctx c = reconciled();
		AppUser intruder = users.register("intruder@example.com", "password123");

		assertThatThrownBy(() -> llm.explainDiscrepancy(c.discrepancyId, intruder.getId()))
				.isInstanceOf(DiscrepancyNotFoundException.class);
	}

	private Ctx reconciled() {
		AppUser user = users.register("owner@example.com", "password123");
		UUID datasetId = datasets.create(user.getId(), "Demo").getId();
		sampleData.load(datasetId, user.getId());
		reconciliation.reconcile(datasetId, user.getId(), AS_OF);
		UUID discrepancyId = discrepancies.findByDatasetId(datasetId).get(0).getId();
		return new Ctx(user.getId(), datasetId, discrepancyId);
	}

	private record Ctx(UUID userId, UUID datasetId, UUID discrepancyId) {
	}
}
