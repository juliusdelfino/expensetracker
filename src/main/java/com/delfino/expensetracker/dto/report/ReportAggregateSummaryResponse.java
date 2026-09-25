package com.delfino.expensetracker.dto.report;

import java.math.BigDecimal;

public record ReportAggregateSummaryResponse(
		BigDecimal totalAmount,
		int expenseCount,
		BigDecimal averageAmount,
		BigDecimal minAmount,
		BigDecimal maxAmount,
		String topCategory,
		String topLocation,
		int activeDaysCount,
		String coveredStartDate,
		String coveredEndDate
) {
}

