package com.delfino.expensetracker.dto.chat;

import com.delfino.expensetracker.model.ChatMessage;

import java.util.List;
import java.util.Map;

public record ChatHistoryResponse(
        List<ChatMessage> messages,
        Map<String, ExpenseCard> expenses,
        boolean hasMore,
        long total) {}
