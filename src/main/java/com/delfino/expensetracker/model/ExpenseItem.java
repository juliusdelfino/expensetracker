package com.delfino.expensetracker.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;


@Entity
@Table(name = "expense_items")
public class ExpenseItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    private long expenseId;
    private String itemName;
    @Column(precision = 38, scale = 4)
    private BigDecimal quantity;
    private BigDecimal unitPrice;
    private BigDecimal adjustment;  // extra charge (+) or discount (-) on this line item
    private boolean deleted;

    public ExpenseItem() {}

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    public long getExpenseId() { return expenseId; }
    public void setExpenseId(long expenseId) { this.expenseId = expenseId; }

    public String getItemName() { return itemName; }
    public void setItemName(String itemName) { this.itemName = itemName; }

    public BigDecimal getQuantity() { return quantity; }
    public void setQuantity(BigDecimal quantity) { this.quantity = quantity; }

    public BigDecimal getUnitPrice() { return unitPrice; }
    public void setUnitPrice(BigDecimal unitPrice) { this.unitPrice = unitPrice; }

    public BigDecimal getAdjustment() { return adjustment != null ? adjustment : BigDecimal.ZERO; }
    public void setAdjustment(BigDecimal adjustment) { this.adjustment = adjustment; }

    public BigDecimal getTotalPrice() { return quantity.multiply(unitPrice).add(getAdjustment()); }

    public boolean isDeleted() { return deleted; }
    public void setDeleted(boolean deleted) { this.deleted = deleted; }
}
