package com.reconciler.llm;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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

@Entity
@Table(name = "llm_explanation")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class LlmExplanation {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(name = "dataset_id", nullable = false)
	private UUID datasetId;

	@Column(name = "discrepancy_id")
	private UUID discrepancyId;

	@Column(name = "user_id", nullable = false)
	private UUID userId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private ExplanationScope scope;

	@Column(name = "input_hash", nullable = false)
	private String inputHash;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private ExplanationStatus status;

	private String model;

	private BigDecimal temperature;

	@Column(name = "prompt_version")
	private String promptVersion;

	private String summary;

	@Column(name = "likely_cause")
	private String likelyCause;

	@Column(name = "recommended_action")
	private String recommendedAction;

	private String confidence;

	@Column(name = "raw_response")
	private String rawResponse;

	private String error;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@PrePersist
	void onCreate() {
		if (createdAt == null) {
			createdAt = Instant.now();
		}
	}

	public boolean isOk() {
		return status == ExplanationStatus.OK;
	}
}
