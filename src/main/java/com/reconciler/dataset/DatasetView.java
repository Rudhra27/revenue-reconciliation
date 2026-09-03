package com.reconciler.dataset;

import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * What the templates render. Keeps the entity out of the view layer and hands
 * the timestamp over already formatted (Thymeleaf can't format a bare Instant).
 */
public record DatasetView(UUID id, String name, DatasetStatus status, String created) {

	private static final DateTimeFormatter CREATED_FORMAT =
			DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm 'UTC'").withZone(ZoneOffset.UTC);

	public static DatasetView of(Dataset dataset) {
		return new DatasetView(
				dataset.getId(),
				dataset.getName(),
				dataset.getStatus(),
				CREATED_FORMAT.format(dataset.getCreatedAt()));
	}
}
