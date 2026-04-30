package com.delfino.expensetracker.model;

import jakarta.persistence.*;
import org.hibernate.annotations.BatchSize;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "expenses")
public class Expense {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    private long userId;

    @Enumerated(EnumType.STRING)
    private ExpenseType type;

    private LocalDateTime transactionDatetime;
    private BigDecimal amount;
    private String currency;
    private BigDecimal amountInBase;
    private BigDecimal exchangeRate;
    private String receiptNumber;
    private String category;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "expense_tags", joinColumns = @JoinColumn(name = "expense_id"))
    @Column(name = "tag")
    @BatchSize(size = 50)
    private List<String> tags = new ArrayList<>();

    @Column(length = 2000)
    private String notes;

    @Enumerated(EnumType.STRING)
    private ExpenseStatus status;

    private String imagePath;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "expense_attachments", joinColumns = @JoinColumn(name = "expense_id"))
    @Column(name = "attachment")
    @BatchSize(size = 50)
    private List<String> attachments = new ArrayList<>();

    private boolean deleted;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime scannedAt;

    private Long storeId;

    private String urlId;

    public Expense() {}

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    public long getUserId() { return userId; }
    public void setUserId(long userId) { this.userId = userId; }

    public ExpenseType getType() { return type; }
    public void setType(ExpenseType type) { this.type = type; }

    public LocalDateTime getTransactionDatetime() { return transactionDatetime; }
    public void setTransactionDatetime(LocalDateTime transactionDatetime) { this.transactionDatetime = transactionDatetime; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public BigDecimal getAmountInBase() { return amountInBase; }
    public void setAmountInBase(BigDecimal amountInBase) { this.amountInBase = amountInBase; }

    public BigDecimal getExchangeRate() { return exchangeRate; }
    public void setExchangeRate(BigDecimal exchangeRate) { this.exchangeRate = exchangeRate; }

    public String getReceiptNumber() { return receiptNumber; }
    public void setReceiptNumber(String receiptNumber) { this.receiptNumber = receiptNumber; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public List<String> getTags() { return tags; }
    public void setTags(List<String> tags) { this.tags = tags; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public ExpenseStatus getStatus() { return status; }
    public void setStatus(ExpenseStatus status) { this.status = status; }

    public String getImagePath() { return imagePath; }
    public void setImagePath(String imagePath) { this.imagePath = imagePath; }

    public List<String> getAttachments() { return attachments; }
    public void setAttachments(List<String> attachments) { this.attachments = attachments; }

    public boolean isDeleted() { return deleted; }
    public void setDeleted(boolean deleted) { this.deleted = deleted; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public LocalDateTime getScannedAt() { return scannedAt; }
    public void setScannedAt(LocalDateTime scannedAt) { this.scannedAt = scannedAt; }

    public Long getStoreId() { return storeId; }
    public void setStoreId(Long storeId) { this.storeId = storeId; }

    public String getUrlId() { return urlId; }
    public void setUrlId(String urlId) { this.urlId = urlId; }

    /**
     * Returns the base-currency amount, falling back to the original amount, then ZERO.
     * Eliminates the duplicate getBaseAmount(Expense) helper across controllers and services.
     */
    public BigDecimal getBaseAmountOrAmount() {
        return amountInBase != null ? amountInBase
                : (amount != null ? amount : BigDecimal.ZERO);
    }
}
