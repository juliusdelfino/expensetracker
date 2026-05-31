package com.delfino.expensetracker.dto.chat;
import java.util.List;
/**
 * The structured JSON payload returned by the LLM when it creates expenses
 * from a chat message. The outer wrapper holds an optional summary text and
 * the list of expense entries to persist.
 */
public record ChatExpenseResponseDto(
        String summary,
        List<ChatExpenseDto> expenses) {
}
