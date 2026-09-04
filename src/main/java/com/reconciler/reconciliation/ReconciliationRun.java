package com.reconciler.reconciliation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** A persisted reconciliation run: the headline figures for one dataset. Replaced on re-run. */
@Entity
@Table(name = "reconciliation_result")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class ReconciliationRun {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(name = "dataset_id", nullable = false)
	private UUID datasetId;

	@Column(name = "user_id", nullable = false)
	private UUID userId;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "engine_version", nullable = false)
	private String engineVersion;

	@Column(name = "as_of", nullable = false)
	private Instant asOf;

	@Column(name = "total_orders", nullable = false)
	private int totalOrders;

	@Column(name = "total_payments", nullable = false)
	private int totalPayments;

	@Column(name = "matched_orders", nullable = false)
	private int matchedOrders;

	@Column(name = "discrepancy_count", nullable = false)
	private int discrepancyCount;

	@Column(name = "value_reconciled", nullable = false)
	private BigDecimal valueReconciled;

	@Column(name = "value_in_dispute", nullable = false)
	private BigDecimal valueInDispute;

	@Column(name = "money_at_risk", nullable = false)
	private BigDecimal moneyAtRisk;

	@PrePersist
	void onCreate() {
		if (createdAt == null) {
			createdAt = Instant.now();
		}
	}
}
