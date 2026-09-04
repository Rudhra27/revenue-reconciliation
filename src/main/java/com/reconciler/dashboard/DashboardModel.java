package com.reconciler.dashboard;

import com.reconciler.dataset.DatasetView;
import com.reconciler.reconciliation.Direction;
import com.reconciler.reconciliation.DiscrepancyType;
import com.reconciler.reconciliation.ReconciliationRun;
import java.math.BigDecimal;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;

/** Everything the dashboard page renders (the drill-down table loads separately). */
public record DashboardModel(
		DatasetView dataset,
		ReconciliationRun run,
		List<DirectionStat> byDirection,
		List<TypeStat> byType,
		List<FlagStat> dataQuality) {

	private static final DateTimeFormatter AS_OF_FORMAT =
			DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm 'UTC'").withZone(ZoneOffset.UTC);

	public String reconciledAt() {
		return AS_OF_FORMAT.format(run.getAsOf());
	}

	public BigDecimal owedToUs() {
		return amountFor(Direction.OWED_TO_US);
	}

	public BigDecimal owedByUs() {
		return amountFor(Direction.OWED_BY_US);
	}

	public long dataQualityRowCount() {
		return dataQuality.stream().mapToLong(FlagStat::count).sum();
	}

	private BigDecimal amountFor(Direction direction) {
		return byDirection.stream()
				.filter(s -> s.direction() == direction)
				.map(DirectionStat::impact)
				.findFirst()
				.orElse(BigDecimal.ZERO);
	}

	public record DirectionStat(Direction direction, String label, long count, BigDecimal impact) {
	}

	public record TypeStat(DiscrepancyType type, String label, long count, BigDecimal impact) {
	}

	public record FlagStat(String flag, long count) {
	}
}
