package com.reconciler.ingest;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PushbackReader;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

/**
 * Shared CSV plumbing: opens the file, checks the header is exactly what we expect,
 * then hands each record to the subclass. A record the subclass rejects becomes a
 * RowError; every other kind of problem fails the whole file.
 */
abstract class CsvRowParser<T> {

	private static final int BOM = 0xFEFF;

	abstract List<String> expectedHeader();

	abstract T toRow(CSVRecord record, UUID datasetId, UUID userId);

	ParseResult<T> parse(InputStream csv, UUID datasetId, UUID userId) {
		List<T> rows = new ArrayList<>();
		List<RowError> errors = new ArrayList<>();
		int dataRows = 0;

		try (CSVParser parser = open(csv)) {
			requireExpectedHeader(parser.getHeaderNames());
			for (CSVRecord record : parser) {
				dataRows++;
				int line = (int) record.getRecordNumber() + 1; // +1: the header is line 1
				try {
					if (!record.isConsistent()) {
						throw new RowRejectedException(
								"expected " + expectedHeader().size() + " columns, found " + record.size());
					}
					rows.add(toRow(record, datasetId, userId));
				} catch (RowRejectedException e) {
					errors.add(new RowError(line, e.getMessage()));
				}
			}
		} catch (InvalidCsvException e) {
			throw e;
		} catch (IOException | IllegalArgumentException | UncheckedIOException e) {
			throw new InvalidCsvException("Could not read the file as CSV: " + e.getMessage());
		}

		return new ParseResult<>(rows, errors, dataRows);
	}

	private CSVParser open(InputStream csv) throws IOException {
		Reader reader = skipByteOrderMark(new InputStreamReader(csv, StandardCharsets.UTF_8));
		return CSVFormat.DEFAULT.builder()
				.setHeader()
				.setSkipHeaderRecord(true)
				.setIgnoreEmptyLines(true)
				.get()
				.parse(reader);
	}

	private void requireExpectedHeader(List<String> actual) {
		List<String> normalised = actual.stream()
				.map(header -> header == null ? "" : header.trim().toLowerCase(Locale.ROOT))
				.toList();
		if (!normalised.equals(expectedHeader())) {
			throw new InvalidCsvException("Unexpected columns. Expected: "
					+ String.join(", ", expectedHeader()) + ". Found: " + String.join(", ", actual));
		}
	}

	// A UTF-8 BOM would otherwise become part of the first column name.
	private static Reader skipByteOrderMark(Reader reader) throws IOException {
		PushbackReader pushback = new PushbackReader(reader, 1);
		int first = pushback.read();
		if (first != -1 && first != BOM) {
			pushback.unread(first);
		}
		return pushback;
	}
}
