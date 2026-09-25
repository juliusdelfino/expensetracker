package com.delfino.expensetracker.dto.expense;

import com.delfino.expensetracker.model.ExpenseItem;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ExpenseItemExportDto(
        String itemName,
        BigDecimal quantity,
        BigDecimal unitPrice,
        BigDecimal adjustment,   // null when zero — omitted by @JsonInclude
        BigDecimal totalPrice
) {
    public static ExpenseItemExportDto from(ExpenseItem i) {
        BigDecimal adj = i.getAdjustment();
        return new ExpenseItemExportDto(
                i.getItemName(),
                i.getQuantity(),
                i.getUnitPrice(),
                adj != null && adj.compareTo(BigDecimal.ZERO) != 0 ? adj : null,
                i.getTotalPrice()
        );
    }
}

