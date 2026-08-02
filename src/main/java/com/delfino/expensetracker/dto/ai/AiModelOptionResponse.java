package com.delfino.expensetracker.dto.ai;

public record AiModelOptionResponse(
        String id,
        String label,
        String provider,
        boolean supportsChat,
        boolean supportsOcr) {
}

