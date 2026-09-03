package com.reconciler.dataset;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PaymentRowRepository extends JpaRepository<PaymentRow, UUID> {

	long countByDatasetId(UUID datasetId);

	List<PaymentRow> findByDatasetIdOrderBySourceLineNo(UUID datasetId);

	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("delete from PaymentRow r where r.datasetId = :datasetId")
	void deleteByDatasetId(@Param("datasetId") UUID datasetId);
}
