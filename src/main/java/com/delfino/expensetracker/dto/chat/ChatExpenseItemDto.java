package com.delfino.expensetracker.dto.chat;
import java.math.BigDecimal;
/**
 * A single line item within a chat-created expense, as returned by the LLM.
 */
public record ChatExpenseItemDto(
        String itemName,
        BigDecimal quantity,
        BigDecimal unitPrice,
        BigDecimal adjustment) {
}
