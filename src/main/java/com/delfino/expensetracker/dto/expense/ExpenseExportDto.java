package com.delfino.expensetracker.dto.expense;

import com.delfino.expensetracker.model.Expense;
import com.delfino.expensetracker.model.ExpenseItem;
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
        String expenseId,
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
        List<ExpenseItemExportDto> items,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        LocalDateTime scannedAt
) {
    /** Build DTO without items (e.g. for list exports where items aren't loaded). */
    public static ExpenseExportDto from(Expense e) {
        return fromWithItems(e, List.of());
    }

    /** Build DTO with pre-loaded items. */
    public static ExpenseExportDto fromWithItems(Expense e, List<ExpenseItem> expenseItems) {
        List<ExpenseItemExportDto> itemDtos = expenseItems == null || expenseItems.isEmpty()
                ? null
                : expenseItems.stream().map(ExpenseItemExportDto::from).toList();
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
                itemDtos,
                e.getCreatedAt(),
                e.getUpdatedAt(),
                e.getScannedAt()
        );
    }
}
