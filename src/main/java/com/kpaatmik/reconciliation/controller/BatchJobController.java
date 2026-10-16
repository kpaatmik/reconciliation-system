
package com.kpaatmik.reconciliation.controller;

import java.util.UUID;

import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.kpaatmik.reconciliation.config.ReconciliationFileResolver;
import com.kpaatmik.reconciliation.entity.ReconciliationRun;
import com.kpaatmik.reconciliation.entity.ReconciliationStatus;
import com.kpaatmik.reconciliation.repository.ReconciliationRunRepository;

@RestController
@RequestMapping("/api/batch/reconciliation")
public class BatchJobController {

	private final JobOperator jobOperator;
	private final Job reconciliationJob;
	private final ReconciliationRunRepository runRepository;
	private final ReconciliationFileResolver fileResolver;

	public BatchJobController(JobOperator jobOperator, @Qualifier("transactionReconciliationJob") Job reconciliationJob,
			ReconciliationRunRepository runRepository, ReconciliationFileResolver fileResolver) {
		this.jobOperator = jobOperator;
		this.reconciliationJob = reconciliationJob;
		this.runRepository = runRepository;
		this.fileResolver = fileResolver;
	}

	@PostMapping("/run")
	public ResponseEntity<BatchJobResponse> runReconciliationJob() throws Exception {

		String runId = UUID.randomUUID().toString();
		UUID runUuid = UUID.fromString(runId);

		ReconciliationRun run = new ReconciliationRun();
		run.setRunId(runUuid);
		run.setBaselineFileName(fileResolver.getBaselineFile().getFileName().toString());
		run.setSourceFileName(fileResolver.getSourceFile().getFileName().toString());
		run.setStatus(ReconciliationStatus.PENDING);

		runRepository.saveAndFlush(run);

		JobParameters jobParameters = new JobParametersBuilder().addString("runId", runId).toJobParameters();

		JobExecution execution = jobOperator.start(reconciliationJob, jobParameters);

		return ResponseEntity.status(HttpStatus.ACCEPTED).body(new BatchJobResponse(runId, execution.getId(),
				execution.getStatus().toString(), "Reconciliation job submitted"));
	}

	public record BatchJobResponse(String runId, Long executionId, String status, String message) {
	}
}
