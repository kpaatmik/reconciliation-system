package com.kpaatmik.reconciliation.entity;

import java.time.LocalDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "reconciliation_run")
@Getter
@Setter
@NoArgsConstructor
public class ReconciliationRun {

	@Id
	@Column(name = "run_id", nullable = false, updatable = false)
	private UUID runId;

	@Column(name = "baseline_file_name", nullable = false, length = 255)
	private String baselineFileName;

	@Column(name = "source_file_name", nullable = false, length = 255)
	private String sourceFileName;

	@Column(name = "baseline_file_size")
	private Long baselineFileSize;

	@Column(name = "source_file_size")
	private Long sourceFileSize;

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false, length = 30)
	private ReconciliationStatus status = ReconciliationStatus.PENDING;

	// Baseline file counters
	@Column(name = "baseline_total_count", nullable = false)
	private long baselineTotalCount = 0;

	@Column(name = "baseline_valid_count", nullable = false)
	private long baselineValidCount = 0;

	@Column(name = "baseline_invalid_count", nullable = false)
	private long baselineInvalidCount = 0;

	// Source file counters
	@Column(name = "source_total_count", nullable = false)
	private long sourceTotalCount = 0;

	@Column(name = "source_valid_count", nullable = false)
	private long sourceValidCount = 0;

	@Column(name = "source_invalid_count", nullable = false)
	private long sourceInvalidCount = 0;

	// Reconciliation result counters
	@Column(name = "matched_count", nullable = false)
	private long matchedCount = 0;

	@Column(name = "amount_mismatch_count", nullable = false)
	private long amountMismatchCount = 0;

	@Column(name = "missing_in_database_count", nullable = false)
	private long missingInDatabaseCount = 0;

	@Column(name = "started_at")
	private LocalDateTime startedAt;

	@Column(name = "completed_at")
	private LocalDateTime completedAt;

	@Column(name = "created_at", nullable = false, updatable = false)
	private LocalDateTime createdAt;

	@PrePersist
	protected void onCreate() {
		if (createdAt == null) {
			createdAt = LocalDateTime.now();
		}
	}

}