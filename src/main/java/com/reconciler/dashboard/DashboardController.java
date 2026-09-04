package com.reconciler.dashboard;

import com.reconciler.dataset.DatasetService;
import com.reconciler.reconciliation.Direction;
import com.reconciler.reconciliation.DiscrepancyRow;
import com.reconciler.reconciliation.DiscrepancyType;
import com.reconciler.user.AppUserPrincipal;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class DashboardController {

	private final DashboardService dashboard;
	private final DiscrepancyQueryService drilldown;
	private final DatasetService datasets;

	public DashboardController(DashboardService dashboard, DiscrepancyQueryService drilldown, DatasetService datasets) {
		this.dashboard = dashboard;
		this.drilldown = drilldown;
		this.datasets = datasets;
	}

	@GetMapping("/dashboard")
	public String dashboard(@AuthenticationPrincipal AppUserPrincipal user, @RequestParam UUID datasetId, Model model) {
		return dashboard.load(datasetId, user.id())
				.map(dash -> {
					model.addAttribute("dash", dash);
					model.addAttribute("types", DiscrepancyType.values());
					model.addAttribute("directions", Direction.values());
					return "dashboard";
				})
				.orElse("redirect:/datasets/" + datasetId);
	}

	@GetMapping("/dashboard/discrepancies")
	public String drilldown(@AuthenticationPrincipal AppUserPrincipal user, @RequestParam UUID datasetId,
			@RequestParam(required = false) String type, @RequestParam(required = false) String direction,
			@RequestParam(required = false) String q, @RequestParam(defaultValue = "0") int page, Model model) {
		datasets.getOwned(datasetId, user.id()); // ownership gate

		Page<DiscrepancyRow> results = drilldown.search(datasetId,
				parse(DiscrepancyType.class, type), parse(Direction.class, direction), q, page);

		model.addAttribute("results", results.map(DiscrepancyView::of));
		model.addAttribute("datasetId", datasetId);
		model.addAttribute("type", type);
		model.addAttribute("direction", direction);
		model.addAttribute("q", q);
		return "fragments/discrepancies :: table";
	}

	private static <E extends Enum<E>> E parse(Class<E> enumType, String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		try {
			return Enum.valueOf(enumType, value);
		} catch (IllegalArgumentException ignored) {
			return null;
		}
	}
}
