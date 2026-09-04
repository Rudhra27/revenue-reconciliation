package com.reconciler.llm;

import com.reconciler.llm.PromptBuilder.LlmInput;
import com.reconciler.reconciliation.DiscrepancyNotFoundException;
import com.reconciler.reconciliation.DiscrepancyRow;
import com.reconciler.reconciliation.DiscrepancyRowRepository;
import com.reconciler.reconciliation.ReconciliationRun;
import com.reconciler.reconciliation.ReconciliationRunRepository;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * The explanation layer. Everything deterministic has already happened; this only turns a
 * finding into words. A cached OK explanation is reused; a bad or failed call is stored so
 * the UI can show it and offer a retry, and never blocks the dashboard.
 */
@Service
public class LlmService {

	private final DiscrepancyRowRepository discrepancies;
	private final ReconciliationRunRepository runs;
	private final LlmExplanationRepository explanations;
	private final PromptBuilder prompts;
	private final LlmClient client;
	private final LlmProperties properties;
	private final ObjectMapper json;

	public LlmService(DiscrepancyRowRepository discrepancies, ReconciliationRunRepository runs,
			LlmExplanationRepository explanations, PromptBuilder prompts, LlmClient client,
			LlmProperties properties, ObjectMapper json) {
		this.discrepancies = discrepancies;
		this.runs = runs;
		this.explanations = explanations;
		this.prompts = prompts;
		this.client = client;
		this.properties = properties;
		this.json = json;
	}

	public LlmExplanation explainDiscrepancy(UUID discrepancyId, UUID userId) {
		DiscrepancyRow discrepancy = discrepancies.findByIdAndUserId(discrepancyId, userId)
				.orElseThrow(() -> new DiscrepancyNotFoundException(discrepancyId));

		LlmInput input = prompts.forDiscrepancy(discrepancy);
		String hash = hash(input.canonicalJson());

		Optional<LlmExplanation> cached =
				explanations.findFirstByDiscrepancyIdAndInputHashOrderByCreatedAtDesc(discrepancyId, hash);
		if (cached.isPresent() && cached.get().isOk()) {
			return cached.get();
		}

		LlmExplanation.LlmExplanationBuilder base = LlmExplanation.builder()
				.datasetId(discrepancy.getDatasetId())
				.discrepancyId(discrepancyId)
				.userId(userId)
				.scope(ExplanationScope.SINGLE)
				.inputHash(hash);
		return explanations.save(call(input, base));
	}

	public LlmExplanation explainSummary(UUID datasetId, UUID userId) {
		ReconciliationRun run = runs.findByDatasetId(datasetId)
				.filter(r -> r.getUserId().equals(userId))
				.orElseThrow(() -> new DiscrepancyNotFoundException(datasetId));

		List<DiscrepancyRow> top = discrepancies.findTop20ByDatasetIdOrderByAmountImpactDesc(datasetId);
		LlmInput input = prompts.forSummary(run, top);
		String hash = hash(input.canonicalJson());

		Optional<LlmExplanation> cached = explanations
				.findFirstByDatasetIdAndScopeAndInputHashOrderByCreatedAtDesc(datasetId, ExplanationScope.SUMMARY, hash);
		if (cached.isPresent() && cached.get().isOk()) {
			return cached.get();
		}

		LlmExplanation.LlmExplanationBuilder base = LlmExplanation.builder()
				.datasetId(datasetId)
				.userId(userId)
				.scope(ExplanationScope.SUMMARY)
				.inputHash(hash);
		return explanations.save(call(input, base));
	}

	private LlmExplanation call(LlmInput input, LlmExplanation.LlmExplanationBuilder base) {
		base.model(properties.model())
				.temperature(BigDecimal.valueOf(properties.temperature()))
				.promptVersion(PromptBuilder.PROMPT_VERSION);

		if (!properties.configured()) {
			return base.status(ExplanationStatus.FAILED)
					.error("The explanation service is not configured on this deployment.")
					.build();
		}
		try {
			String raw = client.complete(input.messages());
			ExplanationResult parsed = parse(raw);
			return base.status(ExplanationStatus.OK)
					.summary(parsed.summary())
					.likelyCause(parsed.likelyCause())
					.recommendedAction(parsed.recommendedAction())
					.confidence(parsed.confidence().toLowerCase())
					.rawResponse(raw)
					.build();
		} catch (LlmParseException e) {
			return base.status(ExplanationStatus.INVALID).rawResponse(e.rawResponse()).error(e.getMessage()).build();
		} catch (LlmCallException e) {
			return base.status(ExplanationStatus.FAILED).error(e.getMessage()).build();
		}
	}

	private ExplanationResult parse(String raw) {
		ExplanationResult result;
		try {
			result = json.readValue(unfence(raw), ExplanationResult.class);
		} catch (JacksonException e) {
			throw new LlmParseException(raw, "The model's reply was not valid JSON.");
		}
		if (!result.valid()) {
			throw new LlmParseException(raw, "The model's reply was missing one of the required fields.");
		}
		return result;
	}

	// Some models wrap JSON in a ```json ... ``` fence even when asked not to.
	private static String unfence(String raw) {
		String trimmed = raw.strip();
		if (trimmed.startsWith("```")) {
			trimmed = trimmed.replaceAll("^```(?:json)?\\s*", "").replaceAll("\\s*```$", "");
		}
		return trimmed;
	}

	private String hash(String canonicalJson) {
		String material = String.join("|", PromptBuilder.PROMPT_VERSION, properties.model(),
				String.valueOf(properties.temperature()), canonicalJson);
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256").digest(material.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(digest);
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}
}
