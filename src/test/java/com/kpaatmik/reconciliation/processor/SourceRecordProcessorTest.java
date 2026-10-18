
package com.kpaatmik.reconciliation.processor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import com.kpaatmik.reconciliation.dto.SourceCsvRecord;

class SourceRecordProcessorTest {

	private final SourceRecordProcessor processor = new SourceRecordProcessor();

	private SourceCsvRecord row(String id, String account, String date, String amount, String currency, String type) {

		return new SourceCsvRecord(3, id, account, date, amount, currency, type, "memo");
	}

	@ParameterizedTest
	@CsvSource({ "' T1 ', A1, 2026-01-02, -12.30, USD, DEBIT, T1, -12.30",
			"T2, A2, 2026-01-03, 100.00, INR, CREDIT, T2, 100.00", "T3, A3, 2026-01-04, 0.00, USD, DEBIT, T3, 0.00" })
	void acceptsValidRecords(String id, String account, String date, String amount, String currency, String type,
			String expectedId, String expectedAmount) {

		var result = processor.process(row(id, account, date, amount, currency, type));

		assertTrue(result.isValid());
		assertEquals(expectedId, result.transactionId());
		assertEquals(new BigDecimal(expectedAmount), result.amount());
		assertNull(result.errorCode());
	}

	@ParameterizedTest
	@CsvSource(value = { "null, A, 2026-01-01, 1.00, USD, CREDIT", "' ', A, 2026-01-01, 1.00, USD, CREDIT",
			"T, null, 2026-01-01, 1.00, USD, CREDIT", "T, ' ', 2026-01-01, 1.00, USD, CREDIT",
			"T, A, null, 1.00, USD, CREDIT", "T, A, 2026-01-01, null, USD, CREDIT",
			"T, A, 2026-01-01, ' ', USD, CREDIT", "T, A, 2026-01-01, 1.00, null, CREDIT",
			"T, A, 2026-01-01, 1.00, USD, null", "T, A, 2026-01-01, 1.00, USD, ' '" }, nullValues = "null")
	void rejectsMissingRequiredFields(String id, String account, String date, String amount, String currency,
			String type) {

		assertInvalid(row(id, account, date, amount, currency, type));
	}

	@ParameterizedTest
	@ValueSource(strings = { "abc", "1.001", "12345678901234.00" })
	void rejectsInvalidAmounts(String amount) {
		assertInvalid(row("T", "A", "2026-01-01", amount, "USD", "CREDIT"));
	}

	@ParameterizedTest
	@ValueSource(strings = { "US", "USDD" })
	void rejectsInvalidCurrencyLengths(String currency) {
		assertInvalid(row("T", "A", "2026-01-01", "1.00", currency, "CREDIT"));
	}

	private void assertInvalid(SourceCsvRecord row) {
		var result = processor.process(row);

		assertFalse(result.isValid());
		assertNull(result.transactionId());
		assertNull(result.amount());
		assertEquals("INVALID_SOURCE_RECORD", result.errorCode());
		assertNotNull(result.errorMessage());
	}
}
