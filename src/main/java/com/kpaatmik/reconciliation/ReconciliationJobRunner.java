
package com.kpaatmik.reconciliation;

import java.util.UUID;

import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import com.kpaatmik.reconciliation.config.ReconciliationFileResolver;
import com.kpaatmik.reconciliation.entity.ReconciliationRun;
import com.kpaatmik.reconciliation.entity.ReconciliationStatus;
import com.kpaatmik.reconciliation.repository.ReconciliationRunRepository;

@Component
public class ReconciliationJobRunner implements CommandLineRunner {

	private final JobOperator jobOperator;
	private final Job reconciliationJob;
	private final JobRepository jobRepository;
	private final ReconciliationRunRepository runRepository;
	private final ReconciliationFileResolver fileResolver;

	public ReconciliationJobRunner(JobOperator jobOperator,
			@Qualifier("transactionReconciliationJob") Job reconciliationJob, JobRepository jobRepository,
			ReconciliationRunRepository runRepository, ReconciliationFileResolver fileResolver) {

		this.jobOperator = jobOperator;
		this.reconciliationJob = reconciliationJob;
		this.jobRepository = jobRepository;
		this.runRepository = runRepository;
		this.fileResolver = fileResolver;
	}

	@Override
	public void run(String... args) throws Exception {
		String executionId = getArgument(args, "--restartExecutionId");

		if (executionId != null) {
			restartRun(executionId);
		} else {
			startNewRun();
		}
	}

	private void startNewRun() throws Exception {
		String runId = UUID.randomUUID().toString();

		ReconciliationRun run = new ReconciliationRun();
		run.setRunId(UUID.fromString(runId));
		run.setBaselineFileName(fileResolver.getBaselineFile().getFileName().toString());
		run.setSourceFileName(fileResolver.getSourceFile().getFileName().toString());
		run.setStatus(ReconciliationStatus.PENDING);

		runRepository.saveAndFlush(run);

		JobParameters parameters = new JobParametersBuilder().addString("runId", runId).toJobParameters();

		JobExecution execution = jobOperator.start(reconciliationJob, parameters);

		printExecution(runId, execution);
	}

	private void restartRun(String executionIdArgument) throws Exception {
		long executionId;

		try {
			executionId = Long.parseLong(executionIdArgument);
		} catch (NumberFormatException e) {
			throw new IllegalArgumentException("--restartExecutionId must be a valid numeric Batch execution ID.", e);
		}

		JobExecution previousExecution = jobRepository.getJobExecution(executionId);

		if (previousExecution == null) {
			throw new IllegalArgumentException("No Batch execution found with ID: " + executionId);
		}

		if (!reconciliationJob.getName().equals(previousExecution.getJobInstance().getJobName())) {
			throw new IllegalArgumentException("Execution ID does not belong to " + reconciliationJob.getName());
		}

		if (previousExecution.getStatus().isRunning()) {
			throw new IllegalStateException("This execution is already running: " + executionId);
		}

		switch (previousExecution.getStatus()) {
		case FAILED, STOPPED -> {
			JobExecution execution = jobOperator.restart(previousExecution);

			String runId = execution.getJobParameters().getString("runId");

			printExecution(runId, execution);
		}
		default -> throw new IllegalStateException("Only FAILED or STOPPED executions can be restarted. "
				+ "Current status: " + previousExecution.getStatus());
		}
	}

	private String getArgument(String[] args, String name) {
		for (String arg : args) {
			if (arg.startsWith(name + "=")) {
				return arg.substring(name.length() + 1);
			}
		}
		return null;
	}

	private void printExecution(String runId, JobExecution execution) {
		System.out.println("Run ID: " + runId);
		System.out.println("Execution ID: " + execution.getId());
		System.out.println("Status: " + execution.getStatus());
	}
}
