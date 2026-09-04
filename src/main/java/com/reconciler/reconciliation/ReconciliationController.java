package com.reconciler.reconciliation;

import com.reconciler.user.AppUserPrincipal;
import java.time.Instant;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/datasets/{id}")
public class ReconciliationController {

	private final ReconciliationService reconciliation;

	public ReconciliationController(ReconciliationService reconciliation) {
		this.reconciliation = reconciliation;
	}

	@PostMapping("/reconcile")
	public String reconcile(@AuthenticationPrincipal AppUserPrincipal user, @PathVariable UUID id,
			RedirectAttributes flash) {
		try {
			ReconciliationRun run = reconciliation.reconcile(id, user.id(), Instant.now());
			flash.addFlashAttribute("reconcileMessage", String.format(
					"Reconciled: %d matched, %d discrepancies, %s at risk.",
					run.getMatchedOrders(), run.getDiscrepancyCount(), run.getMoneyAtRisk().toPlainString()));
		} catch (NotReadyToReconcileException e) {
			flash.addFlashAttribute("uploadError", e.getMessage());
		}
		return "redirect:/datasets/" + id;
	}
}
