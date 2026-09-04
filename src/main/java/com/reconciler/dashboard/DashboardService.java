package com.reconciler.dashboard;

import com.reconciler.dashboard.DashboardModel.DirectionStat;
import com.reconciler.dashboard.DashboardModel.FlagStat;
import com.reconciler.dashboard.DashboardModel.TypeStat;
import com.reconciler.dataset.Dataset;
import com.reconciler.dataset.DatasetService;
import com.reconciler.dataset.DatasetView;
import com.reconciler.dataset.OrderRowRepository;
import com.reconciler.dataset.PaymentRowRepository;
import com.reconciler.reconciliation.Direction;
import com.reconciler.reconciliation.DiscrepancyRowRepository;
import com.reconciler.reconciliation.DiscrepancyType;
import com.reconciler.reconciliation.ReconciliationRunRepository;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DashboardService {

	private final DatasetService datasets;
	private final ReconciliationRunRepository runs;
	private final DiscrepancyRowRepository discrepancies;
	private final OrderRowRepository orderRows;
	private final PaymentRowRepository paymentRows;

	public DashboardService(DatasetService datasets, ReconciliationRunRepository runs,
			DiscrepancyRowRepository discrepancies, OrderRowRepository orderRows, PaymentRowRepository paymentRows) {
		this.datasets = datasets;
		this.runs = runs;
		this.discrepancies = discrepancies;
		this.orderRows = orderRows;
		this.paymentRows = paymentRows;
	}

	@Transactional(readOnly = true)
	public Optional<DashboardModel> load(UUID datasetId, UUID userId) {
		Dataset dataset = datasets.getOwned(datasetId, userId);
		return runs.findByDatasetId(datasetId).map(run -> new DashboardModel(
				DatasetView.of(dataset),
				run,
				directionStats(datasetId),
				typeStats(datasetId),
				flagStats(datasetId)));
	}

	// Every direction, in a fixed order, so the breakdown reads the same each time.
	private List<DirectionStat> directionStats(UUID datasetId) {
		Map<Direction, DiscrepancyRowRepository.DirectionAggregate> byDirection = new LinkedHashMap<>();
		discrepancies.aggregateByDirection(datasetId).forEach(a -> byDirection.put(a.getDirection(), a));

		List<DirectionStat> stats = new ArrayList<>();
		for (Direction direction : Direction.values()) {
			var aggregate = byDirection.get(direction);
			long count = aggregate == null ? 0 : aggregate.getCount();
			if (count > 0) {
				stats.add(new DirectionStat(direction, direction.label(), count, aggregate.getImpact()));
			}
		}
		return stats;
	}

	private List<TypeStat> typeStats(UUID datasetId) {
		Map<DiscrepancyType, DiscrepancyRowRepository.TypeAggregate> byType = new LinkedHashMap<>();
		discrepancies.aggregateByType(datasetId).forEach(a -> byType.put(a.getType(), a));

		List<TypeStat> stats = new ArrayList<>();
		for (DiscrepancyType type : DiscrepancyType.values()) {
			var aggregate = byType.get(type);
			if (aggregate != null && aggregate.getCount() > 0) {
				stats.add(new TypeStat(type, type.label(), aggregate.getCount(), aggregate.getImpact()));
			}
		}
		return stats;
	}

	private List<FlagStat> flagStats(UUID datasetId) {
		Map<String, Long> counts = new LinkedHashMap<>();
		orderRows.flagCounts(datasetId).forEach(f -> counts.merge(f.getFlag(), f.getCount(), Long::sum));
		paymentRows.flagCounts(datasetId).forEach(f -> counts.merge(f.getFlag(), f.getCount(), Long::sum));
		return counts.entrySet().stream()
				.sorted(Map.Entry.<String, Long>comparingByValue().reversed())
				.map(e -> new FlagStat(e.getKey(), e.getValue()))
				.toList();
	}
}
