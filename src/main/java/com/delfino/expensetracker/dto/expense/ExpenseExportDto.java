package com.delfino.expensetracker.dto.expense;

import com.delfino.expensetracker.model.Expense;
import com.delfino.expensetracker.model.ExpenseStatus;
import com.delfino.expensetracker.model.ExpenseType;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Export-safe view of an expense — strips all internal IDs (numeric id, userId, storeId)
 * and server-side implementation details (imagePath) before exposing to the user.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ExpenseExportDto(
        String expenseId,                   // urlId — no numeric DB id exposed
        ExpenseType type,
        LocalDateTime transactionDatetime,
        BigDecimal amount,
        String currency,
        BigDecimal amountInBase,
        BigDecimal exchangeRate,
        String receiptNumber,
        String category,
        List<String> tags,
        String notes,
        ExpenseStatus status,
        List<String> attachments,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        LocalDateTime scannedAt
) {
    public static ExpenseExportDto from(Expense e) {
        return new ExpenseExportDto(
                e.getUrlId(),
                e.getType(),
                e.getTransactionDatetime(),
                e.getAmount(),
                e.getCurrency(),
                e.getAmountInBase(),
                e.getExchangeRate(),
                e.getReceiptNumber(),
                e.getCategory(),
                e.getTags() != null && !e.getTags().isEmpty() ? e.getTags() : null,
                e.getNotes(),
                e.getStatus(),
                e.getAttachments() != null && !e.getAttachments().isEmpty() ? e.getAttachments() : null,
                e.getCreatedAt(),
                e.getUpdatedAt(),
                e.getScannedAt()
        );
    }
}
