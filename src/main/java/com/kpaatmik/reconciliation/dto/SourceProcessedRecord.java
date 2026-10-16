package com.kpaatmik.reconciliation.dto;

import java.math.BigDecimal;

public record SourceProcessedRecord(SourceCsvRecord source, String transactionId, BigDecimal amount, String errorCode,
		String errorMessage) {
	public boolean isValid() {
		return errorCode == null;
	}
}
