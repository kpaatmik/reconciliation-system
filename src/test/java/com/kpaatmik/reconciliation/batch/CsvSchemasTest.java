package com.kpaatmik.reconciliation.batch;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
class CsvSchemasTest {
    @Test void headersAreCorrectAndImmutable() {
        assertEquals(java.util.List.of("transaction_id","account_number","transaction_date","amount","currency","transaction_type","description"), CsvSchemas.TRANSACTION_HEADERS);
        assertThrows(UnsupportedOperationException.class,()->CsvSchemas.TRANSACTION_HEADERS.add("extra"));
    }
}
