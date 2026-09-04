package com.reconciler.llm;

import com.reconciler.user.AppUserPrincipal;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

/**
 * Both endpoints run the model call inline (it's bounded by the client timeout) and return
 * the explanation fragment. A FAILED or INVALID result still comes back as HTML so the page
 * can show it and offer a retry.
 */
@Controller
public class LlmController {

	private final LlmService llm;

	public LlmController(LlmService llm) {
		this.llm = llm;
	}

	@PostMapping("/discrepancies/{id}/explain")
	public String explainDiscrepancy(@AuthenticationPrincipal AppUserPrincipal user, @PathVariable UUID id,
			Model model) {
		model.addAttribute("explanation", llm.explainDiscrepancy(id, user.id()));
		model.addAttribute("retryUrl", "/discrepancies/" + id + "/explain");
		return "fragments/explanation :: panel";
	}

	@PostMapping("/datasets/{id}/explain-summary")
	public String explainSummary(@AuthenticationPrincipal AppUserPrincipal user, @PathVariable UUID id, Model model) {
		model.addAttribute("explanation", llm.explainSummary(id, user.id()));
		model.addAttribute("retryUrl", "/datasets/" + id + "/explain-summary");
		return "fragments/explanation :: panel";
	}
}
