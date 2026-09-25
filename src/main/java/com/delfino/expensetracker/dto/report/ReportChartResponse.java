package com.delfino.expensetracker.dto.report;

import java.math.BigDecimal;
import java.util.List;

public record ReportChartResponse(
        String id,
        String title,
        String type,
        List<String> labels,
        List<BigDecimal> values
) {
}

