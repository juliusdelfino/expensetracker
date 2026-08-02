package com.delfino.expensetracker.dto.ai;

public record AiUsageLineDto(
        String type,
        int usageCount,
        int quota,
        int remaining,
        boolean allowed) {
}

