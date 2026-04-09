package com.delfino.expensetracker.dto.dashboard;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record TopExpense(
        Long id,
        String displayName,
        BigDecimal amount,
        String currency,
        BigDecimal amountInBase,
        LocalDateTime transactionDatetime,
        String category) {}
