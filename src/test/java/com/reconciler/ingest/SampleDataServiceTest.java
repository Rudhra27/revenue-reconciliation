package com.reconciler.ingest;

import static org.assertj.core.api.Assertions.assertThat;

import com.reconciler.TestcontainersConfiguration;
import com.reconciler.dataset.DatasetService;
import com.reconciler.dataset.DatasetStatus;
import com.reconciler.dataset.OrderRowRepository;
import com.reconciler.dataset.PaymentRowRepository;
import com.reconciler.user.AppUser;
import com.reconciler.user.UserService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class SampleDataServiceTest {

	@Autowired
	private SampleDataService sampleData;

	@Autowired
	private UserService users;

	@Autowired
	private DatasetService datasets;

	@Autowired
	private OrderRowRepository orderRows;

	@Autowired
	private PaymentRowRepository paymentRows;

	@Test
	void loadsBothBundledFilesIntoTheDataset() {
		AppUser user = users.register("owner@example.com", "password123");
		UUID datasetId = datasets.create(user.getId(), "Demo").getId();

		List<UploadSummary> summaries = sampleData.load(datasetId, user.getId());

		assertThat(summaries).extracting(UploadSummary::fileKind).containsExactly("orders", "payments");
		assertThat(orderRows.countByDatasetId(datasetId)).isEqualTo(185);
		assertThat(paymentRows.countByDatasetId(datasetId)).isEqualTo(187);
		assertThat(datasets.getOwned(datasetId, user.getId()).getStatus()).isEqualTo(DatasetStatus.PAYMENTS_LOADED);

		// The bundled orders file has one byte-identical repeat row (ORD-1004).
		assertThat(summaries.get(0).duplicatesFlagged()).isEqualTo(1);
	}
}
