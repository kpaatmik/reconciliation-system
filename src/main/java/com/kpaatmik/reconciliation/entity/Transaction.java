package com.kpaatmik.reconciliation.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "reconciliation_transaction")
@IdClass(TransactionId.class)
@Getter
@Setter
@NoArgsConstructor
public class Transaction {

	@Id
	@Column(name = "run_id", nullable = false, updatable = false)
	private UUID runId;

	@Id
	@Column(name = "transaction_id", nullable = false, length = 100)
	private String transactionId;

	@Column(name = "account_number", nullable = false, length = 50)
	private String accountNumber;

	@Column(name = "transaction_date", nullable = false)
	private LocalDate transactionDate;

	@Column(name = "amount", nullable = false, precision = 15, scale = 2)
	private BigDecimal amount;

	@Column(name = "currency", nullable = false, length = 3)
	private String currency;

	@Column(name = "transaction_type", nullable = false, length = 30)
	private String transactionType;

	@Column(name = "description", length = 500)
	private String description;
}