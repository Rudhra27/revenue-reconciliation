package com.reconciler.reconciliation;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.repository.query.Param;

public interface DiscrepancyRowRepository
		extends JpaRepository<DiscrepancyRow, UUID>, JpaSpecificationExecutor<DiscrepancyRow> {

	List<DiscrepancyRow> findByDatasetId(UUID datasetId);

	@Query("""
			select d.direction as direction, count(d) as count, coalesce(sum(d.amountImpact), 0) as impact
			from DiscrepancyRow d where d.datasetId = :datasetId group by d.direction
			""")
	List<DirectionAggregate> aggregateByDirection(@Param("datasetId") UUID datasetId);

	@Query("""
			select d.type as type, count(d) as count, coalesce(sum(d.amountImpact), 0) as impact
			from DiscrepancyRow d where d.datasetId = :datasetId group by d.type
			""")
	List<TypeAggregate> aggregateByType(@Param("datasetId") UUID datasetId);

	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("delete from DiscrepancyRow d where d.datasetId = :datasetId")
	void deleteByDatasetId(@Param("datasetId") UUID datasetId);

	interface DirectionAggregate {
		Direction getDirection();

		long getCount();

		BigDecimal getImpact();
	}

	interface TypeAggregate {
		DiscrepancyType getType();

		long getCount();

		BigDecimal getImpact();
	}
}
