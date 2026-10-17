package com.kpaatmik.reconciliation.processor;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import com.kpaatmik.reconciliation.dto.BaselineCsvRecord;

class BaselineRecordProcessorTest {
    private final BaselineRecordProcessor processor = new BaselineRecordProcessor();
    private BaselineCsvRecord row(String id, String account, String date, String amount, String currency, String type) {
        return new BaselineCsvRecord(2,id,account,date,amount,currency,type,"memo");
    }
    @Test void acceptsValidRecordIncludingNegativeAmount() {
        var result = processor.process(row(" T1 "," A1 ","2026-01-02","-12.30","USD"," DEBIT "));
        assertTrue(result.isValid());
        assertEquals("T1", result.transaction().getTransactionId());
        assertEquals("A1", result.transaction().getAccountNumber());
        assertEquals(new java.math.BigDecimal("-12.30"), result.transaction().getAmount());
        assertEquals("DEBIT", result.transaction().getTransactionType());
        assertNull(result.errorCode());
    }
    @Test void rejectsMissingRequiredFields() {
        assertInvalid(row(" ","A","2026-01-01","1.00","USD","CREDIT"), "transaction_id");
        assertInvalid(row("T",null,"2026-01-01","1.00","USD","CREDIT"), "account_number");
        assertInvalid(row("T","A","","1.00","USD","CREDIT"), null);
        assertInvalid(row("T","A","2026-01-01"," ","USD","CREDIT"), "amount");
        assertInvalid(row("T","A","2026-01-01","1.00",null,"CREDIT"), "currency");
        assertInvalid(row("T","A","2026-01-01","1.00","USD"," "), "transaction_type");
    }
    @Test void rejectsMalformedDateAmountScalePrecisionAndCurrency() {
        assertInvalid(row("T","A","bad-date","1.00","USD","CREDIT"), null);
        assertInvalid(row("T","A","2026-01-01","abc","USD","CREDIT"), null);
        assertInvalid(row("T","A","2026-01-01","1.001","USD","CREDIT"), null);
        assertInvalid(row("T","A","2026-01-01","12345678901234.00","USD","CREDIT"), null);
        assertInvalid(row("T","A","2026-01-01","1.00","US","CREDIT"), null);
    }
    private void assertInvalid(BaselineCsvRecord row, String message) {
        var result = processor.process(row);
        assertFalse(result.isValid());
        assertNull(result.transaction());
        assertEquals("INVALID_BASELINE_RECORD", result.errorCode());
        assertNotNull(result.errorMessage());
        if (message != null) assertTrue(result.errorMessage().contains(message));
    }
}
