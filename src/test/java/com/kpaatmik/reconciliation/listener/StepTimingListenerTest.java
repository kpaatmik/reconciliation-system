
package com.kpaatmik.reconciliation.listener;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.step.StepExecution;

class StepTimingListenerTest {

	private StepTimingListener listener;
	private StepExecution stepExecution;

	@BeforeEach
	void setUp() {
		listener = new StepTimingListener();
		stepExecution = mock(StepExecution.class);
	}

	@Test
	void beforeStepExecutesWithoutException() {
		when(stepExecution.getStepName()).thenReturn("sourceStep");

		listener.beforeStep(stepExecution);
	}

	@Test
	void afterStepReturnsExistingExitStatus() {
		LocalDateTime startTime = LocalDateTime.of(2026, 10, 17, 10, 0, 0);

		LocalDateTime endTime = startTime.plusSeconds(5);

		ExitStatus expectedStatus = ExitStatus.COMPLETED;

		when(stepExecution.getStepName()).thenReturn("sourceStep");
		when(stepExecution.getStartTime()).thenReturn(startTime);
		when(stepExecution.getEndTime()).thenReturn(endTime);
		when(stepExecution.getReadCount()).thenReturn(10L);
		when(stepExecution.getWriteCount()).thenReturn(8L);
		when(stepExecution.getFilterCount()).thenReturn(2L);
		when(stepExecution.getSkipCount()).thenReturn(0L);
		when(stepExecution.getExitStatus()).thenReturn(expectedStatus);

		ExitStatus actualStatus = listener.afterStep(stepExecution);

		assertEquals(expectedStatus, actualStatus);
	}

	@Test
	void afterStepReturnsFailedExitStatus() {
		LocalDateTime startTime = LocalDateTime.of(2026, 10, 17, 10, 0, 0);

		LocalDateTime endTime = startTime.plusSeconds(2);

		ExitStatus expectedStatus = ExitStatus.FAILED;

		when(stepExecution.getStepName()).thenReturn("baselineStep");
		when(stepExecution.getStartTime()).thenReturn(startTime);
		when(stepExecution.getEndTime()).thenReturn(endTime);
		when(stepExecution.getReadCount()).thenReturn(5L);
		when(stepExecution.getWriteCount()).thenReturn(3L);
		when(stepExecution.getFilterCount()).thenReturn(1L);
		when(stepExecution.getSkipCount()).thenReturn(1L);
		when(stepExecution.getExitStatus()).thenReturn(expectedStatus);

		ExitStatus actualStatus = listener.afterStep(stepExecution);

		assertNotNull(actualStatus);
		assertEquals(ExitStatus.FAILED, actualStatus);
	}
}
