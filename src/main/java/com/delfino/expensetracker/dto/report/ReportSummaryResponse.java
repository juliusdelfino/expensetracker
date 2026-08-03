package com.delfino.expensetracker.dto.report;

import com.delfino.expensetracker.model.ReportGroupBy;

import java.time.LocalDateTime;

public record ReportSummaryResponse(
        Long id,
        String title,
        String description,
        ReportGroupBy groupBy,
        int expenseCount,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}

