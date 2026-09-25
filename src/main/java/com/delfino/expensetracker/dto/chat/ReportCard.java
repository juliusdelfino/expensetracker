package com.delfino.expensetracker.dto.chat;

import com.delfino.expensetracker.model.ReportGroupBy;

import java.time.LocalDateTime;

public record ReportCard(
        Long id,
        String title,
        String description,
        ReportGroupBy groupBy,
        int expenseCount,
        LocalDateTime createdAt
) {
}

