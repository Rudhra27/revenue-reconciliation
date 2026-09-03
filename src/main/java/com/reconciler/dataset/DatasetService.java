package com.reconciler.dataset;

import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DatasetService {

	private final DatasetRepository datasets;

	public DatasetService(DatasetRepository datasets) {
		this.datasets = datasets;
	}

	@Transactional
	public Dataset create(UUID userId, String name) {
		return datasets.save(new Dataset(userId, name.trim()));
	}

	@Transactional(readOnly = true)
	public List<Dataset> listFor(UUID userId) {
		return datasets.findAllByUserIdOrderByCreatedAtDesc(userId);
	}

	@Transactional(readOnly = true)
	public Dataset getOwned(UUID id, UUID userId) {
		return datasets.findByIdAndUserId(id, userId)
				.orElseThrow(() -> new DatasetNotFoundException(id));
	}

	// Re-loads the dataset (the ingest step clears the persistence context) before updating it.
	@Transactional
	public void markOrdersLoaded(UUID id, UUID userId) {
		getOwned(id, userId).markOrdersLoaded();
	}

	@Transactional
	public void markPaymentsLoaded(UUID id, UUID userId) {
		getOwned(id, userId).markPaymentsLoaded();
	}
}
