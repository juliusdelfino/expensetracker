package com.delfino.expensetracker.dto.expense;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * DTO for adding or updating an expense line item. Keeps the persistence entity
 * out of the API contract.
 */
public class ExpenseItemRequest {

    @Size(max = 100)
    private String itemName;

    @Digits(integer = 4, fraction = 4)
    private BigDecimal quantity;

    @Digits(integer = 10, fraction = 4)
    private BigDecimal unitPrice;

    @Digits(integer = 10, fraction = 5)
    private BigDecimal adjustment;

    public String getItemName() { return itemName; }
    public void setItemName(String itemName) { this.itemName = itemName; }

    public BigDecimal getQuantity() { return quantity; }
    public void setQuantity(BigDecimal quantity) { this.quantity = quantity; }

    public BigDecimal getUnitPrice() { return unitPrice; }
    public void setUnitPrice(BigDecimal unitPrice) { this.unitPrice = unitPrice; }

    public BigDecimal getAdjustment() { return adjustment; }
    public void setAdjustment(BigDecimal adjustment) { this.adjustment = adjustment; }
}

