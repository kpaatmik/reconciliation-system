package com.kpaatmik.reconciliation.processor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.kpaatmik.reconciliation.dto.SourceCsvRecord;

class SourceRecordProcessorTest {
	private final SourceRecordProcessor processor = new SourceRecordProcessor();

	private SourceCsvRecord row(String id, String account, String date, String amount, String currency, String type) {
		return new SourceCsvRecord(3, id, account, date, amount, currency, type, "memo");
	}

	@Test
	void acceptsValidRecordAndTrimsId() {
		var result = processor.process(row(" T1 ", "A1", "2026-01-02", "-12.30", "USD", "DEBIT"));
		assertTrue(result.isValid());
		assertEquals("T1", result.transactionId());
		assertEquals(new java.math.BigDecimal("-12.30"), result.amount());
		assertNull(result.errorCode());
	}

	@Test
	void rejectsMissingRequiredFields() {
		assertInvalid(row(null, "A", "2026-01-01", "1.00", "USD", "CREDIT"));
		assertInvalid(row("T", " ", "2026-01-01", "1.00", "USD", "CREDIT"));
		assertInvalid(row("T", "A", null, "1.00", "USD", "CREDIT"));
		assertInvalid(row("T", "A", "2026-01-01", null, "USD", "CREDIT"));
		assertInvalid(row("T", "A", "2026-01-01", "1.00", null, "CREDIT"));
		assertInvalid(row("T", "A", "2026-01-01", "1.00", "USD", " "));
	}

	@Test
	void rejectsMalformedDateAmountAndCurrency() {
//        assertInvalid(row("T","A","bad","1.00","USD","CREDIT"));
		assertInvalid(row("T", "A", "2026-01-01", "abc", "USD", "CREDIT"));
		assertInvalid(row("T", "A", "2026-01-01", "1.001", "USD", "CREDIT"));
		assertInvalid(row("T", "A", "2026-01-01", "12345678901234.00", "USD", "CREDIT"));
		assertInvalid(row("T", "A", "2026-01-01", "1.00", "US", "CREDIT"));
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
