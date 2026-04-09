package com.delfino.expensetracker.dto.expense;

import com.delfino.expensetracker.model.ExpenseStatus;
import com.delfino.expensetracker.model.ExpenseType;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record EnrichedExpense(
        long id,
        long userId,
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
        String imagePath,
        List<String> attachments,
        boolean deleted,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        LocalDateTime scannedAt,
        String urlId,
        String storeName,
        String country,
        String countryName,
        String displayName,
        List<MatchingItem> matchingItems) {

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private long id;
        private long userId;
        private ExpenseType type;
        private LocalDateTime transactionDatetime;
        private BigDecimal amount;
        private String currency;
        private BigDecimal amountInBase;
        private BigDecimal exchangeRate;
        private String receiptNumber;
        private String category;
        private List<String> tags;
        private String notes;
        private ExpenseStatus status;
        private String imagePath;
        private List<String> attachments;
        private boolean deleted;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
        private LocalDateTime scannedAt;
        private String urlId;
        private String storeName;
        private String country;
        private String countryName;
        private String displayName;
        private List<MatchingItem> matchingItems;

        public Builder id(long v) { this.id = v; return this; }
        public Builder userId(long v) { this.userId = v; return this; }
        public Builder type(ExpenseType v) { this.type = v; return this; }
        public Builder transactionDatetime(LocalDateTime v) { this.transactionDatetime = v; return this; }
        public Builder amount(BigDecimal v) { this.amount = v; return this; }
        public Builder currency(String v) { this.currency = v; return this; }
        public Builder amountInBase(BigDecimal v) { this.amountInBase = v; return this; }
        public Builder exchangeRate(BigDecimal v) { this.exchangeRate = v; return this; }
        public Builder receiptNumber(String v) { this.receiptNumber = v; return this; }
        public Builder category(String v) { this.category = v; return this; }
        public Builder tags(List<String> v) { this.tags = v; return this; }
        public Builder notes(String v) { this.notes = v; return this; }
        public Builder status(ExpenseStatus v) { this.status = v; return this; }
        public Builder imagePath(String v) { this.imagePath = v; return this; }
        public Builder attachments(List<String> v) { this.attachments = v; return this; }
        public Builder deleted(boolean v) { this.deleted = v; return this; }
        public Builder createdAt(LocalDateTime v) { this.createdAt = v; return this; }
        public Builder updatedAt(LocalDateTime v) { this.updatedAt = v; return this; }
        public Builder scannedAt(LocalDateTime v) { this.scannedAt = v; return this; }
        public Builder urlId(String v) { this.urlId = v; return this; }
        public Builder storeName(String v) { this.storeName = v; return this; }
        public Builder country(String v) { this.country = v; return this; }
        public Builder countryName(String v) { this.countryName = v; return this; }
        public Builder displayName(String v) { this.displayName = v; return this; }
        public Builder matchingItems(List<MatchingItem> v) { this.matchingItems = v; return this; }

        public EnrichedExpense build() {
            return new EnrichedExpense(id, userId, type, transactionDatetime, amount, currency,
                    amountInBase, exchangeRate, receiptNumber, category, tags, notes, status,
                    imagePath, attachments, deleted, createdAt, updatedAt, scannedAt, urlId,
                    storeName, country, countryName, displayName, matchingItems);
        }
    }
}
