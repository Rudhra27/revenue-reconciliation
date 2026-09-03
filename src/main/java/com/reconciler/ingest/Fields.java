package com.reconciler.ingest;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Field-level normalisation shared by both parsers. The two files use different date
 * formats, so each gets its own parser here.
 */
final class Fields {

	// orders.csv: 2025-04-13 00:00:00
	private static final DateTimeFormatter ORDER_DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
	// payments.csv: 13/04/2025 00:30
	private static final DateTimeFormatter PAYMENT_DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

	private Fields() {
	}

	static String trimToNull(String value) {
		if (value == null) {
			return null;
		}
		String trimmed = value.trim();
		return trimmed.isEmpty() ? null : trimmed;
	}

	/** Match keys (order_id, order_reference) are compared without regard to case or surrounding space. */
	static String key(String value) {
		String trimmed = trimToNull(value);
		return trimmed == null ? null : trimmed.toUpperCase(Locale.ROOT);
	}

	static String lower(String value) {
		String trimmed = trimToNull(value);
		return trimmed == null ? null : trimmed.toLowerCase(Locale.ROOT);
	}

	/** Money to 2 dp, or null if blank. Throws NumberFormatException on junk. */
	static BigDecimal money(String value) {
		String trimmed = trimToNull(value);
		return trimmed == null ? null : new BigDecimal(trimmed).setScale(2, RoundingMode.HALF_UP);
	}

	static Instant orderDate(String value) {
		return parseDate(value, ORDER_DATE);
	}

	static Instant paymentDate(String value) {
		return parseDate(value, PAYMENT_DATE);
	}

	// Both files hold wall-clock timestamps with no zone; we read them as UTC.
	private static Instant parseDate(String value, DateTimeFormatter format) {
		String trimmed = trimToNull(value);
		if (trimmed == null) {
			return null;
		}
		return LocalDateTime.parse(trimmed, format).toInstant(ZoneOffset.UTC);
	}
}
