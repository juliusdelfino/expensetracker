package com.delfino.expensetracker.dto.report;

import jakarta.validation.constraints.Size;

public record ReportFilterRequest(
        @Size(max = 20) String startDate,
        @Size(max = 20) String endDate,
        @Size(max = 100) String category,
        @Size(max = 100) String country,
        @Size(max = 100) String city,
        @Size(max = 100) String storeName,
        @Size(max = 100) String search
) {
}

