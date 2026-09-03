package com.reconciler.ingest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.reconciler.TestcontainersConfiguration;
import com.reconciler.dataset.DatasetService;
import com.reconciler.dataset.OrderRowRepository;
import com.reconciler.dataset.PaymentRowRepository;
import com.reconciler.user.AppUser;
import com.reconciler.user.AppUserPrincipal;
import com.reconciler.user.UserService;
import java.io.IOException;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class IngestControllerTest {

	@Autowired
	private MockMvc mvc;

	@Autowired
	private UserService users;

	@Autowired
	private DatasetService datasets;

	@Autowired
	private OrderRowRepository orderRows;

	@Autowired
	private PaymentRowRepository paymentRows;

	@Test
	void loadSamplePopulatesBothFilesAndRedirects() throws Exception {
		AppUser owner = users.register("owner@example.com", "password123");
		UUID datasetId = datasets.create(owner.getId(), "Demo").getId();

		mvc.perform(post("/datasets/{id}/load-sample", datasetId)
						.with(user(principal(owner)))
						.with(csrf()))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/datasets/" + datasetId));

		assertThat(orderRows.countByDatasetId(datasetId)).isEqualTo(185);
		assertThat(paymentRows.countByDatasetId(datasetId)).isEqualTo(187);
	}

	@Test
	void uploadsTheOrdersFileAndRedirectsToTheDataset() throws Exception {
		AppUser owner = users.register("owner@example.com", "password123");
		UUID datasetId = datasets.create(owner.getId(), "Q1").getId();

		mvc.perform(multipart("/datasets/{id}/orders", datasetId)
						.file(ordersFile())
						.with(user(principal(owner)))
						.with(csrf()))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/datasets/" + datasetId));

		assertThat(orderRows.countByDatasetId(datasetId)).isEqualTo(6);
	}

	@Test
	void cannotUploadToAnotherUsersDataset() throws Exception {
		AppUser owner = users.register("owner@example.com", "password123");
		AppUser intruder = users.register("intruder@example.com", "password123");
		UUID datasetId = datasets.create(owner.getId(), "Q1").getId();

		mvc.perform(multipart("/datasets/{id}/orders", datasetId)
						.file(ordersFile())
						.with(user(principal(intruder)))
						.with(csrf()))
				.andExpect(status().isNotFound());

		assertThat(orderRows.countByDatasetId(datasetId)).isZero();
	}

	private MockMultipartFile ordersFile() throws IOException {
		try (var in = getClass().getResourceAsStream("/csv/orders-sample.csv")) {
			return new MockMultipartFile("file", "orders.csv", "text/csv", in.readAllBytes());
		}
	}

	private static AppUserPrincipal principal(AppUser user) {
		return new AppUserPrincipal(user.getId(), user.getEmail(), user.getPasswordHash());
	}
}
