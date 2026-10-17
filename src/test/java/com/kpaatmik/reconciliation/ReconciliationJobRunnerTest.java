
package com.kpaatmik.reconciliation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.nio.file.Path;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.JobInstance;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.batch.core.repository.JobRepository;

import com.kpaatmik.reconciliation.config.ReconciliationFileResolver;
import com.kpaatmik.reconciliation.entity.ReconciliationRun;
import com.kpaatmik.reconciliation.repository.ReconciliationRunRepository;

class ReconciliationJobRunnerTest {

	private JobOperator jobOperator;
	private Job reconciliationJob;
	private JobRepository jobRepository;
	private ReconciliationRunRepository runRepository;
	private ReconciliationFileResolver fileResolver;

	private ReconciliationJobRunner runner;

	@BeforeEach
	void setUp() {
		jobOperator = mock(JobOperator.class);
		reconciliationJob = mock(Job.class);
		jobRepository = mock(JobRepository.class);
		runRepository = mock(ReconciliationRunRepository.class);
		fileResolver = mock(ReconciliationFileResolver.class);

		when(reconciliationJob.getName()).thenReturn("transactionReconciliationJob");

		when(fileResolver.getBaselineFile()).thenReturn(Path.of("baseline.csv"));

		when(fileResolver.getSourceFile()).thenReturn(Path.of("source.csv"));

		runner = new ReconciliationJobRunner(jobOperator, reconciliationJob, jobRepository, runRepository,
				fileResolver);
	}

	@Test
	void startsNewRunWhenNoRestartArgumentIsProvided() throws Exception {

		JobExecution execution = mock(JobExecution.class);
		when(execution.getId()).thenReturn(101L);
		when(execution.getStatus()).thenReturn(BatchStatus.STARTING);

		when(jobOperator.start(eq(reconciliationJob), any(JobParameters.class))).thenReturn(execution);

		runner.run();

		verify(runRepository).saveAndFlush(any(ReconciliationRun.class));

		verify(jobOperator).start(eq(reconciliationJob), any(JobParameters.class));

		verify(jobOperator, never()).restart(any(JobExecution.class));
	}

	@Test
	void rejectsInvalidRestartExecutionId() throws Exception {
		IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
				() -> runner.run("--restartExecutionId=abc"));

		assertEquals("--restartExecutionId must be a valid numeric Batch execution ID.", exception.getMessage());

		verifyNoInteractions(jobRepository, jobOperator);
	}

	@Test
	void rejectsRestartWhenExecutionDoesNotExist() throws Exception {
		when(jobRepository.getJobExecution(42L)).thenReturn(null);

		IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
				() -> runner.run("--restartExecutionId=42"));

		assertEquals("No Batch execution found with ID: 42", exception.getMessage());

		verify(jobOperator, never()).restart(any(JobExecution.class));
	}

	@Test
	void rejectsRestartForAnotherJob() throws Exception {
		JobExecution previousExecution = mock(JobExecution.class);
		JobInstance jobInstance = mock(JobInstance.class);

		when(jobRepository.getJobExecution(42L)).thenReturn(previousExecution);
		when(previousExecution.getJobInstance()).thenReturn(jobInstance);
		when(jobInstance.getJobName()).thenReturn("anotherJob");

		IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
				() -> runner.run("--restartExecutionId=42"));

		assertEquals("Execution ID does not belong to transactionReconciliationJob", exception.getMessage());

		verify(jobOperator, never()).restart(any(JobExecution.class));
	}

	@Test
	void rejectsRestartWhenExecutionIsRunning() throws Exception {
		JobExecution previousExecution = mock(JobExecution.class);
		JobInstance jobInstance = mock(JobInstance.class);

		when(jobRepository.getJobExecution(42L)).thenReturn(previousExecution);
		when(previousExecution.getJobInstance()).thenReturn(jobInstance);
		when(jobInstance.getJobName()).thenReturn("transactionReconciliationJob");
		when(previousExecution.getStatus()).thenReturn(BatchStatus.STARTED);

		IllegalStateException exception = assertThrows(IllegalStateException.class,
				() -> runner.run("--restartExecutionId=42"));

		assertEquals("This execution is already running: 42", exception.getMessage());

		verify(jobOperator, never()).restart(any(JobExecution.class));
	}

	@Test
	void restartsFailedExecution() throws Exception {
		JobExecution previousExecution = mock(JobExecution.class);
		JobExecution restartedExecution = mock(JobExecution.class);
		JobInstance jobInstance = mock(JobInstance.class);
		JobParameters parameters = mock(JobParameters.class);

		String runId = UUID.randomUUID().toString();

		when(jobRepository.getJobExecution(42L)).thenReturn(previousExecution);
		when(previousExecution.getJobInstance()).thenReturn(jobInstance);
		when(jobInstance.getJobName()).thenReturn("transactionReconciliationJob");
		when(previousExecution.getStatus()).thenReturn(BatchStatus.FAILED);

		when(jobOperator.restart(previousExecution)).thenReturn(restartedExecution);
		when(restartedExecution.getJobParameters()).thenReturn(parameters);
		when(parameters.getString("runId")).thenReturn(runId);
		when(restartedExecution.getId()).thenReturn(43L);
		when(restartedExecution.getStatus()).thenReturn(BatchStatus.STARTING);

		runner.run("--restartExecutionId=42");

		verify(jobOperator).restart(previousExecution);
	}

	@Test
	void rejectsRestartWhenExecutionIsCompleted() throws Exception {
		JobExecution previousExecution = mock(JobExecution.class);
		JobInstance jobInstance = mock(JobInstance.class);

		when(jobRepository.getJobExecution(42L)).thenReturn(previousExecution);
		when(previousExecution.getJobInstance()).thenReturn(jobInstance);
		when(jobInstance.getJobName()).thenReturn("transactionReconciliationJob");
		when(previousExecution.getStatus()).thenReturn(BatchStatus.COMPLETED);

		IllegalStateException exception = assertThrows(IllegalStateException.class,
				() -> runner.run("--restartExecutionId=42"));

		assertEquals("Only FAILED or STOPPED executions can be restarted. " + "Current status: COMPLETED",
				exception.getMessage());

		verify(jobOperator, never()).restart(any(JobExecution.class));
	}
}
