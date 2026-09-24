package com.delfino.expensetracker.dto.report;

import com.delfino.expensetracker.model.ReportGroupBy;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ReportSummaryResponse(
        Long id,
        String title,
        String description,
        ReportGroupBy groupBy,
        int expenseCount,
        BigDecimal totalAmount,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}

