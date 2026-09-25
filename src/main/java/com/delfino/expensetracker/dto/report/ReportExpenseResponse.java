package com.delfino.expensetracker.dto.report;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record ReportExpenseResponse(
        long id,
        String urlId,
        LocalDateTime transactionDatetime,
        BigDecimal amount,
        String currency,
        BigDecimal amountInBase,
        String category,
        String notes,
        List<String> tags,
        String storeName,
        String locationLabel,
        boolean deleted
) {
}

