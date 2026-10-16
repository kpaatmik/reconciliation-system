package com.kpaatmik.reconciliation.config;

import org.springframework.batch.integration.async.AsyncItemProcessor;
import org.springframework.batch.integration.async.AsyncItemWriter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import com.kpaatmik.reconciliation.dto.BaselineCsvRecord;
import com.kpaatmik.reconciliation.dto.BaselineProcessedRecord;
import com.kpaatmik.reconciliation.dto.SourceCsvRecord;
import com.kpaatmik.reconciliation.dto.SourceProcessedRecord;
import com.kpaatmik.reconciliation.processor.BaselineRecordProcessor;
import com.kpaatmik.reconciliation.processor.SourceRecordProcessor;
import com.kpaatmik.reconciliation.writer.BaselineChunkWriter;
import com.kpaatmik.reconciliation.writer.SourceReconciliationWriter;

@Configuration
public class AsyncBatchConfig {

	@Bean("reconciliationTaskExecutor")
	public TaskExecutor reconciliationTaskExecutor() {
		ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

		executor.setCorePoolSize(4);
		executor.setMaxPoolSize(4);
		executor.setQueueCapacity(500);
		executor.setThreadNamePrefix("reconciliation-worker-");
		executor.setWaitForTasksToCompleteOnShutdown(true);

		return executor;
	}

	@Bean
	public AsyncItemProcessor<SourceCsvRecord, SourceProcessedRecord> asyncSourceProcessor(
			SourceRecordProcessor sourceRecordProcessor,
			@Qualifier("reconciliationTaskExecutor") TaskExecutor taskExecutor) {

		AsyncItemProcessor<SourceCsvRecord, SourceProcessedRecord> processor = new AsyncItemProcessor<>(
				sourceRecordProcessor);

		processor.setTaskExecutor(taskExecutor);

		return processor;
	}

	@Bean
	public AsyncItemWriter<SourceProcessedRecord> asyncSourceWriter(
			SourceReconciliationWriter sourceReconciliationWriter) {

		return new AsyncItemWriter<>(sourceReconciliationWriter);
	}

	@Bean("asyncBaselineProcessor")
	public AsyncItemProcessor<BaselineCsvRecord, BaselineProcessedRecord> asyncBaselineProcessor(
			BaselineRecordProcessor baselineRecordProcessor,
			@Qualifier("reconciliationTaskExecutor") TaskExecutor taskExecutor) {

		AsyncItemProcessor<BaselineCsvRecord, BaselineProcessedRecord> processor = new AsyncItemProcessor<>(
				baselineRecordProcessor);

		processor.setTaskExecutor(taskExecutor);

		return processor;
	}

	@Bean("asyncBaselineWriter")
	public AsyncItemWriter<BaselineProcessedRecord> asyncBaselineWriter(BaselineChunkWriter baselineChunkWriter) {

		return new AsyncItemWriter<>(baselineChunkWriter);
	}

}
