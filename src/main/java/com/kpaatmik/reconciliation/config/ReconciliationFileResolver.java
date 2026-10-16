
package com.kpaatmik.reconciliation.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.springframework.stereotype.Component;

@Component
public class ReconciliationFileResolver {

	private final ReconciliationFileProperties properties;

	public ReconciliationFileResolver(ReconciliationFileProperties properties) {
		this.properties = properties;
	}

	public Path getBaselineFile() {
		return Path.of(properties.getInput().getDirectory(), properties.getInput().getBaselineFile()).toAbsolutePath()
				.normalize();
	}

	public Path getSourceFile() {
		return Path.of(properties.getInput().getDirectory(), properties.getInput().getSourceFile()).toAbsolutePath()
				.normalize();
	}

	public Path getOutputDirectory() {
		return Path.of(properties.getOutput().getDirectory()).toAbsolutePath().normalize();
	}

	public Path getRunOutputDirectory(String runId) {
		validateRunId(runId);

		return getOutputDirectory().resolve("run-" + runId).normalize();
	}

	public Path getAuditFile(String runId) {
		return getRunOutputDirectory(runId).resolve("audit.csv");
	}

	public Path getSummaryFile(String runId) {
		return getRunOutputDirectory(runId).resolve("reconciliation-summary.txt");
	}

	public void prepareOutputDirectory(String runId) throws IOException {
		Path outputDirectory = getRunOutputDirectory(runId);

		Files.createDirectories(outputDirectory);

		if (!Files.isDirectory(outputDirectory) || !Files.isWritable(outputDirectory)) {
			throw new IOException("Output directory is not writable: " + outputDirectory);
		}
	}

	private void validateRunId(String runId) {
		if (runId == null || !runId.matches("[A-Za-z0-9_-]+")) {
			throw new IllegalArgumentException("Invalid runId: " + runId);
		}
	}
}
