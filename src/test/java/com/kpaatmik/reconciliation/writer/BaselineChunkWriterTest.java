
package com.kpaatmik.reconciliation.writer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.springframework.batch.infrastructure.item.Chunk;

import com.kpaatmik.reconciliation.config.ReconciliationFileResolver;
import com.kpaatmik.reconciliation.dto.BaselineCsvRecord;
import com.kpaatmik.reconciliation.dto.BaselineProcessedRecord;
import com.kpaatmik.reconciliation.entity.ReconciliationRun;
import com.kpaatmik.reconciliation.entity.Transaction;
import com.kpaatmik.reconciliation.repository.ReconciliationRunRepository;
import com.kpaatmik.reconciliation.repository.TransactionRepository;

class BaselineChunkWriterTest {

	@TempDir
	Path tempDir;

	private TransactionRepository transactionRepository;
	private ReconciliationRunRepository runRepository;
	private ReconciliationFileResolver fileResolver;
	private BaselineChunkWriter writer;

	private UUID runId;
	private ReconciliationRun run;
	private Path auditFile;

	@BeforeEach
	void setUp() {
		runId = UUID.randomUUID();

		transactionRepository = mock(TransactionRepository.class);
		runRepository = mock(ReconciliationRunRepository.class);
		fileResolver = mock(ReconciliationFileResolver.class);
		run = mock(ReconciliationRun.class);

		auditFile = tempDir.resolve("run-" + runId).resolve("audit.csv");

		writer = new BaselineChunkWriter(transactionRepository, runRepository, fileResolver, runId.toString());

		when(fileResolver.getAuditFile(runId.toString())).thenReturn(auditFile);

		when(runRepository.findById(runId)).thenReturn(Optional.of(run));

		when(run.getBaselineTotalCount()).thenReturn(0L);
		when(run.getBaselineValidCount()).thenReturn(0L);
		when(run.getBaselineInvalidCount()).thenReturn(0L);
	}

	private BaselineCsvRecord source(long lineNumber, String transactionId) {
		return new BaselineCsvRecord(lineNumber, transactionId, "ACC001", "2026-10-01", "100.00", "INR", "CREDIT",
				"Payment");
	}

	private Transaction transaction(String transactionId) {
		Transaction transaction = new Transaction();
		transaction.setTransactionId(transactionId);
		transaction.setAmount(new BigDecimal("100.00"));
		return transaction;
	}

	private BaselineProcessedRecord valid(BaselineCsvRecord source) {
		return new BaselineProcessedRecord(source, transaction(source.transactionId()), null, null);
	}

	private BaselineProcessedRecord invalid(BaselineCsvRecord source) {
		return new BaselineProcessedRecord(source, null, "INVALID_AMOUNT", "Invalid amount");
	}

	private Chunk<BaselineProcessedRecord> chunkOf(BaselineProcessedRecord... records) {
		Chunk<BaselineProcessedRecord> chunk = new Chunk<>();

		for (BaselineProcessedRecord record : records) {
			chunk.add(record);
		}

		return chunk;
	}

	private List<Transaction> captureSavedTransactions() {
		ArgumentCaptor<Iterable<Transaction>> captor = ArgumentCaptor.forClass(Iterable.class);

		verify(transactionRepository).saveAll(captor.capture());

		List<Transaction> saved = new ArrayList<>();
		captor.getValue().forEach(saved::add);
		return saved;
	}

	@Test
	void emptyChunkDoesNothing() throws Exception {
		writer.write(new Chunk<>());

		verifyNoInteractions(runRepository, transactionRepository, fileResolver);
	}

	@Test
	void throwsWhenRunDoesNotExist() {
		when(runRepository.findById(runId)).thenReturn(Optional.empty());

		assertThrows(IllegalStateException.class, () -> writer.write(chunkOf(valid(source(2, "TX001")))));
	}

	@Test
	void savesValidTransactionAndUpdatesCounters() throws Exception {
		writer.write(chunkOf(valid(source(2, "TX001"))));

		List<Transaction> saved = captureSavedTransactions();

		assertEquals(1, saved.size());
		assertEquals("TX001", saved.get(0).getTransactionId());
		assertEquals(runId, saved.get(0).getRunId());

		verify(run).setBaselineTotalCount(1L);
		verify(run).setBaselineValidCount(1L);
		verify(run).setBaselineInvalidCount(0L);
		verify(runRepository).save(run);
	}

