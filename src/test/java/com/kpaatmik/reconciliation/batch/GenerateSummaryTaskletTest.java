
package com.kpaatmik.reconciliation.batch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.StepContribution;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;
import org.springframework.test.util.ReflectionTestUtils;

import com.kpaatmik.reconciliation.config.ReconciliationFileResolver;
import com.kpaatmik.reconciliation.entity.ReconciliationRun;
import com.kpaatmik.reconciliation.entity.ReconciliationStatus;
import com.kpaatmik.reconciliation.repository.ReconciliationRunRepository;

class GenerateSummaryTaskletTest {

	@TempDir
	Path tempDir;

	private ReconciliationRunRepository runRepository;
	private ReconciliationFileResolver fileResolver;
	private GenerateSummaryTasklet tasklet;
	private UUID runId;

	@BeforeEach
	void setUp() {
		runRepository = mock(ReconciliationRunRepository.class);
		fileResolver = mock(ReconciliationFileResolver.class);
		runId = UUID.randomUUID();

		when(fileResolver.getSummaryFile(runId.toString()))
				.thenReturn(tempDir.resolve("run-" + runId + "/summary.txt"));

		when(fileResolver.getAuditFile(runId.toString())).thenReturn(tempDir.resolve("run-" + runId + "/audit.csv"));

		tasklet = new GenerateSummaryTasklet(runRepository, fileResolver);

		// runId is UUID in GenerateSummaryTasklet, so inject a UUID.
		ReflectionTestUtils.setField(tasklet, "runId", runId);
	}

	private ReconciliationRun createRun() {
		ReconciliationRun run = mock(ReconciliationRun.class);

		when(run.getRunId()).thenReturn(runId);
		when(run.getBaselineFileName()).thenReturn("baseline.csv");
		when(run.getSourceFileName()).thenReturn("source.csv");

		when(run.getCreatedAt()).thenReturn(LocalDateTime.of(2026, 10, 17, 10, 0));

		when(run.getStartedAt()).thenReturn(LocalDateTime.of(2026, 10, 17, 10, 0));

		return run;
	}

	private void stubZeroCounts(ReconciliationRun run) {
		when(run.getBaselineTotalCount()).thenReturn(10L);
		when(run.getBaselineValidCount()).thenReturn(10L);
		when(run.getBaselineInvalidCount()).thenReturn(0L);

		when(run.getSourceTotalCount()).thenReturn(10L);
		when(run.getSourceValidCount()).thenReturn(10L);
		when(run.getSourceInvalidCount()).thenReturn(0L);

		when(run.getMatchedCount()).thenReturn(10L);
		when(run.getAmountMismatchCount()).thenReturn(0L);
		when(run.getMissingInDatabaseCount()).thenReturn(0L);
	}

	@Test
	void generatesSuccessfulSummary() throws Exception {
		ReconciliationRun run = createRun();
		stubZeroCounts(run);

		when(runRepository.findById(runId)).thenReturn(Optional.of(run));

		RepeatStatus result = tasklet.execute(mock(StepContribution.class), mock(ChunkContext.class));

		assertEquals(RepeatStatus.FINISHED, result);

		Path report = tempDir.resolve("run-" + runId + "/summary.txt");

		assertTrue(Files.exists(report));

		String content = Files.readString(report);

		assertTrue(content.contains("TRANSACTION RECONCILIATION REPORT"));
		assertTrue(content.contains("COMPLETED"));
		assertTrue(content.contains("baseline.csv"));
		assertTrue(content.contains("source.csv"));

		verify(run).setStatus(ReconciliationStatus.COMPLETED);
		verify(run).setCompletedAt(any(LocalDateTime.class));
		verify(runRepository).saveAndFlush(run);
	}

