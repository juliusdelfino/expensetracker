package com.delfino.expensetracker.dto.dashboard;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;

public record DashboardResponse(
        Map<String, BigDecimal> monthlyTotals,
        Map<String, BigDecimal> weeklyTotals,
        Map<String, BigDecimal> annualTotals,
        Map<String, BigDecimal> categoryTotals,
        Map<String, BigDecimal> timeline,
        List<GeoPoint> geoData,
        List<GeoCountry> geoByCountry,
        List<TopShop> topShops,
        List<TopItem> topItems,
        List<DiscoveryCard> discoveryCards,
        Set<String> categories,
        int totalExpenses,
        String minDate,
        String maxDate,
        List<TopExpense> topExpenses,
        Map<String, Integer> perMonthTxCount,
        Map<String, String> perMonthTopCategory,
        Map<String, Integer> perYearTxCount,
        Map<String, String> perYearTopCategory) {

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private Map<String, BigDecimal> monthlyTotals;
        private Map<String, BigDecimal> weeklyTotals;
        private Map<String, BigDecimal> annualTotals;
        private Map<String, BigDecimal> categoryTotals;
        private Map<String, BigDecimal> timeline;
        private List<GeoPoint> geoData;
        private List<GeoCountry> geoByCountry;
        private List<TopShop> topShops;
        private List<TopItem> topItems;
        private List<DiscoveryCard> discoveryCards;
        private Set<String> categories;
        private int totalExpenses;
        private String minDate;
        private String maxDate;
        private List<TopExpense> topExpenses;
        private Map<String, Integer> perMonthTxCount;
        private Map<String, String> perMonthTopCategory;
        private Map<String, Integer> perYearTxCount;
        private Map<String, String> perYearTopCategory;

        public Builder monthlyTotals(Map<String, BigDecimal> v) { this.monthlyTotals = v; return this; }
        public Builder weeklyTotals(Map<String, BigDecimal> v) { this.weeklyTotals = v; return this; }
        public Builder annualTotals(Map<String, BigDecimal> v) { this.annualTotals = v; return this; }
        public Builder categoryTotals(Map<String, BigDecimal> v) { this.categoryTotals = v; return this; }
        public Builder timeline(Map<String, BigDecimal> v) { this.timeline = v; return this; }
        public Builder geoData(List<GeoPoint> v) { this.geoData = v; return this; }
        public Builder geoByCountry(List<GeoCountry> v) { this.geoByCountry = v; return this; }
        public Builder topShops(List<TopShop> v) { this.topShops = v; return this; }
        public Builder topItems(List<TopItem> v) { this.topItems = v; return this; }
        public Builder discoveryCards(List<DiscoveryCard> v) { this.discoveryCards = v; return this; }
        public Builder categories(Set<String> v) { this.categories = v; return this; }
        public Builder totalExpenses(int v) { this.totalExpenses = v; return this; }
        public Builder minDate(String v) { this.minDate = v; return this; }
        public Builder maxDate(String v) { this.maxDate = v; return this; }
        public Builder topExpenses(List<TopExpense> v) { this.topExpenses = v; return this; }
        public Builder perMonthTxCount(Map<String, Integer> v) { this.perMonthTxCount = v; return this; }
        public Builder perMonthTopCategory(Map<String, String> v) { this.perMonthTopCategory = v; return this; }
        public Builder perYearTxCount(Map<String, Integer> v) { this.perYearTxCount = v; return this; }
        public Builder perYearTopCategory(Map<String, String> v) { this.perYearTopCategory = v; return this; }

        public DashboardResponse build() {
            return new DashboardResponse(monthlyTotals, weeklyTotals, annualTotals, categoryTotals,
                    timeline, geoData, geoByCountry, topShops, topItems, discoveryCards, categories,
                    totalExpenses, minDate, maxDate, topExpenses, perMonthTxCount, perMonthTopCategory,
                    perYearTxCount, perYearTopCategory);
        }
    }
}
