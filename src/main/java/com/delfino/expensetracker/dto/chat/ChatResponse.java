package com.delfino.expensetracker.dto.chat;

import com.delfino.expensetracker.model.ChatMessage;

import java.util.List;

public record ChatResponse(ChatMessage message, List<ExpenseCard> expenseCards) {}
