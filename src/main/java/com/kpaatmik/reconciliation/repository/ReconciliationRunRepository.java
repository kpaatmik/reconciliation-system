package com.kpaatmik.reconciliation.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.kpaatmik.reconciliation.entity.ReconciliationRun;

public interface ReconciliationRunRepository extends JpaRepository<ReconciliationRun, UUID> {
}