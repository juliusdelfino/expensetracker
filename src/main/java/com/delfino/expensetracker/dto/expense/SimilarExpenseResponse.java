package com.delfino.expensetracker.dto.expense;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record SimilarExpenseResponse(
        String urlId,
        String imagePath,
        String storeName,
        String storeAddress,
        BigDecimal amount,
        String currency,
        LocalDateTime transactionDatetime) {
}

