
package com.kpaatmik.reconciliation.reader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.batch.infrastructure.item.file.FlatFileItemReader;

import com.kpaatmik.reconciliation.config.ReconciliationFileResolver;
import com.kpaatmik.reconciliation.dto.SourceCsvRecord;

class SourceReaderConfigTest {

	@TempDir
	Path tempDir;

	private ReconciliationFileResolver fileResolver;
	private SourceReaderConfig sourceReaderConfig;
	private Path sourceFile;

	@BeforeEach
	void setUp() throws Exception {
		fileResolver = mock(ReconciliationFileResolver.class);
		sourceReaderConfig = new SourceReaderConfig();

		sourceFile = tempDir.resolve("source.csv");

		when(fileResolver.getSourceFile()).thenReturn(sourceFile);
	}

	private FlatFileItemReader<SourceCsvRecord> createReader() throws Exception {
		return sourceReaderConfig.sourceReader(fileResolver);
	}

	private void writeCsv(String content) throws Exception {
		Files.writeString(sourceFile, content);
	}

	@Test
	void createsReaderSuccessfully() throws Exception {
		writeCsv("transaction_id,account_number,transaction_date,amount,currency,transaction_type,description\n"
				+ "TX001,ACC001,2026-10-01,100.00,INR,CREDIT,Payment\n");

		FlatFileItemReader<SourceCsvRecord> reader = createReader();

		assertNotNull(reader);
		assertEquals("sourceReader", reader.getName());
	}

	@Test
	void skipsHeaderAndReadsSourceRecord() throws Exception {
		writeCsv("transaction_id,account_number,transaction_date,amount,currency,transaction_type,description\n"
				+ "TX001,ACC001,2026-10-01,100.00,INR,CREDIT,Payment\n");

		FlatFileItemReader<SourceCsvRecord> reader = createReader();
		reader.open(new org.springframework.batch.infrastructure.item.ExecutionContext());

		try {
			SourceCsvRecord record = reader.read();

			assertNotNull(record);
			assertEquals(2L, record.lineNumber());
			assertEquals("TX001", record.transactionId());
			assertEquals("ACC001", record.accountNumber());
			assertEquals("2026-10-01", record.transactionDate());
			assertEquals("100.00", record.amount());
			assertEquals("INR", record.currency());
			assertEquals("CREDIT", record.transactionType());
			assertEquals("Payment", record.description());

			assertEquals(null, reader.read());
		} finally {
			reader.close();
		}
	}

	@Test
	void readsQuotedFieldsContainingCommas() throws Exception {
		writeCsv("transaction_id,account_number,transaction_date,amount,currency,transaction_type,description\n"
				+ "TX001,ACC001,2026-10-01,100.00,INR,CREDIT,\"Payment, online\"\n");

		FlatFileItemReader<SourceCsvRecord> reader = createReader();
		reader.open(new org.springframework.batch.infrastructure.item.ExecutionContext());

		try {
			SourceCsvRecord record = reader.read();

			assertNotNull(record);
			assertEquals("Payment, online", record.description());
		} finally {
			reader.close();
		}
	}

	@Test
	void wrapsMalformedRowWithLineNumber() throws Exception {
		writeCsv("transaction_id,account_number,transaction_date,amount,currency,transaction_type,description\n"
				+ "TX001,ACC001,2026-10-01,100.00,INR,CREDIT\n");

		FlatFileItemReader<SourceCsvRecord> reader = createReader();
		reader.open(new org.springframework.batch.infrastructure.item.ExecutionContext());

		try {
			assertThrows(Exception.class, reader::read);
		} finally {
			reader.close();
		}
	}

	@Test
	void throwsWhenSourceFileDoesNotExist() {
		when(fileResolver.getSourceFile()).thenReturn(tempDir.resolve("missing.csv"));

		FlatFileItemReader<SourceCsvRecord> reader = sourceReaderConfig.sourceReader(fileResolver);

		assertThrows(Exception.class,
				() -> reader.open(new org.springframework.batch.infrastructure.item.ExecutionContext()));
	}

	@Test
	void readsMultipleRecordsInOrder() throws Exception {
		writeCsv("transaction_id,account_number,transaction_date,amount,currency,transaction_type,description\n"
				+ "TX001,ACC001,2026-10-01,100.00,INR,CREDIT,First\n"
				+ "TX002,ACC002,2026-10-02,200.00,INR,DEBIT,Second\n");

		FlatFileItemReader<SourceCsvRecord> reader = createReader();
		reader.open(new org.springframework.batch.infrastructure.item.ExecutionContext());

		try {
			SourceCsvRecord first = reader.read();
			SourceCsvRecord second = reader.read();

			assertEquals("TX001", first.transactionId());
			assertEquals("First", first.description());
			assertEquals("TX002", second.transactionId());
			assertEquals("Second", second.description());
			assertEquals(2L, first.lineNumber());
			assertEquals(3L, second.lineNumber());
		} finally {
			reader.close();
		}
	}
}
