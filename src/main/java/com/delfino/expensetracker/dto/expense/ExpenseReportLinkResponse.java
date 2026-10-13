package com.delfino.expensetracker.dto.expense;

import java.time.LocalDateTime;

public record ExpenseReportLinkResponse(
        Long id,
        String title,
        String groupBy,
        LocalDateTime createdAt,
        int expenseCount) {
}

