package com.reconciler.dataset;

import com.reconciler.user.AppUserPrincipal;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/datasets")
public class DatasetController {

	private final DatasetService datasets;

	public DatasetController(DatasetService datasets) {
		this.datasets = datasets;
	}

	@GetMapping
	public String list(@AuthenticationPrincipal AppUserPrincipal user, Model model) {
		model.addAttribute("datasets", views(datasets.listFor(user.id())));
		model.addAttribute("form", new NewDatasetForm());
		return "datasets/list";
	}

	@PostMapping
	public String create(@AuthenticationPrincipal AppUserPrincipal user,
			@Valid @ModelAttribute("form") NewDatasetForm form, BindingResult result, Model model) {
		if (result.hasErrors()) {
			model.addAttribute("datasets", views(datasets.listFor(user.id())));
			return "datasets/list";
		}
		Dataset created = datasets.create(user.id(), form.getName());
		return "redirect:/datasets/" + created.getId();
	}

	@GetMapping("/{id}")
	public String detail(@AuthenticationPrincipal AppUserPrincipal user, @PathVariable UUID id, Model model) {
		model.addAttribute("dataset", DatasetView.of(datasets.getOwned(id, user.id())));
		return "datasets/detail";
	}

	private static List<DatasetView> views(List<Dataset> datasets) {
		return datasets.stream().map(DatasetView::of).toList();
	}
}
