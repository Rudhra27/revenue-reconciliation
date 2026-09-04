package com.reconciler.llm;

import static org.assertj.core.api.Assertions.assertThat;

import com.reconciler.llm.PromptBuilder.LlmInput;
import com.reconciler.reconciliation.DiscrepancyRow;
import com.reconciler.reconciliation.DiscrepancyType;
import com.reconciler.reconciliation.Direction;
import com.reconciler.reconciliation.Severity;
import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class PromptBuilderTest {

	private final PromptBuilder builder = new PromptBuilder(JsonMapper.builder().build());

	@Test
	void theContextCarriesTheClassificationAndTheNumbers() {
		DiscrepancyRow row = DiscrepancyRow.builder()
				.type(DiscrepancyType.DUPLICATE_PAYMENT)
				.severity(Severity.HIGH)
				.direction(Direction.OWED_BY_US)
				.orderId("ORD-1501")
				.currency("USD")
				.amountImpact(new BigDecimal("119.84"))
				.detail(Map.of("settledCharges", "239.68", "orderNet", "119.84"))
				.build();

		LlmInput input = builder.forDiscrepancy(row);

		assertThat(input.canonicalJson())
				.contains("DUPLICATE_PAYMENT")
				.contains("239.68")
				.contains("ORD-1501");
		assertThat(input.messages()).hasSize(2);
		assertThat(input.messages().get(0).role()).isEqualTo("system");
		assertThat(input.messages().get(1).role()).isEqualTo("user");
		assertThat(input.messages().get(1).content()).contains(input.canonicalJson());
	}

	@Test
	void aDiscrepancyWithNoOrderIdStillBuilds() {
		DiscrepancyRow orphan = DiscrepancyRow.builder()
				.type(DiscrepancyType.ORDER_NOT_FOUND)
				.severity(Severity.HIGH)
				.direction(Direction.INVESTIGATION)
				.currency("USD")
				.amountImpact(new BigDecimal("79.51"))
				.detail(Map.of("transactionRef", "TXN700161", "amount", "79.51"))
				.build();

		LlmInput input = builder.forDiscrepancy(orphan);

		assertThat(input.canonicalJson()).contains("TXN700161");
	}

	@Test
	void differentDiscrepanciesProduceDifferentContext() {
		DiscrepancyRow a = discrepancy(UUID.randomUUID(), "100.00");
		DiscrepancyRow b = discrepancy(UUID.randomUUID(), "250.00");

		assertThat(builder.forDiscrepancy(a).canonicalJson())
				.isNotEqualTo(builder.forDiscrepancy(b).canonicalJson());
	}

	private static DiscrepancyRow discrepancy(UUID id, String impact) {
		return DiscrepancyRow.builder()
				.type(DiscrepancyType.AMOUNT_MISMATCH)
				.subtype("OVER")
				.severity(Severity.MEDIUM)
				.direction(Direction.OWED_BY_US)
				.orderId("ORD-1")
				.currency("USD")
				.amountImpact(new BigDecimal(impact))
				.detail(Map.of("difference", impact))
				.build();
	}
}
