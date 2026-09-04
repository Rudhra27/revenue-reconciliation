package com.reconciler.dashboard;

import com.reconciler.reconciliation.DiscrepancyRow;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** One drill-down table row, with the detail flattened for display. */
public record DiscrepancyView(
		UUID id,
		String type,
		String subtype,
		String severity,
		String direction,
		String orderId,
		String currency,
		BigDecimal amountImpact,
		List<DetailEntry> detail) {

	public static DiscrepancyView of(DiscrepancyRow row) {
		return new DiscrepancyView(
				row.getId(),
				row.getType().label(),
				row.getSubtype(),
				row.getSeverity().name(),
				row.getDirection().label(),
				row.getOrderId(),
				row.getCurrency(),
				row.getAmountImpact(),
				flatten(row.getDetail()));
	}

	private static List<DetailEntry> flatten(Map<String, Object> detail) {
		return detail.entrySet().stream()
				.map(e -> new DetailEntry(e.getKey(), String.valueOf(e.getValue())))
				.toList();
	}

	public record DetailEntry(String key, String value) {
	}
}
