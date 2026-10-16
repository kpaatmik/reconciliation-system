package com.kpaatmik.reconciliation.processor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;

import org.springframework.batch.infrastructure.item.ItemProcessor;
import org.springframework.stereotype.Component;

import com.kpaatmik.reconciliation.dto.BaselineCsvRecord;
import com.kpaatmik.reconciliation.dto.BaselineProcessedRecord;
import com.kpaatmik.reconciliation.entity.Transaction;

@Component
public class BaselineRecordProcessor implements ItemProcessor<BaselineCsvRecord, BaselineProcessedRecord> {

	@Override
	public BaselineProcessedRecord process(BaselineCsvRecord row) {

		try {
			String transactionId = required(row.transactionId(), "transaction_id");

			String accountNumber = required(row.accountNumber(), "account_number");

			String dateText = required(row.transactionDate(), "transaction_date");

			String amountText = required(row.amount(), "amount");

			String currency = required(row.currency(), "currency");

			String transactionType = required(row.transactionType(), "transaction_type");

			LocalDate transactionDate = LocalDate.parse(dateText);
			BigDecimal amount = new BigDecimal(amountText);

			if (amount.scale() > 2) {
				throw new IllegalArgumentException("amount must have at most 2 decimal places");
			}

			if (amount.precision() > 15) {
				throw new IllegalArgumentException("amount exceeds NUMERIC(15,2)");
			}

			if (currency.length() != 3) {
				throw new IllegalArgumentException("currency must contain 3 characters");
			}

			Transaction transaction = new Transaction();
			transaction.setTransactionId(transactionId);
			transaction.setAccountNumber(accountNumber);
			transaction.setTransactionDate(transactionDate);
			transaction.setAmount(amount);
			transaction.setCurrency(currency);
			transaction.setTransactionType(transactionType);
			transaction.setDescription(row.description());

			return new BaselineProcessedRecord(row, transaction, null, null);

		} catch (IllegalArgumentException | DateTimeParseException ex) {
			return new BaselineProcessedRecord(row, null, "INVALID_BASELINE_RECORD", ex.getMessage());
		}
	}

	private String required(String value, String fieldName) {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException(fieldName + " is required");
		}
		return value.trim();
	}
}