	@Test
	void marksRunCompletedWithErrorsWhenMismatchExists() throws Exception {
		ReconciliationRun run = createRun();
		stubZeroCounts(run);

		when(run.getAmountMismatchCount()).thenReturn(1L);
		when(runRepository.findById(runId)).thenReturn(Optional.of(run));

		tasklet.execute(mock(StepContribution.class), mock(ChunkContext.class));

		verify(run).setStatus(ReconciliationStatus.COMPLETED_WITH_ERRORS);
	}

	@Test
	void usesCreatedAtWhenStartedAtIsNull() throws Exception {
		ReconciliationRun run = createRun();
		stubZeroCounts(run);

		when(run.getStartedAt()).thenReturn(null);
		when(runRepository.findById(runId)).thenReturn(Optional.of(run));

		tasklet.execute(mock(StepContribution.class), mock(ChunkContext.class));

		Path report = tempDir.resolve("run-" + runId + "/summary.txt");

		assertTrue(Files.exists(report));
		assertTrue(Files.readString(report).contains("Started At"));
	}

	@Test
	void throwsWhenRunDoesNotExist() {
		when(runRepository.findById(runId)).thenReturn(Optional.empty());

		assertThrows(IllegalStateException.class,
				() -> tasklet.execute(mock(StepContribution.class), mock(ChunkContext.class)));
	}

	@Test
	void marksErrorsWhenBaselineContainsInvalidRecords() throws Exception {
		ReconciliationRun run = createRun();
		stubZeroCounts(run);

		when(run.getBaselineInvalidCount()).thenReturn(1L);
		when(runRepository.findById(runId)).thenReturn(Optional.of(run));

		tasklet.execute(mock(StepContribution.class), mock(ChunkContext.class));

		verify(run).setStatus(ReconciliationStatus.COMPLETED_WITH_ERRORS);
	}

	@Test
	void marksErrorsWhenSourceContainsInvalidRecords() throws Exception {
		ReconciliationRun run = createRun();
		stubZeroCounts(run);

		when(run.getSourceInvalidCount()).thenReturn(1L);
		when(runRepository.findById(runId)).thenReturn(Optional.of(run));

		tasklet.execute(mock(StepContribution.class), mock(ChunkContext.class));

		verify(run).setStatus(ReconciliationStatus.COMPLETED_WITH_ERRORS);
	}

	@Test
	void marksErrorsWhenTransactionsHaveAmountMismatch() throws Exception {
		ReconciliationRun run = createRun();
		stubZeroCounts(run);

		when(run.getAmountMismatchCount()).thenReturn(1L);
		when(runRepository.findById(runId)).thenReturn(Optional.of(run));

		tasklet.execute(mock(StepContribution.class), mock(ChunkContext.class));

		verify(run).setStatus(ReconciliationStatus.COMPLETED_WITH_ERRORS);
	}

	@Test
	void marksErrorsWhenTransactionsAreMissingInDatabase() throws Exception {
		ReconciliationRun run = createRun();
		stubZeroCounts(run);

		when(run.getMissingInDatabaseCount()).thenReturn(1L);
		when(runRepository.findById(runId)).thenReturn(Optional.of(run));

		tasklet.execute(mock(StepContribution.class), mock(ChunkContext.class));

		verify(run).setStatus(ReconciliationStatus.COMPLETED_WITH_ERRORS);
	}

	@Test
	void formatReturnsNAWhenDateTimeIsNull() throws Exception {
		var method = GenerateSummaryTasklet.class.getDeclaredMethod("format", LocalDateTime.class);

		method.setAccessible(true);

		String result = (String) method.invoke(tasklet, (Object) null);

		assertEquals("N/A", result);
	}

	@Test
	void formatReturnsFormattedDateTimeWhenNotNull() throws Exception {
		var method = GenerateSummaryTasklet.class.getDeclaredMethod("format", LocalDateTime.class);

		method.setAccessible(true);

		LocalDateTime dateTime = LocalDateTime.of(2026, 10, 17, 10, 30, 45);

		String result = (String) method.invoke(tasklet, dateTime);

		assertEquals("2026-10-17 10:30:45", result);
	}
}
