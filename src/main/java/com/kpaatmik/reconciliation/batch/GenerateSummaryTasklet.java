package com.kpaatmik.reconciliation.batch;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.StepContribution;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.kpaatmik.reconciliation.config.ReconciliationFileResolver;
import com.kpaatmik.reconciliation.entity.ReconciliationRun;
import com.kpaatmik.reconciliation.entity.ReconciliationStatus;
import com.kpaatmik.reconciliation.repository.ReconciliationRunRepository;

import lombok.RequiredArgsConstructor;

@Component
@StepScope
@RequiredArgsConstructor
public class GenerateSummaryTasklet implements Tasklet {

	private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

	private final ReconciliationRunRepository runRepository;
	private final ReconciliationFileResolver fileResolver;
	@Value("#{jobParameters['runId']}")
	private final UUID runId;

	@Override
	public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) throws Exception {

		ReconciliationRun run = runRepository.findById(runId)
				.orElseThrow(() -> new IllegalStateException("Reconciliation run not found: " + runId));

		LocalDateTime completedAt = LocalDateTime.now();

		LocalDateTime startedAt = run.getStartedAt() != null ? run.getStartedAt() : run.getCreatedAt();

		ReconciliationStatus finalStatus = hasErrors(run) ? ReconciliationStatus.COMPLETED_WITH_ERRORS
				: ReconciliationStatus.COMPLETED;

		Path summaryFile = fileResolver.getSummaryFile(runId.toString());
		Path auditFile = fileResolver.getAuditFile(runId.toString());

		Files.createDirectories(summaryFile.getParent());

		String report = buildReport(run, finalStatus, startedAt, completedAt, summaryFile, auditFile);

		// Write the report before marking the run as completed in the database.
		Files.writeString(summaryFile, report, StandardCharsets.UTF_8);

		run.setStatus(finalStatus);
		run.setCompletedAt(completedAt);
		runRepository.saveAndFlush(run);

		return RepeatStatus.FINISHED;
	}

	private boolean hasErrors(ReconciliationRun run) {
		return run.getBaselineInvalidCount() > 0 || run.getSourceInvalidCount() > 0 || run.getAmountMismatchCount() > 0
				|| run.getMissingInDatabaseCount() > 0;
	}

	private String buildReport(ReconciliationRun run, ReconciliationStatus finalStatus, LocalDateTime startedAt,
			LocalDateTime completedAt, Path summaryFile, Path auditFile) {

		long durationSeconds = Duration.between(startedAt, completedAt).getSeconds();

		StringBuilder report = new StringBuilder();

		report.append("==================================================\n");
		report.append("       TRANSACTION RECONCILIATION REPORT\n");
		report.append("==================================================\n\n");

		report.append("[RUN DETAILS]\n");
		report.append("Run ID             : ").append(run.getRunId()).append('\n');
		report.append("Status             : ").append(finalStatus).append('\n');
		report.append("Started At         : ").append(format(startedAt)).append('\n');
		report.append("Completed At       : ").append(format(completedAt)).append('\n');
		report.append("Duration           : ").append(formatDuration(durationSeconds)).append("\n\n");

		report.append("[INPUT FILES]\n");
		report.append("Baseline File      : ").append(run.getBaselineFileName()).append('\n');
		report.append("Source File        : ").append(run.getSourceFileName()).append("\n\n");

		report.append("[BASELINE IMPORT SUMMARY]\n");
		report.append("Total Rows         : ").append(run.getBaselineTotalCount()).append('\n');
		report.append("Valid Rows         : ").append(run.getBaselineValidCount()).append('\n');
		report.append("Invalid Rows       : ").append(run.getBaselineInvalidCount()).append("\n\n");

		report.append("[SOURCE FILE SUMMARY]\n");
		report.append("Total Rows         : ").append(run.getSourceTotalCount()).append('\n');
		report.append("Valid Rows         : ").append(run.getSourceValidCount()).append('\n');
		report.append("Invalid Rows       : ").append(run.getSourceInvalidCount()).append("\n\n");

		report.append("[RECONCILIATION RESULTS]\n");
		report.append("Matched            : ").append(run.getMatchedCount()).append('\n');
		report.append("Amount Mismatch    : ").append(run.getAmountMismatchCount()).append('\n');
		report.append("Missing in Database: ").append(run.getMissingInDatabaseCount()).append("\n\n");

		report.append("[OUTPUT FILES]\n");
		report.append("Audit CSV          : ").append(auditFile.toAbsolutePath()).append('\n');
		report.append("Summary Report     : ").append(summaryFile.toAbsolutePath()).append("\n\n");

		report.append("==================================================\n");
		report.append("                 END OF REPORT\n");
		report.append("==================================================\n");

		return report.toString();
	}

	private String format(LocalDateTime dateTime) {
		return dateTime == null ? "N/A" : dateTime.format(DATE_TIME_FORMATTER);
	}

	private String formatDuration(long seconds) {
		long hours = seconds / 3600;
		long minutes = (seconds % 3600) / 60;
		long remainingSeconds = seconds % 60;

		return String.format("%02d:%02d:%02d (HH:mm:ss)", hours, minutes, remainingSeconds);
	}
}