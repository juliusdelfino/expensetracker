package com.delfino.expensetracker.dto.admin;

import java.util.List;

public record AdminAiUsageOverviewResponse(
        String monthYear,
        int totalUsers,
        int adminUsers,
        int totalChatUsage,
        int totalChatQuota,
        int totalOcrUsage,
        int totalOcrQuota,
        long chatNearQuotaUsers,
        long ocrNearQuotaUsers,
        long chatExceededUsers,
        long ocrExceededUsers,
        List<AdminUsageBreakdownResponse> chatByModel,
        List<AdminUsageBreakdownResponse> ocrByModel) {
}


