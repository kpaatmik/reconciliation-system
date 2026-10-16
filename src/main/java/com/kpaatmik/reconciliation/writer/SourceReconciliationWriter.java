package com.kpaatmik.reconciliation.writer;

import java.io.BufferedWriter;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.springframework.batch.infrastructure.item.Chunk;
import org.springframework.batch.infrastructure.item.ItemWriter;

import com.kpaatmik.reconciliation.config.ReconciliationFileResolver;
import com.kpaatmik.reconciliation.dto.SourceCsvRecord;
import com.kpaatmik.reconciliation.dto.SourceProcessedRecord;
import com.kpaatmik.reconciliation.entity.ReconciliationRun;
import com.kpaatmik.reconciliation.entity.Transaction;
import com.kpaatmik.reconciliation.repository.ReconciliationRunRepository;
import com.kpaatmik.reconciliation.repository.TransactionRepository;

public class SourceReconciliationWriter implements ItemWriter<SourceProcessedRecord> {

	private final TransactionRepository transactionRepository;
	private final ReconciliationRunRepository runRepository;
	private final ReconciliationFileResolver fileResolver;
	private final UUID runId;

	public SourceReconciliationWriter(TransactionRepository transactionRepository,
			ReconciliationRunRepository runRepository, ReconciliationFileResolver fileResolver, String runId) {

		this.transactionRepository = transactionRepository;
		this.runRepository = runRepository;
		this.fileResolver = fileResolver;
		this.runId = UUID.fromString(runId);
	}

	@Override
	public void write(Chunk<? extends SourceProcessedRecord> chunk) throws Exception {

		if (chunk.isEmpty()) {
			return;
		}

		ReconciliationRun run = runRepository.findById(runId)
				.orElseThrow(() -> new IllegalStateException("Reconciliation run not found: " + runId));

		long totalCount = 0;
		long validCount = 0;
		long invalidCount = 0;
		long matchedCount = 0;
		long mismatchCount = 0;
		long missingCount = 0;

		List<AuditEntry> auditEntries = new ArrayList<>();

		List<String> candidateIds = chunk.getItems().stream().filter(SourceProcessedRecord::isValid)
				.map(SourceProcessedRecord::transactionId).distinct().toList();

		Map<String, Transaction> baselineById = new HashMap<>();

		if (!candidateIds.isEmpty()) {
			transactionRepository.findAllByRunIdAndTransactionIdIn(runId, candidateIds)
					.forEach(transaction -> baselineById.put(transaction.getTransactionId(), transaction));
		}

		for (SourceProcessedRecord item : chunk) {
			totalCount++;

			SourceCsvRecord source = item.source();

			if (!item.isValid()) {
				invalidCount++;

				auditEntries.add(new AuditEntry(source, "INVALID", item.errorCode() + ": " + item.errorMessage()));

				continue;
			}

			validCount++;

			Transaction baseline = baselineById.get(item.transactionId());

			if (baseline == null) {
				missingCount++;

				auditEntries.add(
						new AuditEntry(source, "MISSING_IN_DATABASE", "Transaction ID does not exist in the baseline"));

				continue;
			}

			BigDecimal baselineAmount = baseline.getAmount();
			BigDecimal sourceAmount = item.amount();

			if (baselineAmount.compareTo(sourceAmount) == 0) {
				// Matched records are counted but not audited.
				matchedCount++;
			} else {
				mismatchCount++;

				auditEntries.add(new AuditEntry(source, "AMOUNT_MISMATCH",
						"Baseline amount=" + baselineAmount + ", source amount=" + sourceAmount));
			}
		}

		/*
		 * Persist aggregate counters once per chunk. This step does not modify baseline
		 * transactions.
		 */
		run.setSourceTotalCount(run.getSourceTotalCount() + totalCount);

		run.setSourceValidCount(run.getSourceValidCount() + validCount);

		run.setSourceInvalidCount(run.getSourceInvalidCount() + invalidCount);

		run.setMatchedCount(run.getMatchedCount() + matchedCount);

		run.setAmountMismatchCount(run.getAmountMismatchCount() + mismatchCount);

		run.setMissingInDatabaseCount(run.getMissingInDatabaseCount() + missingCount);

		runRepository.save(run);

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
				printer.printRecord("source.csv", entry.source().lineNumber(), entry.source().transactionId(),
						entry.errorCode(), entry.errorMessage());
			}
		}
	}

	private record AuditEntry(SourceCsvRecord source, String errorCode, String errorMessage) {
	}
}
