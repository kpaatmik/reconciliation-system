package com.kpaatmik.reconciliation.batch;

import java.util.concurrent.Future;

import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.infrastructure.item.file.FlatFileItemReader;
import org.springframework.batch.integration.async.AsyncItemProcessor;
import org.springframework.batch.integration.async.AsyncItemWriter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

import com.kpaatmik.reconciliation.config.ReconciliationFileResolver;
import com.kpaatmik.reconciliation.dto.BaselineCsvRecord;
import com.kpaatmik.reconciliation.dto.BaselineProcessedRecord;
import com.kpaatmik.reconciliation.dto.SourceCsvRecord;
import com.kpaatmik.reconciliation.dto.SourceProcessedRecord;
import com.kpaatmik.reconciliation.listener.StepTimingListener;
import com.kpaatmik.reconciliation.repository.ReconciliationRunRepository;
import com.kpaatmik.reconciliation.repository.TransactionRepository;
import com.kpaatmik.reconciliation.writer.BaselineChunkWriter;
import com.kpaatmik.reconciliation.writer.SourceReconciliationWriter;

@Configuration
public class ReconciliationJobConfig {

	@Bean
	public Step validateFilesStep(JobRepository jobRepository, PlatformTransactionManager transactionManager,
			ValidateFilesTasklet validateFilesTasklet, StepTimingListener stepTimingListener) {

		return new StepBuilder("validateFilesStep", jobRepository).tasklet(validateFilesTasklet, transactionManager)
				.listener(stepTimingListener).build();
	}

	@Bean
	@StepScope
	public BaselineChunkWriter baselineChunkWriter(TransactionRepository transactionRepository,
			ReconciliationRunRepository runRepository, ReconciliationFileResolver fileResolver,
			@Value("#{jobParameters['runId']}") String runId) {

		return new BaselineChunkWriter(transactionRepository, runRepository, fileResolver, runId);
	}

//	@Bean
//	public Step importBaselineStep(JobRepository jobRepository, PlatformTransactionManager transactionManager,
//			FlatFileItemReader<BaselineCsvRecord> baselineReader, BaselineRecordProcessor baselineRecordProcessor,
//			BaselineChunkWriter baselineChunkWriter, @Value("${reconciliation.chunk-size:500}") int chunkSize) {
//
//		return new StepBuilder("importBaselineStep", jobRepository)
//				.<BaselineCsvRecord, BaselineProcessedRecord>chunk(chunkSize).transactionManager(transactionManager)
//				.reader(baselineReader).processor(baselineRecordProcessor).writer(baselineChunkWriter).build();
//	}

	@Bean
	public Step importBaselineStep(JobRepository jobRepository, PlatformTransactionManager transactionManager,
			FlatFileItemReader<BaselineCsvRecord> baselineReader,
			@Qualifier("asyncBaselineProcessor") AsyncItemProcessor<BaselineCsvRecord, BaselineProcessedRecord> asyncBaselineProcessor,
			@Qualifier("asyncBaselineWriter") AsyncItemWriter<BaselineProcessedRecord> asyncBaselineWriter,
			StepTimingListener stepTimingListener, @Value("${reconciliation.chunk-size:500}") int chunkSize) {

		return new StepBuilder("importBaselineStep", jobRepository)
				.<BaselineCsvRecord, Future<BaselineProcessedRecord>>chunk(chunkSize)
				.transactionManager(transactionManager).reader(baselineReader).processor(asyncBaselineProcessor)
				.writer(asyncBaselineWriter).listener(stepTimingListener).build();
	}

	@Bean
	@StepScope
	public SourceReconciliationWriter sourceReconciliationWriter(TransactionRepository transactionRepository,
			ReconciliationRunRepository runRepository, ReconciliationFileResolver fileResolver,
			@Value("#{jobParameters['runId']}") String runId) {

		return new SourceReconciliationWriter(transactionRepository, runRepository, fileResolver, runId);
	}

//	@Bean
//	public Step reconcileSourceStep(JobRepository jobRepository, PlatformTransactionManager transactionManager,
//			FlatFileItemReader<SourceCsvRecord> sourceReader, SourceRecordProcessor sourceRecordProcessor,
//			SourceReconciliationWriter sourceReconciliationWriter,
//			@Value("${reconciliation.chunk-size:500}") int chunkSize) {
//
//		return new StepBuilder("reconcileSourceStep", jobRepository)
//				.<SourceCsvRecord, SourceProcessedRecord>chunk(chunkSize).transactionManager(transactionManager)
//				.reader(sourceReader).processor(sourceRecordProcessor).writer(sourceReconciliationWriter).build();
//	}

	@Bean
	public Step reconcileSourceStep(JobRepository jobRepository, PlatformTransactionManager transactionManager,
			FlatFileItemReader<SourceCsvRecord> sourceReader,
			AsyncItemProcessor<SourceCsvRecord, SourceProcessedRecord> asyncSourceProcessor,
			AsyncItemWriter<SourceProcessedRecord> asyncSourceWriter, StepTimingListener stepTimingListener,
			@Value("${reconciliation.chunk-size:500}") int chunkSize) {

		return new StepBuilder("reconcileSourceStep", jobRepository)
				.<SourceCsvRecord, Future<SourceProcessedRecord>>chunk(chunkSize).transactionManager(transactionManager)
				.reader(sourceReader).processor(asyncSourceProcessor).writer(asyncSourceWriter)
				.listener(stepTimingListener).build();
	}

	@Bean
	public Step generateSummaryStep(JobRepository jobRepository, PlatformTransactionManager transactionManager,
			GenerateSummaryTasklet generateSummaryTasklet, StepTimingListener stepTimingListener) {

		return new StepBuilder("generateSummaryStep", jobRepository).tasklet(generateSummaryTasklet, transactionManager)
				.listener(stepTimingListener).build();
	}

	@Bean
	public Job transactionReconciliationJob(JobRepository jobRepository, Step validateFilesStep,
			Step importBaselineStep, Step reconcileSourceStep, Step generateSummaryStep) {

		return new JobBuilder("transactionReconciliationJob", jobRepository).start(validateFilesStep)
				.next(importBaselineStep).next(reconcileSourceStep).next(generateSummaryStep).build();
	}
}