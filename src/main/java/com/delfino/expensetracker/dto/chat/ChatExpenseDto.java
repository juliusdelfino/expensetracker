package com.delfino.expensetracker.dto.chat;
import java.math.BigDecimal;
import java.util.List;
/**
 * A single expense entry returned by the LLM in a chat response.
 * {@code transactionDatetime} is kept as a {@code String} so that a malformed
 * value from the model does not abort the entire parse; the service layer
 * handles the conversion with a try/catch.
 */
public record ChatExpenseDto(
        String transactionDatetime,
        BigDecimal amount,
        String currency,
        String category,
        String notes,
        Long storeId,
        List<ChatExpenseItemDto> items) {
}
