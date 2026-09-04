package com.reconciler.dashboard;

import com.reconciler.reconciliation.DiscrepancyRow;
import com.reconciler.reconciliation.DiscrepancyRowRepository;
import com.reconciler.reconciliation.DiscrepancyType;
import com.reconciler.reconciliation.Direction;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/** The drill-down: filter by type/direction, free-text search, biggest impact first, paged. */
@Service
public class DiscrepancyQueryService {

	static final int PAGE_SIZE = 20;

	private final DiscrepancyRowRepository discrepancies;

	public DiscrepancyQueryService(DiscrepancyRowRepository discrepancies) {
		this.discrepancies = discrepancies;
	}

	@Transactional(readOnly = true)
	public Page<DiscrepancyRow> search(UUID datasetId, UUID userId, DiscrepancyType type, Direction direction,
			String query, int page) {
		Specification<DiscrepancyRow> spec = (root, q, cb) -> {
			List<Predicate> where = new ArrayList<>();
			where.add(cb.equal(root.get("datasetId"), datasetId));
			where.add(cb.equal(root.get("userId"), userId)); // defence in depth alongside the controller's ownership gate
			if (type != null) {
				where.add(cb.equal(root.get("type"), type));
			}
			if (direction != null) {
				where.add(cb.equal(root.get("direction"), direction));
			}
			if (StringUtils.hasText(query)) {
				where.add(cb.like(root.get("searchText"), "%" + query.trim().toLowerCase() + "%"));
			}
			return cb.and(where.toArray(Predicate[]::new));
		};
		return discrepancies.findAll(spec,
				PageRequest.of(Math.max(page, 0), PAGE_SIZE, Sort.by(Sort.Direction.DESC, "amountImpact")));
	}
}
