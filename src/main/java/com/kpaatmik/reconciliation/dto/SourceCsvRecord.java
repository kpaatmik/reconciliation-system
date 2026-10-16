package com.kpaatmik.reconciliation.dto;

public record SourceCsvRecord(long lineNumber, String transactionId, String accountNumber, String transactionDate,
		String amount, String currency, String transactionType, String description) {

}
