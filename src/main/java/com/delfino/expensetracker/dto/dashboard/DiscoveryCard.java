package com.delfino.expensetracker.dto.dashboard;

import java.math.BigDecimal;
import java.util.List;

public record DiscoveryCard(
        String type,
        String country,
        String countryName,
        String city,
        String locationLabel,
        String month,
        int year,
        String yearMonth,
        BigDecimal total,
        int count,
        int daysStayed,
        String originalCurrency,
        BigDecimal originalTotal,
        List<String> shops,
        String title,
        List<DiscoveryCardExpense> topExpenses) {

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String type;
        private String country;
        private String countryName;
        private String city;
        private String locationLabel;
        private String month;
        private int year;
        private String yearMonth;
        private BigDecimal total;
        private int count;
        private int daysStayed;
        private String originalCurrency;
        private BigDecimal originalTotal;
        private List<String> shops;
        private String title;
        private List<DiscoveryCardExpense> topExpenses;

        public Builder type(String v) { this.type = v; return this; }
        public Builder country(String v) { this.country = v; return this; }
        public Builder countryName(String v) { this.countryName = v; return this; }
        public Builder city(String v) { this.city = v; return this; }
        public Builder locationLabel(String v) { this.locationLabel = v; return this; }
        public Builder month(String v) { this.month = v; return this; }
        public Builder year(int v) { this.year = v; return this; }
        public Builder yearMonth(String v) { this.yearMonth = v; return this; }
        public Builder total(BigDecimal v) { this.total = v; return this; }
        public Builder count(int v) { this.count = v; return this; }
        public Builder daysStayed(int v) { this.daysStayed = v; return this; }
        public Builder originalCurrency(String v) { this.originalCurrency = v; return this; }
        public Builder originalTotal(BigDecimal v) { this.originalTotal = v; return this; }
        public Builder shops(List<String> v) { this.shops = v; return this; }
        public Builder title(String v) { this.title = v; return this; }
        public Builder topExpenses(List<DiscoveryCardExpense> v) { this.topExpenses = v; return this; }

        public DiscoveryCard build() {
            return new DiscoveryCard(type, country, countryName, city, locationLabel, month, year,
                    yearMonth, total, count, daysStayed, originalCurrency, originalTotal, shops,
                    title, topExpenses);
        }
    }
}
