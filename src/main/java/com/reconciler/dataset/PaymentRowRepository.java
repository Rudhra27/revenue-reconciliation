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

	@Query(value = """
			select f as flag, count(*) as count
			from payment_row p cross join lateral unnest(p.data_quality_flags) as f
			where p.dataset_id = :datasetId
			group by f order by count(*) desc
			""", nativeQuery = true)
	List<OrderRowRepository.FlagCount> flagCounts(@Param("datasetId") UUID datasetId);

	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("delete from PaymentRow r where r.datasetId = :datasetId")
	void deleteByDatasetId(@Param("datasetId") UUID datasetId);
}
