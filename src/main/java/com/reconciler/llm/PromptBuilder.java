package com.reconciler.llm;

import com.reconciler.reconciliation.DiscrepancyRow;
import com.reconciler.reconciliation.ReconciliationRun;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * Turns a discrepancy (or a whole run) into the context we hand the model. Only derived
 * facts go in — type, the computed numbers, dates and amounts. No customer details.
 */
@Component
class PromptBuilder {

	static final String PROMPT_VERSION = "1";

	private static final String SYSTEM = """
			You explain reconciliation discrepancies to a revenue analyst. You are given a \
			deterministic classification and the numbers behind it. Do not recompute or dispute \
			the match decision. Explain plainly what likely happened and what someone should do \
			about it. If the data is insufficient to be sure, say so.

			Reply with a single JSON object and nothing else, with exactly these string fields: \
			"summary", "likely_cause", "recommended_action", and "confidence" (one of "low", \
			"medium", "high").""";

	private final ObjectMapper json;

	PromptBuilder(ObjectMapper json) {
		this.json = json;
	}

	LlmInput forDiscrepancy(DiscrepancyRow d) {
		Map<String, Object> context = new LinkedHashMap<>();
		context.put("type", d.getType().name());
		context.put("typeMeaning", d.getType().label());
		context.put("subtype", d.getSubtype());
		context.put("orderId", d.getOrderId());
		context.put("currency", d.getCurrency());
		context.put("amountImpact", d.getAmountImpact());
		context.put("interpretation", d.getDirection().label());
		context.put("numbers", d.getDetail());

		String body = write(context);
		return new LlmInput(body, List.of(
				new ChatMessage("system", SYSTEM),
				new ChatMessage("user", "A reconciliation engine flagged this discrepancy:\n\n" + body
						+ "\n\nExplain what likely happened and what to do about it.")));
	}

	LlmInput forSummary(ReconciliationRun run, List<DiscrepancyRow> discrepancies) {
		Map<String, Object> context = new LinkedHashMap<>();
		context.put("totalOrders", run.getTotalOrders());
		context.put("totalPayments", run.getTotalPayments());
		context.put("matchedOrders", run.getMatchedOrders());
		context.put("discrepancyCount", run.getDiscrepancyCount());
		context.put("valueReconciled", run.getValueReconciled());
		context.put("valueInDispute", run.getValueInDispute());
		context.put("moneyAtRisk", run.getMoneyAtRisk());
		context.put("discrepancies", discrepancies.stream().map(PromptBuilder::brief).toList());

		String body = write(context);
		return new LlmInput(body, List.of(
				new ChatMessage("system", SYSTEM),
				new ChatMessage("user", "Here is a reconciliation summary:\n\n" + body
						+ "\n\nGive a short briefing: how bad is it, what kinds of problems dominate, "
						+ "and which ones to look at first.")));
	}

	private static Map<String, Object> brief(DiscrepancyRow d) {
		Map<String, Object> row = new LinkedHashMap<>();
		row.put("type", d.getType().name());
		row.put("orderId", d.getOrderId());
		row.put("impact", d.getAmountImpact());
		row.put("interpretation", d.getDirection().label());
		return row;
	}

	private String write(Map<String, Object> context) {
		try {
			return json.writerWithDefaultPrettyPrinter().writeValueAsString(context);
		} catch (JacksonException e) {
			throw new IllegalStateException("Could not serialise the LLM context", e);
		}
	}

	record ChatMessage(String role, String content) {
	}

	record LlmInput(String canonicalJson, List<ChatMessage> messages) {
	}
}
