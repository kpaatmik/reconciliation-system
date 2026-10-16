
package com.kpaatmik.reconciliation.listener;

import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.listener.StepExecutionListener;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.stereotype.Component;

@Component
public class StepTimingListener implements StepExecutionListener {

	private static final Logger log = LoggerFactory.getLogger(StepTimingListener.class);

	@Override
	public void beforeStep(StepExecution stepExecution) {
		log.info("Step '{}' started", stepExecution.getStepName());
	}

	@Override
	public ExitStatus afterStep(StepExecution stepExecution) {
		Duration duration = Duration.between(stepExecution.getStartTime(), stepExecution.getEndTime());

		log.info("Step '{}' completed in {} ms. Read={}, Written={}, Filtered={}, Skipped={}",
				stepExecution.getStepName(), duration.toMillis(), stepExecution.getReadCount(),
				stepExecution.getWriteCount(), stepExecution.getFilterCount(), stepExecution.getSkipCount());

		return stepExecution.getExitStatus();
	}
}