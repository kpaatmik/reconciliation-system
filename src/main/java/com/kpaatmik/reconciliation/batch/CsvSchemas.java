package com.kpaatmik.reconciliation.batch;

import java.util.List;

public final class CsvSchemas {

	private CsvSchemas() {
	}

	public static final List<String> TRANSACTION_HEADERS = List.of("transaction_id", "account_number",
			"transaction_date", "amount", "currency", "transaction_type", "description");
}
