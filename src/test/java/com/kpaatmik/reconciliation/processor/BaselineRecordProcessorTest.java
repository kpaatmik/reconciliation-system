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

import com.kpaatmik.reconciliation.dto.BaselineCsvRecord;

class BaselineRecordProcessorTest {

	private final BaselineRecordProcessor processor = new BaselineRecordProcessor();

	private BaselineCsvRecord row(String id, String account, String date, String amount, String currency, String type) {

		return new BaselineCsvRecord(2, id, account, date, amount, currency, type, "memo");
	}

	@ParameterizedTest
	@CsvSource(value = { "' T1 ', A1, 2026-01-02, -12.30, USD, ' DEBIT ', T1, A1, DEBIT" })
	void acceptsValidRecordIncludingNegativeAmount(String id, String account, String date, String amount,
			String currency, String type, String expectedId, String expectedAccount, String expectedType) {

		var result = processor.process(row(id, account, date, amount, currency, type));

		assertTrue(result.isValid());
		assertEquals(expectedId, result.transaction().getTransactionId());
		assertEquals(expectedAccount, result.transaction().getAccountNumber());
		assertEquals(new BigDecimal(amount), result.transaction().getAmount());
		assertEquals(expectedType, result.transaction().getTransactionType());
		assertNull(result.errorCode());
	}

	@ParameterizedTest
	@CsvSource(value = { "' ', A, 2026-01-01, 1.00, USD, CREDIT, transaction_id",
			"T, null, 2026-01-01, 1.00, USD, CREDIT, account_number", "T, A, '', 1.00, USD, CREDIT, DATE",
			"T, A, 2026-01-01, ' ', USD, CREDIT, amount", "T, A, 2026-01-01, 1.00, null, CREDIT, currency",
			"T, A, 2026-01-01, 1.00, USD, ' ', transaction_type" }, nullValues = "null")
	void rejectsMissingRequiredFields(String id, String account, String date, String amount, String currency,
			String type, String expectedMessage) {

		var result = processor.process(row(id, account, date, amount, currency, type));

		assertFalse(result.isValid());
		assertNull(result.transaction());
		assertEquals("INVALID_BASELINE_RECORD", result.errorCode());
		assertNotNull(result.errorMessage());

		// Assert a field-specific message only if your processor
		// actually includes the field name in its error message.
		if (expectedMessage != null && !expectedMessage.equals("DATE")) {
			assertTrue(result.errorMessage().contains(expectedMessage));
		}
	}

	@ParameterizedTest
	@ValueSource(strings = { "bad-date" })
	void rejectsMalformedDate(String date) {

		assertInvalid(row("T", "A", date, "1.00", "USD", "CREDIT"));
	}

	@ParameterizedTest
	@ValueSource(strings = { "abc", "1.001", "12345678901234.00" })
	void rejectsInvalidAmounts(String amount) {

		assertInvalid(row("T", "A", "2026-01-01", amount, "USD", "CREDIT"));
	}

	@ParameterizedTest
	@ValueSource(strings = { "US", "USDD" })
	void rejectsInvalidCurrencyLength(String currency) {

		assertInvalid(row("T", "A", "2026-01-01", "1.00", currency, "CREDIT"));
	}

	private void assertInvalid(BaselineCsvRecord row) {

		var result = processor.process(row);

		assertFalse(result.isValid());
		assertNull(result.transaction());
		assertEquals("INVALID_BASELINE_RECORD", result.errorCode());
		assertNotNull(result.errorMessage());
	}
}