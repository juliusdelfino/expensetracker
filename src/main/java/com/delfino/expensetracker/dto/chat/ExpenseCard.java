package com.delfino.expensetracker.dto.chat;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ExpenseCard(
        Long id,
        String urlId,
        BigDecimal amount,
        String currency,
        String category,
        String notes,
        LocalDateTime transactionDatetime) {}
