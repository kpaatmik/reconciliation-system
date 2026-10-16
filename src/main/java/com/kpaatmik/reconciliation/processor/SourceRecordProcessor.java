
package com.kpaatmik.reconciliation.processor;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.springframework.batch.infrastructure.item.ItemProcessor;
import org.springframework.stereotype.Component;

import com.kpaatmik.reconciliation.dto.SourceCsvRecord;
import com.kpaatmik.reconciliation.dto.SourceProcessedRecord;

@Component
public class SourceRecordProcessor implements ItemProcessor<SourceCsvRecord, SourceProcessedRecord> {

	@Override
	public SourceProcessedRecord process(SourceCsvRecord row) {

		try {
			String transactionId = required(row.transactionId(), "transaction_id");

			required(row.accountNumber(), "account_number");
			required(row.transactionDate(), "transaction_date");
			required(row.currency(), "currency");
			required(row.transactionType(), "transaction_type");

			LocalDate.parse(row.transactionDate().trim());

			String amountText = required(row.amount(), "amount");
			BigDecimal amount = new BigDecimal(amountText);

			if (amount.scale() > 2 || amount.precision() > 15) {
				throw new IllegalArgumentException("amount must fit NUMERIC(15,2)");
			}

			if (row.currency().trim().length() != 3) {
				throw new IllegalArgumentException("currency must contain 3 characters");
			}

			return new SourceProcessedRecord(row, transactionId, amount, null, null);

		} catch (IllegalArgumentException ex) {
			return new SourceProcessedRecord(row, null, null, "INVALID_SOURCE_RECORD", ex.getMessage());
		}
	}

	private String required(String value, String fieldName) {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException(fieldName + " is required");
		}

		return value.trim();
	}
}
