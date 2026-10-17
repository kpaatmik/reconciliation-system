
package com.kpaatmik.reconciliation.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

import com.kpaatmik.reconciliation.entity.ReconciliationRun;

class ReconciliationRunTest {

	@Test
	void onCreateSetsCreatedAtWhenNull() {
		ReconciliationRun run = new ReconciliationRun();

		assertEquals(null, run.getCreatedAt());

		LocalDateTime before = LocalDateTime.now();

		run.onCreate();

		LocalDateTime after = LocalDateTime.now();

		assertNotNull(run.getCreatedAt());
		assertTrue(!run.getCreatedAt().isBefore(before) && !run.getCreatedAt().isAfter(after),
				"createdAt should be set to the current time");
	}

	@Test
	void onCreatePreservesExistingCreatedAt() {
		ReconciliationRun run = new ReconciliationRun();

		LocalDateTime existingTime = LocalDateTime.of(2026, 1, 15, 10, 30, 0);

		run.setCreatedAt(existingTime);

		run.onCreate();

		assertEquals(existingTime, run.getCreatedAt());
	}
}
