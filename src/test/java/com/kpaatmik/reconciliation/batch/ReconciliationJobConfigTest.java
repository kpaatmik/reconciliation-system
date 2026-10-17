
package com.kpaatmik.reconciliation.batch;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.kpaatmik.reconciliation.config.ReconciliationFileResolver;
import com.kpaatmik.reconciliation.repository.ReconciliationRunRepository;
import com.kpaatmik.reconciliation.repository.TransactionRepository;
import com.kpaatmik.reconciliation.writer.BaselineChunkWriter;
import com.kpaatmik.reconciliation.writer.SourceReconciliationWriter;

class ReconciliationJobConfigTest {

	private ReconciliationJobConfig config;
	private TransactionRepository transactionRepository;
	private ReconciliationRunRepository runRepository;
	private ReconciliationFileResolver fileResolver;

	@BeforeEach
	void setUp() {
		config = new ReconciliationJobConfig();
		transactionRepository = mock(TransactionRepository.class);
		runRepository = mock(ReconciliationRunRepository.class);
		fileResolver = mock(ReconciliationFileResolver.class);
	}

	@Test
	void createsBaselineChunkWriter() {
		String runId = UUID.randomUUID().toString();

		BaselineChunkWriter writer = config.baselineChunkWriter(transactionRepository, runRepository, fileResolver,
				runId);

		assertNotNull(writer);
		assertInstanceOf(BaselineChunkWriter.class, writer);
	}

	@Test
	void createsSourceReconciliationWriter() {
		String runId = UUID.randomUUID().toString();

		SourceReconciliationWriter writer = config.sourceReconciliationWriter(transactionRepository, runRepository,
				fileResolver, runId);

		assertNotNull(writer);
		assertInstanceOf(SourceReconciliationWriter.class, writer);
	}

	@Test
	void baselineWriterRejectsInvalidRunId() {
		assertThrows(IllegalArgumentException.class,
				() -> config.baselineChunkWriter(transactionRepository, runRepository, fileResolver, "invalid-uuid"));
	}

	@Test
	void sourceWriterRejectsInvalidRunId() {
		assertThrows(IllegalArgumentException.class, () -> config.sourceReconciliationWriter(transactionRepository,
				runRepository, fileResolver, "invalid-uuid"));
	}
}
