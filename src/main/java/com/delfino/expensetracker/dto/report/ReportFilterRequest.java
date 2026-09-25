package com.delfino.expensetracker.dto.report;

import jakarta.validation.constraints.Size;

import java.util.List;

public record ReportFilterRequest(
        @Size(max = 20) String startDate,
        @Size(max = 20) String endDate,
        @Size(max = 100) String category,
        @Size(max = 100) String country,
        @Size(max = 100) String city,
        @Size(max = 100) String storeName,
        @Size(max = 1000) String search,
        List<@Size(max = 100) String> searchKeywords
) {
}

