package com.delfino.expensetracker.dto.ai;

public record AiUsageStatusResponse(
		String monthYear,
		String selectedAiModel,
		String effectiveChatModel,
		String effectiveOcrModel,
		AiUsageLineDto chat,
		AiUsageLineDto ocr,
		boolean chatAllowed,
		boolean ocrAllowed) {
}

