
package com.kpaatmik.reconciliation.reader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.batch.infrastructure.item.ExecutionContext;
import org.springframework.batch.infrastructure.item.file.FlatFileItemReader;
import org.springframework.batch.infrastructure.item.file.FlatFileParseException;

import com.kpaatmik.reconciliation.config.ReconciliationFileResolver;
import com.kpaatmik.reconciliation.dto.BaselineCsvRecord;

class BaselineReaderConfigTest {

	@TempDir
	Path tempDir;

	private ReconciliationFileResolver fileResolver;
	private BaselineReaderConfig readerConfig;
	private Path baselineFile;

	@BeforeEach
	void setUp() throws Exception {
		fileResolver = mock(ReconciliationFileResolver.class);
		readerConfig = new BaselineReaderConfig();

		baselineFile = tempDir.resolve("baseline.csv");

		when(fileResolver.getBaselineFile()).thenReturn(baselineFile);
	}

	private FlatFileItemReader<BaselineCsvRecord> createReader() {
		return readerConfig.baselineReader(fileResolver);
	}

	private void writeCsv(String content) throws Exception {
		Files.writeString(baselineFile, content);
	}

	private String header() {
		return "transaction_id,account_number,transaction_date,amount,currency,transaction_type,description\n";
	}

	@Test
	void createsReaderSuccessfully() throws Exception {
		writeCsv(header() + "TX001,ACC001,2026-10-01,100.00,INR,CREDIT,Payment\n");

		FlatFileItemReader<BaselineCsvRecord> reader = createReader();

		assertNotNull(reader);
	}

	@Test
	void skipsHeaderAndMapsAllFields() throws Exception {
		writeCsv(header() + "TX001,ACC001,2026-10-01,100.00,INR,CREDIT,Payment\n");

		FlatFileItemReader<BaselineCsvRecord> reader = createReader();
		reader.open(new ExecutionContext());

		try {
			BaselineCsvRecord record = reader.read();

			assertNotNull(record);
			assertEquals(2L, record.lineNumber());
			assertEquals("TX001", record.transactionId());
			assertEquals("ACC001", record.accountNumber());
			assertEquals("2026-10-01", record.transactionDate());
			assertEquals("100.00", record.amount());
			assertEquals("INR", record.currency());
			assertEquals("CREDIT", record.transactionType());
			assertEquals("Payment", record.description());

			assertNull(reader.read());
		} finally {
			reader.close();
		}
	}

	@Test
	void readsQuotedDescriptionContainingComma() throws Exception {
		writeCsv(header() + "TX001,ACC001,2026-10-01,100.00,INR,CREDIT,\"Online, card payment\"\n");

		FlatFileItemReader<BaselineCsvRecord> reader = createReader();
		reader.open(new ExecutionContext());

		try {
			BaselineCsvRecord record = reader.read();

			assertNotNull(record);
			assertEquals("Online, card payment", record.description());
		} finally {
			reader.close();
		}
	}

	@Test
	void readsMultipleRecordsInOrder() throws Exception {
		writeCsv(header() + "TX001,ACC001,2026-10-01,100.00,INR,CREDIT,First\n"
				+ "TX002,ACC002,2026-10-02,200.00,INR,DEBIT,Second\n");

		FlatFileItemReader<BaselineCsvRecord> reader = createReader();
		reader.open(new ExecutionContext());

		try {
			BaselineCsvRecord first = reader.read();
			BaselineCsvRecord second = reader.read();

			assertEquals("TX001", first.transactionId());
			assertEquals("First", first.description());
			assertEquals(2L, first.lineNumber());

			assertEquals("TX002", second.transactionId());
			assertEquals("Second", second.description());
			assertEquals(3L, second.lineNumber());

			assertNull(reader.read());
		} finally {
			reader.close();
		}
	}

	@Test
	void throwsWhenBaselineFileDoesNotExist() {
		when(fileResolver.getBaselineFile()).thenReturn(tempDir.resolve("missing.csv"));

		FlatFileItemReader<BaselineCsvRecord> reader = createReader();

		assertThrows(Exception.class, () -> reader.open(new ExecutionContext()));
	}

	@Test
	void throwsFlatFileParseExceptionForMalformedRow() throws Exception {
		writeCsv(header() + "TX001,ACC001,2026-10-01,100.00,INR,CREDIT\n");

		FlatFileItemReader<BaselineCsvRecord> reader = createReader();
		reader.open(new ExecutionContext());

		try {
			assertThrows(Exception.class, reader::read);
		} finally {
			reader.close();
		}
	}

	@Test
	void wrapsLineMappingFailureWithFlatFileParseException() throws Exception {
		writeCsv(header() + "TX001,ACC001,2026-10-01,100.00,INR,CREDIT,Payment,EXTRA\n");

		FlatFileItemReader<BaselineCsvRecord> reader = createReader();
		reader.open(new ExecutionContext());

		try {
			Exception exception = assertThrows(Exception.class, reader::read);

			assertTrueExceptionChainContains(exception, FlatFileParseException.class);
		} finally {
			reader.close();
		}
	}

	private void assertTrueExceptionChainContains(Throwable throwable, Class<? extends Throwable> expectedType) {

		Throwable current = throwable;

		while (current != null) {
			if (expectedType.isInstance(current)) {
				return;
			}
			current = current.getCause();
		}

		throw new AssertionError("Expected exception chain to contain " + expectedType.getSimpleName(), throwable);
	}
}
