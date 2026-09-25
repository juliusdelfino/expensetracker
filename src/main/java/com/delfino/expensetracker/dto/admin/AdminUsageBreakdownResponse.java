package com.delfino.expensetracker.dto.admin;

public record AdminUsageBreakdownResponse(
        String provider,
        String modelId,
        String modelLabel,
        int userCount,
        int usageCount,
        int quota,
        long exceededUsers,
        long nearQuotaUsers) {
}

