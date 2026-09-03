package com.reconciler.dataset;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
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

/**
 * One row from an uploaded orders.csv after normalisation. Wide and mostly-immutable,
 * so it's built through the Lombok builder rather than a long constructor.
 */
@Entity
@Table(name = "order_row")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class OrderRow implements ImportedRow {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(name = "dataset_id", nullable = false)
	private UUID datasetId;

	@Column(name = "user_id", nullable = false)
	private UUID userId;

	@Column(name = "source_line_no", nullable = false)
	private int sourceLineNo;

	// The untouched original row (header -> value), kept for the "this is what we read" view.
	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "raw_json", columnDefinition = "jsonb", nullable = false)
	private Map<String, String> rawJson;

	@Column(name = "order_id", nullable = false)
	private String orderId;

	@Column(name = "order_date")
	private Instant orderDate;

	@Column(name = "customer_email")
	private String customerEmail;

	private String currency;

	@Column(name = "gross_amount")
	private BigDecimal grossAmount;

	private BigDecimal discount;

	@Column(name = "net_amount", nullable = false)
	private BigDecimal netAmount;

	private String status;

	@JdbcTypeCode(SqlTypes.ARRAY)
	@Column(name = "data_quality_flags", columnDefinition = "text[]", nullable = false)
	@Builder.Default
	private List<String> dataQualityFlags = new ArrayList<>();

	@Column(name = "is_duplicate_of")
	private UUID isDuplicateOf;

	public void addFlag(DataQualityFlag flag) {
		if (dataQualityFlags.contains(flag.name())) {
			return;
		}
		// Replace the list rather than mutate in place so Hibernate reliably sees the change.
		List<String> next = new ArrayList<>(dataQualityFlags);
		next.add(flag.name());
		this.dataQualityFlags = next;
	}

	/** Point this row at the first identical row and flag it, so reconciliation can skip it. */
	public void markDuplicateOf(UUID originalRowId) {
		this.isDuplicateOf = originalRowId;
		addFlag(DataQualityFlag.DUPLICATE_ORDER_ROW);
	}
}
