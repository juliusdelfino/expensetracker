package com.delfino.expensetracker.dto.ai;

public record AiQuotaExceededResponse(
        String error,
        String code,
        String type,
        int usageCount,
        int quota,
        int requestedUnits,
        int remaining) {
}

