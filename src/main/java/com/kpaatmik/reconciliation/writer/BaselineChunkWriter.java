package com.kpaatmik.reconciliation.writer;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.springframework.batch.infrastructure.item.Chunk;
import org.springframework.batch.infrastructure.item.ItemWriter;

import com.kpaatmik.reconciliation.config.ReconciliationFileResolver;
import com.kpaatmik.reconciliation.dto.BaselineCsvRecord;
import com.kpaatmik.reconciliation.dto.BaselineProcessedRecord;
import com.kpaatmik.reconciliation.entity.ReconciliationRun;
import com.kpaatmik.reconciliation.entity.Transaction;
import com.kpaatmik.reconciliation.repository.ReconciliationRunRepository;
import com.kpaatmik.reconciliation.repository.TransactionRepository;

public class BaselineChunkWriter implements ItemWriter<BaselineProcessedRecord> {

	private final TransactionRepository transactionRepository;
	private final ReconciliationRunRepository runRepository;
	private final ReconciliationFileResolver fileResolver;
	private final UUID runId;

	public BaselineChunkWriter(TransactionRepository transactionRepository, ReconciliationRunRepository runRepository,
			ReconciliationFileResolver fileResolver, String runId) {

		this.transactionRepository = transactionRepository;
		this.runRepository = runRepository;
		this.fileResolver = fileResolver;
		this.runId = UUID.fromString(runId);
	}

	@Override
	public void write(Chunk<? extends BaselineProcessedRecord> chunk) throws Exception {

		if (chunk.isEmpty()) {
			return;
		}

		ReconciliationRun run = runRepository.findById(runId)
				.orElseThrow(() -> new IllegalStateException("Reconciliation run not found: " + runId));

		long totalCount = 0;
		long validCount = 0;
		long invalidCount = 0;

		List<Transaction> transactionsToInsert = new ArrayList<>();
		List<AuditEntry> auditEntries = new ArrayList<>();
		Set<String> seenInChunk = new HashSet<>();

		// Find IDs already persisted by previous chunks.
		List<String> candidateIds = chunk.getItems().stream().filter(BaselineProcessedRecord::isValid)
				.map(item -> item.transaction().getTransactionId()).distinct().toList();

		Set<String> existingIds = candidateIds.isEmpty() ? Set.of()
				: new HashSet<>(transactionRepository.findAllByRunIdAndTransactionIdIn(runId, candidateIds).stream()
						.map(Transaction::getTransactionId).toList());

		for (BaselineProcessedRecord item : chunk) {
			totalCount++;

			BaselineCsvRecord source = item.source();

			String transactionId = source.transactionId() == null ? "" : source.transactionId().trim();

			/*
			 * Detect duplicate IDs within this chunk and IDs already imported by previous
			 * chunks.
			 *
			 * Only valid records are eligible for insertion.
			 */
			boolean duplicate = !transactionId.isBlank()
					&& (existingIds.contains(transactionId) || !seenInChunk.add(transactionId));

			if (duplicate) {
				invalidCount++;

				auditEntries.add(new AuditEntry(source, "DUPLICATE_TRANSACTION_ID",
						"Transaction ID already occurred in this baseline file"));

				continue;
			}

			if (!item.isValid()) {
				invalidCount++;

				auditEntries.add(new AuditEntry(source, item.errorCode(), item.errorMessage()));

				continue;
			}

			Transaction transaction = item.transaction();
			transaction.setRunId(runId);

			transactionsToInsert.add(transaction);
			validCount++;
		}

		/*
		 * Persist transactions and counters in the current Spring Batch chunk
		 * transaction.
		 */
		if (!transactionsToInsert.isEmpty()) {
			transactionRepository.saveAll(transactionsToInsert);
		}

		run.setBaselineTotalCount(run.getBaselineTotalCount() + totalCount);

		run.setBaselineValidCount(run.getBaselineValidCount() + validCount);

		run.setBaselineInvalidCount(run.getBaselineInvalidCount() + invalidCount);

		runRepository.save(run);

		/*
		 * Temporary implementation: the file is not transactionally coordinated with
		 * PostgreSQL. See the restartability note below.
		 */
		appendAuditEntries(auditEntries);
	}

	private void appendAuditEntries(List<AuditEntry> entries) throws IOException {

		if (entries.isEmpty()) {
			return;
		}

		Path auditFile = fileResolver.getAuditFile(runId.toString());

		Files.createDirectories(auditFile.getParent());

		boolean writeHeader = !Files.exists(auditFile) || Files.size(auditFile) == 0;

		try (BufferedWriter writer = Files.newBufferedWriter(auditFile, StandardCharsets.UTF_8,
				StandardOpenOption.CREATE, StandardOpenOption.APPEND);
				CSVPrinter printer = new CSVPrinter(writer, CSVFormat.DEFAULT.builder().get())) {

			if (writeHeader) {
				printer.printRecord("source_file", "line_number", "transaction_id", "error_code", "error_message");
			}

			for (AuditEntry entry : entries) {
				printer.printRecord("baseline.csv", entry.source().lineNumber(), entry.source().transactionId(),
						entry.errorCode(), entry.errorMessage());
			}
		}
	}

	private record AuditEntry(BaselineCsvRecord source, String errorCode, String errorMessage) {
	}
}
