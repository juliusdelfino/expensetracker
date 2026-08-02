package com.delfino.expensetracker.dto.user;

import com.delfino.expensetracker.model.Expense;
import com.delfino.expensetracker.model.ExpenseType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record TrashExpenseResponse(
        String expenseId,
        ExpenseType type,
        LocalDateTime transactionDatetime,
        BigDecimal amount,
        String currency,
        BigDecimal amountInBase,
        String category,
        boolean hasReceipt,
        int attachmentCount,
        LocalDateTime deletedAt
) {
    public static TrashExpenseResponse from(Expense e) {
        return new TrashExpenseResponse(
                e.getUrlId(),
                e.getType(),
                e.getTransactionDatetime(),
                e.getAmount(),
                e.getCurrency(),
                e.getAmountInBase(),
                e.getCategory(),
                e.getImagePath() != null && !e.getImagePath().isBlank(),
                e.getAttachments() != null ? e.getAttachments().size() : 0,
                e.getUpdatedAt()   // deletedAt approximated by updatedAt until field is added
        );
    }
}

