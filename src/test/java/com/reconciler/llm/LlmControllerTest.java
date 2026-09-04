package com.reconciler.llm;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.reconciler.TestcontainersConfiguration;
import com.reconciler.dataset.DatasetService;
import com.reconciler.ingest.SampleDataService;
import com.reconciler.reconciliation.DiscrepancyRowRepository;
import com.reconciler.reconciliation.ReconciliationService;
import com.reconciler.user.AppUser;
import com.reconciler.user.AppUserPrincipal;
import com.reconciler.user.UserService;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = "app.llm.api-key=fake-test-key")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class LlmControllerTest {

	private static final Instant AS_OF = Instant.parse("2025-05-12T00:00:00Z");
	private static final String VALID_JSON = """
			{"summary":"Two settled charges for one order.",\
			"likely_cause":"A retry that had actually gone through.",\
			"recommended_action":"Refund one and check the retry logic.",\
			"confidence":"high"}""";

	@MockitoBean
	private LlmClient client;

	@Autowired
	private MockMvc mvc;

	@Autowired
	private UserService users;

	@Autowired
	private DatasetService datasets;

	@Autowired
	private SampleDataService sampleData;

	@Autowired
	private ReconciliationService reconciliation;

	@Autowired
	private DiscrepancyRowRepository discrepancies;

	@Test
	void returnsTheExplanationFragmentOnSuccess() throws Exception {
		Ctx c = reconciled();
		when(client.complete(anyList())).thenReturn(VALID_JSON);

		mvc.perform(post("/discrepancies/{id}/explain", c.discrepancyId).with(user(c.principal)).with(csrf()))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("Two settled charges")))
				.andExpect(content().string(containsString("What to do")));
	}

	@Test
	void returnsAnErrorFragmentWithARetryWhenTheCallFails() throws Exception {
		Ctx c = reconciled();
		when(client.complete(anyList())).thenThrow(new LlmCallException("The model call timed out."));

		mvc.perform(post("/discrepancies/{id}/explain", c.discrepancyId).with(user(c.principal)).with(csrf()))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("timed out")))
				.andExpect(content().string(containsString("Try again")));
	}

	@Test
	void explainsTheWholeDashboard() throws Exception {
		Ctx c = reconciled();
		when(client.complete(anyList())).thenReturn(VALID_JSON);

		mvc.perform(post("/datasets/{id}/explain-summary", c.datasetId).with(user(c.principal)).with(csrf()))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("Likely cause")));
	}

	@Test
	void cannotExplainAnotherUsersDiscrepancy() throws Exception {
		Ctx c = reconciled();
		AppUser intruder = users.register("intruder@example.com", "password123");

		mvc.perform(post("/discrepancies/{id}/explain", c.discrepancyId)
						.with(user(new AppUserPrincipal(intruder.getId(), intruder.getEmail(), intruder.getPasswordHash())))
						.with(csrf()))
				.andExpect(status().isNotFound());
	}

	private Ctx reconciled() {
		AppUser user = users.register("owner@example.com", "password123");
		UUID datasetId = datasets.create(user.getId(), "Demo").getId();
		sampleData.load(datasetId, user.getId());
		reconciliation.reconcile(datasetId, user.getId(), AS_OF);
		UUID discrepancyId = discrepancies.findByDatasetId(datasetId).get(0).getId();
		return new Ctx(datasetId, discrepancyId,
				new AppUserPrincipal(user.getId(), user.getEmail(), user.getPasswordHash()));
	}

	private record Ctx(UUID datasetId, UUID discrepancyId, AppUserPrincipal principal) {
	}
}
