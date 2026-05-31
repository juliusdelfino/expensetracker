package com.delfino.expensetracker.dto.expense;

import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * DTO for creating or updating a manual expense. Keeps the persistence entity
 * out of the API contract.
 */
public class ExpenseRequest {

    private LocalDateTime transactionDatetime;
    private BigDecimal amount;

    private String currency;

    @Size(max = 50)
    private String receiptNumber;

    @Size(max = 50)
    private String category;

    private List<String> tags;

    @Size(max = 500)
    private String notes;

    private BigDecimal exchangeRate;

    private List<String> attachments;

    public LocalDateTime getTransactionDatetime() { return transactionDatetime; }
    public void setTransactionDatetime(LocalDateTime transactionDatetime) { this.transactionDatetime = transactionDatetime; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public String getReceiptNumber() { return receiptNumber; }
    public void setReceiptNumber(String receiptNumber) { this.receiptNumber = receiptNumber; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public List<String> getTags() { return tags; }
    public void setTags(List<String> tags) { this.tags = tags; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public BigDecimal getExchangeRate() { return exchangeRate; }
    public void setExchangeRate(BigDecimal exchangeRate) { this.exchangeRate = exchangeRate; }

    public List<String> getAttachments() { return attachments; }
    public void setAttachments(List<String> attachments) { this.attachments = attachments; }
}


