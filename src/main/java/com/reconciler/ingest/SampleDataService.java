package com.reconciler.ingest;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.UUID;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Loads the two files bundled under resources/sample-data into a dataset in one step. */
@Service
public class SampleDataService {

	private final IngestService ingest;

	public SampleDataService(IngestService ingest) {
		this.ingest = ingest;
	}

	@Transactional
	public List<UploadSummary> load(UUID datasetId, UUID userId) {
		UploadSummary orders = ingest.ingestOrders(datasetId, userId, open("sample-data/orders.csv"));
		UploadSummary payments = ingest.ingestPayments(datasetId, userId, open("sample-data/payments.csv"));
		return List.of(orders, payments);
	}

	private static InputStream open(String path) {
		try {
			return new ClassPathResource(path).getInputStream();
		} catch (IOException e) {
			throw new IllegalStateException("Bundled sample file is missing: " + path, e);
		}
	}
}
