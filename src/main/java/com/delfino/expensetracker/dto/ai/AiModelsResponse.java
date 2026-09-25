package com.delfino.expensetracker.dto.ai;

import java.util.List;

public record AiModelsResponse(
        String defaultChatModel,
        String defaultOcrModel,
        List<AiModelOptionResponse> models) {
}

