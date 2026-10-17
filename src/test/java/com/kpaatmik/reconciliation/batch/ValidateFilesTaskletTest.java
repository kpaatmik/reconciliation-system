
package com.kpaatmik.reconciliation.batch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.scope.context.StepContext;
import org.springframework.batch.core.step.StepContribution;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;

import com.kpaatmik.reconciliation.config.ReconciliationFileResolver;

class ValidateFilesTaskletTest {

	@TempDir
	Path tempDir;

	private ReconciliationFileResolver resolver;
	private ValidateFilesTasklet tasklet;
	private Path baseline;
	private Path source;
	private ChunkContext chunkContext;
	private StepContribution contribution;

	private static final String HEADER = "transaction_id,account_number,transaction_date,amount,currency,transaction_type,description";

	@BeforeEach
	void setUp() throws Exception {
		resolver = mock(ReconciliationFileResolver.class);
		tasklet = new ValidateFilesTasklet(resolver);

		baseline = tempDir.resolve("baseline.csv");
		source = tempDir.resolve("source.csv");

		when(resolver.getBaselineFile()).thenReturn(baseline);
		when(resolver.getSourceFile()).thenReturn(source);
		when(resolver.getRunOutputDirectory(anyString())).thenReturn(tempDir.resolve("output"));

		chunkContext = mock(ChunkContext.class);
		StepContext stepContext = mock(StepContext.class);
		StepExecution stepExecution = mock(StepExecution.class);
		JobParameters parameters = mock(JobParameters.class);

		when(chunkContext.getStepContext()).thenReturn(stepContext);
		when(stepContext.getStepExecution()).thenReturn(stepExecution);
		when(stepExecution.getJobParameters()).thenReturn(parameters);
		when(parameters.getString("runId")).thenReturn(UUID.randomUUID().toString());

		contribution = mock(StepContribution.class);
	}

	private void writeCsv(Path path, String content) throws Exception {
		Files.writeString(path, content);
	}

	private String validCsv() {
		return HEADER + "\n" + "TX001,ACC001,2026-10-01,100.00,INR,CREDIT,Payment\n";
	}

	@Test
	void validatesBothFilesAndPreparesOutputDirectory() throws Exception {
		writeCsv(baseline, validCsv());
		writeCsv(source, validCsv());

		assertEquals(RepeatStatus.FINISHED, tasklet.execute(contribution, chunkContext));

		verify(resolver).prepareOutputDirectory(anyString());
	}

	@Test
	void rejectsMissingRunId() throws Exception {
		when(chunkContext.getStepContext().getStepExecution().getJobParameters().getString("runId")).thenReturn(" ");

		assertThrows(IllegalArgumentException.class, () -> tasklet.execute(contribution, chunkContext));
	}

	@Test
	void rejectsMissingBaselineFile() {
		assertThrows(IllegalStateException.class, () -> tasklet.execute(contribution, chunkContext));
	}

	@Test
	void rejectsEmptyBaselineFile() throws Exception {
		Files.createFile(baseline);
		writeCsv(source, validCsv());

		assertThrows(IllegalStateException.class, () -> tasklet.execute(contribution, chunkContext));
	}

	@Test
	void rejectsIncorrectHeaders() throws Exception {
		writeCsv(baseline, "id,amount\nTX001,100");
		writeCsv(source, validCsv());

		assertThrows(IllegalStateException.class, () -> tasklet.execute(contribution, chunkContext));
	}

	@Test
	void rejectsInvalidColumnCount() throws Exception {
		writeCsv(baseline, HEADER + "\nTX001,ACC001\n");
		writeCsv(source, validCsv());

		assertThrows(IllegalStateException.class, () -> tasklet.execute(contribution, chunkContext));
	}

	@Test
	void rejectsMissingSourceFile() throws Exception {
		writeCsv(baseline, validCsv());

		assertThrows(IllegalStateException.class, () -> tasklet.execute(contribution, chunkContext));
	}

	@Test
	void rejectsDirectoryInsteadOfBaselineFile() throws Exception {
		Files.createDirectory(baseline);
		Files.writeString(source, validCsv());

		IllegalStateException exception = assertThrows(IllegalStateException.class,
				() -> tasklet.execute(contribution, chunkContext));

		assertTrue(exception.getMessage().contains("CSV path is not a regular file"));
	}

	@Test
	void wrapsMalformedCsvParsingFailure() throws Exception {
		Files.writeString(baseline, HEADER + "\n" + "\"TX001,ACC001,2026-10-01,100.00,INR,CREDIT,Payment\n");

		Files.writeString(source, validCsv());

		IllegalStateException exception = assertThrows(IllegalStateException.class,
				() -> tasklet.execute(contribution, chunkContext));

		assertTrue(exception.getMessage().contains("Unable to parse CSV file"));
	}
}
