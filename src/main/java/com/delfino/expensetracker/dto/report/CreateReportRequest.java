package com.delfino.expensetracker.dto.report;

import com.delfino.expensetracker.model.ReportGroupBy;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CreateReportRequest(
        @Size(max = 255) String title,
        @Size(max = 5000) String description,
        List<Long> expenseIds,
        JsonNode chartDefinitions,
        @NotNull ReportGroupBy groupBy,
        @Size(max = 20) String startDate,
        @Size(max = 20) String endDate,
        @Size(max = 100) String category,
        @Size(max = 100) String country,
        @Size(max = 100) String city,
        @Size(max = 100) String storeName,
        @Size(max = 100) String search,
        JsonNode filterSnapshot,
        JsonNode insights
) {

    public ReportFilterRequest toFilterRequest() {
        return new ReportFilterRequest(startDate, endDate, category, country, city, storeName, search);
    }
}


