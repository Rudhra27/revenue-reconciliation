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

	@Query(value = """
			select f as flag, count(*) as count
			from order_row o cross join lateral unnest(o.data_quality_flags) as f
			where o.dataset_id = :datasetId
			group by f order by count(*) desc
			""", nativeQuery = true)
	List<FlagCount> flagCounts(@Param("datasetId") UUID datasetId);

	interface FlagCount {
		String getFlag();

		long getCount();
	}

	// clears the persistence context too, so a re-upload in the same transaction
	// doesn't leave the just-deleted rows lying around as stale managed entities
	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("delete from OrderRow r where r.datasetId = :datasetId")
	void deleteByDatasetId(@Param("datasetId") UUID datasetId);
}
