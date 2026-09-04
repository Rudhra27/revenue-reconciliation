package com.reconciler.reconciliation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** A persisted discrepancy — the stored form of a {@link Discrepancy} the engine produced. */
@Entity
@Table(name = "discrepancy")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class DiscrepancyRow {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(name = "run_id", nullable = false)
	private UUID runId;

	@Column(name = "dataset_id", nullable = false)
	private UUID datasetId;

	@Column(name = "user_id", nullable = false)
	private UUID userId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private DiscrepancyType type;

	private String subtype;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private Severity severity;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private Direction direction;

	@Column(name = "order_id")
	private String orderId;

	@Column(name = "order_row_id")
	private UUID orderRowId;

	@JdbcTypeCode(SqlTypes.ARRAY)
	@Column(name = "payment_row_ids", columnDefinition = "uuid[]", nullable = false)
	@Builder.Default
	private List<UUID> paymentRowIds = List.of();

	private String currency;

	@Column(name = "amount_impact", nullable = false)
	private BigDecimal amountImpact;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(columnDefinition = "jsonb", nullable = false)
	@Builder.Default
	private Map<String, Object> detail = Map.of();
}
