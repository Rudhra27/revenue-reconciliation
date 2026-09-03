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
 * One row from an uploaded payments.csv after normalisation.
 */
@Entity
@Table(name = "payment_row")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class PaymentRow implements ImportedRow {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(name = "dataset_id", nullable = false)
	private UUID datasetId;

	@Column(name = "user_id", nullable = false)
	private UUID userId;

	@Column(name = "source_line_no", nullable = false)
	private int sourceLineNo;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "raw_json", columnDefinition = "jsonb", nullable = false)
	private Map<String, String> rawJson;

	@Column(name = "transaction_ref", nullable = false)
	private String transactionRef;

	@Column(name = "processed_at")
	private Instant processedAt;

	// Normalised match key (trimmed + upper-cased).
	@Column(name = "order_reference")
	private String orderReference;

	// Exactly what the file had, when it differed from the key.
	@Column(name = "order_reference_raw")
	private String orderReferenceRaw;

	private String currency;

	@Column(nullable = false)
	private BigDecimal amount;

	private BigDecimal fee;

	@Column(name = "net_settled")
	private BigDecimal netSettled;

	private String type;

	private String status;

	@JdbcTypeCode(SqlTypes.ARRAY)
	@Column(name = "data_quality_flags", columnDefinition = "text[]", nullable = false)
	@Builder.Default
	private List<String> dataQualityFlags = new ArrayList<>();

	public void addFlag(DataQualityFlag flag) {
		if (dataQualityFlags.contains(flag.name())) {
			return;
		}
		List<String> next = new ArrayList<>(dataQualityFlags);
		next.add(flag.name());
		this.dataQualityFlags = next;
	}
}
