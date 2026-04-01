package com.delfino.expensetracker.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "exchange_rate_cache")
public class ExchangeRateCache {
    @Id
    @Column(name = "cache_key")
    private String key;

    private BigDecimal rate;
    private LocalDateTime fetchedAt;

    public ExchangeRateCache() {}

    public ExchangeRateCache(String key, BigDecimal rate, LocalDateTime fetchedAt) {
        this.key = key;
        this.rate = rate;
        this.fetchedAt = fetchedAt;
    }

    public String getKey() { return key; }
    public void setKey(String key) { this.key = key; }

    public BigDecimal getRate() { return rate; }
    public void setRate(BigDecimal rate) { this.rate = rate; }

    public LocalDateTime getFetchedAt() { return fetchedAt; }
    public void setFetchedAt(LocalDateTime fetchedAt) { this.fetchedAt = fetchedAt; }
}
