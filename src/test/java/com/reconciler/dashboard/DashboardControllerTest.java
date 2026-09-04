package com.reconciler.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.reconciler.TestcontainersConfiguration;
import com.reconciler.dataset.DatasetService;
import com.reconciler.ingest.SampleDataService;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class DashboardControllerTest {

	private static final Instant AS_OF = Instant.parse("2025-05-12T00:00:00Z");

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

	@Test
	void showsTheHeadlineFiguresForAReconciledDataset() throws Exception {
		Ctx c = reconciledDataset();

		mvc.perform(get("/dashboard").param("datasetId", c.datasetId.toString()).with(user(c.principal)))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("Money at risk")))
				.andExpect(content().string(containsString("1,349.43")));
	}

	@Test
	void redirectsToTheDatasetWhenItHasNotBeenReconciled() throws Exception {
		AppUser user = users.register("owner@example.com", "password123");
		UUID datasetId = datasets.create(user.getId(), "Fresh").getId();

		mvc.perform(get("/dashboard").param("datasetId", datasetId.toString())
						.with(user(principal(user))))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/datasets/" + datasetId));
	}

	@Test
	void theDrilldownFiltersByType() throws Exception {
		Ctx c = reconciledDataset();

		mvc.perform(get("/dashboard/discrepancies")
						.param("datasetId", c.datasetId.toString())
						.param("type", "DUPLICATE_PAYMENT")
						.with(user(c.principal)))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("Duplicate charge")))
				.andExpect(content().string(not(containsString("Missing payment"))));
	}

	@Test
	void theDrilldownSearchesByTransactionRef() throws Exception {
		Ctx c = reconciledDataset();

		mvc.perform(get("/dashboard/discrepancies")
						.param("datasetId", c.datasetId.toString())
						.param("q", "txn700161")
						.with(user(c.principal)))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("Payment with no order")));
	}

	@Test
	void cannotOpenAnotherUsersDashboard() throws Exception {
		Ctx c = reconciledDataset();
		AppUser intruder = users.register("intruder@example.com", "password123");

		mvc.perform(get("/dashboard").param("datasetId", c.datasetId.toString())
						.with(user(principal(intruder))))
				.andExpect(status().isNotFound());
	}

	private Ctx reconciledDataset() {
		AppUser user = users.register("owner@example.com", "password123");
		UUID datasetId = datasets.create(user.getId(), "Demo").getId();
		sampleData.load(datasetId, user.getId());
		reconciliation.reconcile(datasetId, user.getId(), AS_OF);
		return new Ctx(datasetId, principal(user));
	}

	private static AppUserPrincipal principal(AppUser user) {
		return new AppUserPrincipal(user.getId(), user.getEmail(), user.getPasswordHash());
	}

	private record Ctx(UUID datasetId, AppUserPrincipal principal) {
	}
}
