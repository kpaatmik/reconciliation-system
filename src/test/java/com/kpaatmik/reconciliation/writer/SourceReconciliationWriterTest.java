
package com.kpaatmik.reconciliation.writer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.batch.infrastructure.item.Chunk;

import com.kpaatmik.reconciliation.config.ReconciliationFileResolver;
import com.kpaatmik.reconciliation.dto.SourceCsvRecord;
import com.kpaatmik.reconciliation.dto.SourceProcessedRecord;
import com.kpaatmik.reconciliation.entity.ReconciliationRun;
import com.kpaatmik.reconciliation.entity.Transaction;
import com.kpaatmik.reconciliation.repository.ReconciliationRunRepository;
import com.kpaatmik.reconciliation.repository.TransactionRepository;

class SourceReconciliationWriterTest {

	@TempDir
	Path tempDir;

	private TransactionRepository transactionRepository;
	private ReconciliationRunRepository runRepository;
	private ReconciliationFileResolver fileResolver;

	private SourceReconciliationWriter writer;
	private ReconciliationRun run;

	private UUID runId;
	private Path auditFile;

	@BeforeEach
	void setUp() {
		runId = UUID.randomUUID();

		transactionRepository = mock(TransactionRepository.class);
		runRepository = mock(ReconciliationRunRepository.class);
		fileResolver = mock(ReconciliationFileResolver.class);
		run = mock(ReconciliationRun.class);

		auditFile = tempDir.resolve("run-" + runId).resolve("audit.csv");

		writer = new SourceReconciliationWriter(transactionRepository, runRepository, fileResolver, runId.toString());

		when(runRepository.findById(runId)).thenReturn(Optional.of(run));

		when(fileResolver.getAuditFile(runId.toString())).thenReturn(auditFile);

		when(run.getSourceTotalCount()).thenReturn(0L);
		when(run.getSourceValidCount()).thenReturn(0L);
		when(run.getSourceInvalidCount()).thenReturn(0L);
		when(run.getMatchedCount()).thenReturn(0L);
		when(run.getAmountMismatchCount()).thenReturn(0L);
		when(run.getMissingInDatabaseCount()).thenReturn(0L);
	}

	private SourceCsvRecord source(long lineNumber, String transactionId, String amount) {

		return new SourceCsvRecord(lineNumber, transactionId, "ACC001", "2026-10-01", amount, "INR", "CREDIT",
				"Payment");
	}

	private Transaction transaction(String transactionId, String amount) {

		Transaction transaction = new Transaction();
		transaction.setRunId(runId);
		transaction.setTransactionId(transactionId);
		transaction.setAmount(new BigDecimal(amount));

		return transaction;
	}

	private SourceProcessedRecord validRecord(SourceCsvRecord source) {

		return new SourceProcessedRecord(source, source.transactionId(), new BigDecimal(source.amount()), null, null);
	}

	private SourceProcessedRecord invalidRecord(SourceCsvRecord source) {

		return new SourceProcessedRecord(source, null, null, "INVALID_AMOUNT", "Invalid amount");
	}

	private Chunk<SourceProcessedRecord> chunkOf(SourceProcessedRecord... records) {

		Chunk<SourceProcessedRecord> chunk = new Chunk<>();

		for (SourceProcessedRecord record : records) {
			chunk.add(record);
		}

		return chunk;
	}

	@Test
	void skipsBaselineLookupWhenCandidateIdsAreEmpty() throws Exception {

		SourceCsvRecord csv = source(2, "TX001", "invalid");

		writer.write(chunkOf(invalidRecord(csv)));

		verify(transactionRepository, never()).findAllByRunIdAndTransactionIdIn(runId, List.of("TX001"));
	}

	@Test
	void fetchesBaselineWhenCandidateIdsArePresent() throws Exception {

		SourceCsvRecord csv = source(2, "TX001", "100.00");
		Transaction baseline = transaction("TX001", "100.00");

		when(transactionRepository.findAllByRunIdAndTransactionIdIn(runId, List.of("TX001")))
				.thenReturn(List.of(baseline));

		writer.write(chunkOf(validRecord(csv)));

		verify(transactionRepository).findAllByRunIdAndTransactionIdIn(runId, List.of("TX001"));

		verify(run).setMatchedCount(1L);
	}

	@Test
	void writesHeaderWhenAuditFileDoesNotExist() throws Exception {

		SourceCsvRecord csv = source(2, "TX001", "invalid");

		writer.write(chunkOf(invalidRecord(csv)));

		String content = Files.readString(auditFile);

		assertTrue(content.contains("source_file"));
		assertTrue(content.contains("line_number"));
		assertTrue(content.contains("transaction_id"));
		assertTrue(content.contains("error_code"));
		assertTrue(content.contains("error_message"));
		assertTrue(content.contains("INVALID"));
		assertTrue(content.contains("TX001"));
	}

	@Test
	void doesNotWriteDuplicateHeaderWhenAuditFileAlreadyExists() throws Exception {

		Files.createDirectories(auditFile.getParent());

		Files.writeString(auditFile, "source_file,line_number,transaction_id,error_code,error_message\n"
				+ "source.csv,1,OLD_TX,INVALID,Old error\n");

		SourceCsvRecord csv = source(2, "TX002", "invalid");

		writer.write(chunkOf(invalidRecord(csv)));

		String content = Files.readString(auditFile);

		assertEquals(1, content.split("source_file,line_number,transaction_id", -1).length - 1);

		assertTrue(content.contains("OLD_TX"));
		assertTrue(content.contains("TX002"));
	}

	@Test
	void doesNotAccessAuditFileWhenThereAreNoAuditEntries() throws Exception {

		SourceCsvRecord csv = source(2, "TX001", "100.00");
		Transaction baseline = transaction("TX001", "100.00");

		when(transactionRepository.findAllByRunIdAndTransactionIdIn(runId, List.of("TX001")))
				.thenReturn(List.of(baseline));

		writer.write(chunkOf(validRecord(csv)));

		verify(fileResolver, never()).getAuditFile(anyString());
	}

	@Test
	void writesHeaderWhenAuditFileExistsButIsEmpty() throws Exception {

		Files.createDirectories(auditFile.getParent());
		Files.createFile(auditFile);

		SourceCsvRecord csv = source(2, "TX001", "invalid");

		writer.write(chunkOf(invalidRecord(csv)));

		String content = Files.readString(auditFile);

		assertTrue(content.startsWith("source_file,line_number,transaction_id,error_code,error_message"));

		assertTrue(content.contains("INVALID"));
		assertTrue(content.contains("TX001"));
	}
}
