package com.delfino.expensetracker.dto.dashboard;

import java.math.BigDecimal;

public record DiscoveryCardExpense(
        Long id,
        String displayName,
        BigDecimal amount,
        String currency,
        String urlId,
        BigDecimal amountInBase) {}
