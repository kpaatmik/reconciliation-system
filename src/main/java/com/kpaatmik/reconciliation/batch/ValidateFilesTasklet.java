package com.kpaatmik.reconciliation.batch;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.StepContribution;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;
import org.springframework.stereotype.Component;

import com.kpaatmik.reconciliation.config.ReconciliationFileResolver;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class ValidateFilesTasklet implements Tasklet {

	private final ReconciliationFileResolver fileResolver;

	@Override
	public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) throws Exception {

		String runId = chunkContext.getStepContext().getStepExecution().getJobParameters().getString("runId");

		if (runId == null || runId.isBlank()) {
			throw new IllegalArgumentException("Required job parameter 'runId' is missing");
		}

		Path baselineFile = fileResolver.getBaselineFile();
		Path sourceFile = fileResolver.getSourceFile();

		// Validate both files before starting the import step.
		validateCsvFile(baselineFile, CsvSchemas.TRANSACTION_HEADERS);

		validateCsvFile(sourceFile, CsvSchemas.TRANSACTION_HEADERS);

		// Prepare the directory for this reconciliation run.
		fileResolver.prepareOutputDirectory(runId);

		System.out.println("Step 1: File validation completed.");
		System.out.println("Baseline file: " + baselineFile);
		System.out.println("Source file:   " + sourceFile);
		System.out.println("Output directory: " + fileResolver.getRunOutputDirectory(runId));

		return RepeatStatus.FINISHED;
	}

	private void validateCsvFile(Path file, List<String> expectedHeaders) throws IOException {

		if (!Files.exists(file)) {
			throw new IllegalStateException("CSV file does not exist: " + file);
		}

		if (!Files.isRegularFile(file)) {
			throw new IllegalStateException("CSV path is not a regular file: " + file);
		}

		if (!Files.isReadable(file)) {
			throw new IllegalStateException("CSV file is not readable: " + file);
		}

		if (Files.size(file) == 0) {
			throw new IllegalStateException("CSV file is empty: " + file);
		}

		CSVFormat format = CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).setIgnoreEmptyLines(false)
				.get();

		try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8);
				CSVParser parser = format.parse(reader)) {

			List<String> actualHeaders = parser.getHeaderNames();

			if (!expectedHeaders.equals(actualHeaders)) {
				throw new IllegalStateException("Invalid CSV header in " + file + ". Expected: " + expectedHeaders
						+ ", actual: " + actualHeaders);
			}

			long recordNumber = 1; // Header is record 1.

			for (CSVRecord record : parser) {
				recordNumber++;

				if (record.size() != expectedHeaders.size()) {
					throw new IllegalStateException("Invalid column count in " + file + " at CSV record " + recordNumber
							+ ". Expected " + expectedHeaders.size() + " columns, but found " + record.size());
				}
			}

		} catch (IllegalStateException exception) {
			throw exception;
		} catch (IOException | IllegalArgumentException exception) {
			throw new IllegalStateException("Unable to parse CSV file: " + file + ". " + exception.getMessage(),
					exception);
		}
	}
}