	@Test
	void auditsInvalidRecordWithoutSavingTransaction() throws Exception {
		writer.write(chunkOf(invalid(source(2, "TX001"))));

		verify(transactionRepository, never()).saveAll(any(Iterable.class));

		verify(run).setBaselineTotalCount(1L);
		verify(run).setBaselineValidCount(0L);
		verify(run).setBaselineInvalidCount(1L);

		String audit = Files.readString(auditFile);

		assertTrue(audit.contains("source_file"));
		assertTrue(audit.contains("INVALID_AMOUNT"));
		assertTrue(audit.contains("Invalid amount"));
		assertTrue(audit.contains("TX001"));
	}

	@Test
	void auditsDuplicateIdsWithinSameChunk() throws Exception {
		writer.write(chunkOf(valid(source(2, "TX001")), valid(source(3, "TX001"))));

		List<Transaction> saved = captureSavedTransactions();

		assertEquals(1, saved.size());
		assertEquals("TX001", saved.get(0).getTransactionId());

		verify(run).setBaselineTotalCount(2L);
		verify(run).setBaselineValidCount(1L);
		verify(run).setBaselineInvalidCount(1L);

		String audit = Files.readString(auditFile);

		assertTrue(audit.contains("DUPLICATE_TRANSACTION_ID"));
		assertTrue(audit.contains("TX001"));
	}

	@Test
	void auditsTransactionAlreadyPersisted() throws Exception {
		BaselineCsvRecord record = source(2, "TX001");

		when(transactionRepository.findAllByRunIdAndTransactionIdIn(runId, List.of("TX001")))
				.thenReturn(List.of(transaction("TX001")));

		writer.write(chunkOf(valid(record)));

		verify(transactionRepository, never()).saveAll(any(Iterable.class));
		verify(run).setBaselineInvalidCount(1L);

		String audit = Files.readString(auditFile);

		assertTrue(audit.contains("DUPLICATE_TRANSACTION_ID"));
		assertTrue(audit.contains("TX001"));
	}

	@Test
	void writesAuditHeaderOnlyOnceWhenAppending() throws Exception {
		Files.createDirectories(auditFile.getParent());
		Files.writeString(auditFile, "source_file,line_number,transaction_id,error_code,error_message\n");

		writer.write(chunkOf(invalid(source(2, "TX001"))));
		writer.write(chunkOf(invalid(source(3, "TX002"))));

		String audit = Files.readString(auditFile);

		assertEquals(1, audit.split("source_file,line_number", -1).length - 1);

		assertTrue(audit.contains("TX001"));
		assertTrue(audit.contains("TX002"));
	}

	@Test
	void handlesBlankTransactionIdWithoutTreatingItAsDuplicate() throws Exception {
		writer.write(chunkOf(invalid(source(2, " "))));

		String audit = Files.readString(auditFile);

		assertTrue(audit.contains("INVALID_AMOUNT"));
		assertTrue(audit.contains("Invalid amount"));
		assertTrue(!audit.contains("DUPLICATE_TRANSACTION_ID"));
	}

	@Test
	void handlesNullTransactionId() throws Exception {
		writer.write(chunkOf(invalid(source(2, null))));

		String audit = Files.readString(auditFile);

		assertTrue(audit.contains("INVALID_AMOUNT"));
		assertTrue(audit.contains("Invalid amount"));
	}

	@Test
	void doesNotSaveTransactionsWhenAllRecordsAreInvalid() throws Exception {
		writer.write(chunkOf(invalid(source(2, "TX001")), invalid(source(3, "TX002"))));

		verify(transactionRepository, never()).saveAll(any(Iterable.class));

		verify(run).setBaselineTotalCount(2L);
		verify(run).setBaselineValidCount(0L);
		verify(run).setBaselineInvalidCount(2L);

		String audit = Files.readString(auditFile);
		assertTrue(audit.contains("TX001"));
		assertTrue(audit.contains("TX002"));
	}

	@Test
	void rejectsInvalidRunId() {
		assertThrows(IllegalArgumentException.class,
				() -> new BaselineChunkWriter(transactionRepository, runRepository, fileResolver, "not-a-uuid"));
	}
}
