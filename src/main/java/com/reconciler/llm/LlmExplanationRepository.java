package com.reconciler.llm;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LlmExplanationRepository extends JpaRepository<LlmExplanation, UUID> {

	Optional<LlmExplanation> findFirstByDiscrepancyIdAndInputHashOrderByCreatedAtDesc(UUID discrepancyId,
			String inputHash);

	Optional<LlmExplanation> findFirstByDatasetIdAndScopeAndInputHashOrderByCreatedAtDesc(UUID datasetId,
			ExplanationScope scope, String inputHash);
}
