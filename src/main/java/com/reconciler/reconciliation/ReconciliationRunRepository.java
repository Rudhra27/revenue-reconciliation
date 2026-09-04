package com.reconciler.reconciliation;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReconciliationRunRepository extends JpaRepository<ReconciliationRun, UUID> {

	Optional<ReconciliationRun> findByDatasetId(UUID datasetId);

	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("delete from ReconciliationRun r where r.datasetId = :datasetId")
	void deleteByDatasetId(@Param("datasetId") UUID datasetId);
}
