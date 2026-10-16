
package com.kpaatmik.reconciliation.repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.kpaatmik.reconciliation.entity.Transaction;
import com.kpaatmik.reconciliation.entity.TransactionId;

public interface TransactionRepository extends JpaRepository<Transaction, TransactionId> {

	List<Transaction> findAllByRunIdAndTransactionIdIn(UUID runId, Collection<String> transactionIds);
}
