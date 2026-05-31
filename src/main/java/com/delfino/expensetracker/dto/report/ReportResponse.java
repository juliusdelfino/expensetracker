package com.delfino.expensetracker.dto.report;

import com.delfino.expensetracker.model.ReportGroupBy;
import com.fasterxml.jackson.databind.JsonNode;

import java.time.LocalDateTime;
import java.util.List;

public record ReportResponse(
        Long id,
        String title,
        String description,
        List<Long> expenseIds,
        JsonNode chartDefinitions,
        ReportGroupBy groupBy,
        JsonNode filterSnapshot,
        ReportAggregateSummaryResponse summary,
        List<ReportChartResponse> charts,
        List<String> insights,
        List<ReportExpenseResponse> expenses,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}


