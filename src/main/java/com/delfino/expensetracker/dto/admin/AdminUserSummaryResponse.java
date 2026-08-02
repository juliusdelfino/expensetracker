package com.delfino.expensetracker.dto.admin;

import com.delfino.expensetracker.dto.ai.AiUsageLineDto;

public record AdminUserSummaryResponse(
        Long id,
        String username,
        String email,
        String role,
        String selectedAiModel,
        String effectiveChatModel,
        String effectiveChatProvider,
        String effectiveOcrModel,
        String effectiveOcrProvider,
        AiUsageLineDto chat,
        AiUsageLineDto ocr,
        boolean chatAllowed,
        boolean ocrAllowed) {
}

