package com.reconciler.reconciliation;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DiscrepancyRowRepository extends JpaRepository<DiscrepancyRow, UUID> {

	List<DiscrepancyRow> findByDatasetId(UUID datasetId);

	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("delete from DiscrepancyRow d where d.datasetId = :datasetId")
	void deleteByDatasetId(@Param("datasetId") UUID datasetId);
}
