package com.reconciler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.reconciler.dataset.DatasetService;
import com.reconciler.ingest.SampleDataService;
import com.reconciler.reconciliation.DiscrepancyRowRepository;
import com.reconciler.reconciliation.ReconciliationService;
import com.reconciler.user.AppUser;
import com.reconciler.user.AppUserPrincipal;
import com.reconciler.user.UserService;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

/**
 * Walks every data-bearing route as someone who doesn't own the data. Each should 404 —
 * never a 200 with someone else's rows, and never a 403 that would confirm the resource exists.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class TenancyTest {

	private static final Instant AS_OF = Instant.parse("2025-05-12T00:00:00Z");
	private static final String ORDERS_HEADER =
			"order_id,order_date,customer_email,currency,gross_amount,discount,net_amount,status\n";

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

	private UUID datasetId;
	private UUID discrepancyId;
	private AppUserPrincipal intruder;

	@BeforeEach
	void setUp() {
		AppUser owner = users.register("owner@example.com", "password123");
		datasetId = datasets.create(owner.getId(), "Owned").getId();
		sampleData.load(datasetId, owner.getId());
		reconciliation.reconcile(datasetId, owner.getId(), AS_OF);
		discrepancyId = discrepancies.findByDatasetId(datasetId).get(0).getId();

		AppUser other = users.register("intruder@example.com", "password123");
		intruder = new AppUserPrincipal(other.getId(), other.getEmail(), other.getPasswordHash());
	}

	@Test
	void cannotReadTheDatasetPage() throws Exception {
		perform(get("/datasets/{id}", datasetId));
	}

	@Test
	void cannotOpenTheDashboard() throws Exception {
		perform(get("/dashboard").param("datasetId", datasetId.toString()));
	}

	@Test
	void cannotReadTheDrilldown() throws Exception {
		perform(get("/dashboard/discrepancies").param("datasetId", datasetId.toString()));
	}

	@Test
	void cannotLoadSampleData() throws Exception {
		perform(post("/datasets/{id}/load-sample", datasetId));
	}

	@Test
	void cannotReconcile() throws Exception {
		perform(post("/datasets/{id}/reconcile", datasetId));
	}

	@Test
	void cannotExplainTheSummary() throws Exception {
		perform(post("/datasets/{id}/explain-summary", datasetId));
	}

	@Test
	void cannotExplainADiscrepancy() throws Exception {
		perform(post("/discrepancies/{id}/explain", discrepancyId));
	}

	@Test
	void cannotUploadEitherFile() throws Exception {
		MockMultipartFile file = new MockMultipartFile("file", "orders.csv", "text/csv", ORDERS_HEADER.getBytes());

		mvc.perform(multipart("/datasets/{id}/orders", datasetId).file(file).with(user(intruder)).with(csrf()))
				.andExpect(status().isNotFound());
		mvc.perform(multipart("/datasets/{id}/payments", datasetId).file(file).with(user(intruder)).with(csrf()))
				.andExpect(status().isNotFound());
	}

	@Test
	void theOwnersDataIsUntouchedByTheAttempts() {
		assertThat(discrepancies.findByDatasetId(datasetId)).hasSize(19);
	}

	@Test
	void anAnonymousVisitorIsSentToLogin() throws Exception {
		mvc.perform(get("/dashboard").param("datasetId", datasetId.toString()))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/login"));
	}

	private void perform(MockHttpServletRequestBuilder request) throws Exception {
		mvc.perform(request.with(user(intruder)).with(csrf())).andExpect(status().isNotFound());
	}
}
