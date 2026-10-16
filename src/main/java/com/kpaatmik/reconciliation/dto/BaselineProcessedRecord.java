package com.kpaatmik.reconciliation.dto;

import com.kpaatmik.reconciliation.entity.Transaction;

public record BaselineProcessedRecord(BaselineCsvRecord source, Transaction transaction, String errorCode,
		String errorMessage) {
	public boolean isValid() {
		return transaction != null;
	}
}
