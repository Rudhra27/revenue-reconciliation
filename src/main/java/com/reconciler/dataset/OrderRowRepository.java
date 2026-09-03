package com.reconciler.dataset;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderRowRepository extends JpaRepository<OrderRow, UUID> {

	long countByDatasetId(UUID datasetId);

	List<OrderRow> findByDatasetIdOrderBySourceLineNo(UUID datasetId);

	// clears the persistence context too, so a re-upload in the same transaction
	// doesn't leave the just-deleted rows lying around as stale managed entities
	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("delete from OrderRow r where r.datasetId = :datasetId")
	void deleteByDatasetId(@Param("datasetId") UUID datasetId);
}
