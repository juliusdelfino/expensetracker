package com.delfino.expensetracker.dto.dashboard;

import java.math.BigDecimal;

public record GeoCountry(
        String country,
        String countryName,
        Double lat,
        Double lng,
        BigDecimal total,
        int count,
        String minDate,
        String maxDate) {

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String country;
        private String countryName;
        private Double lat;
        private Double lng;
        private BigDecimal total = BigDecimal.ZERO;
        private int count = 0;
        private String minDate;
        private String maxDate;

        public Builder country(String v) { this.country = v; return this; }
        public Builder countryName(String v) { this.countryName = v; return this; }
        public Builder lat(Double v) { this.lat = v; return this; }
        public Builder lng(Double v) { this.lng = v; return this; }

        public Builder addAmount(BigDecimal amount) {
            this.total = this.total.add(amount);
            this.count++;
            return this;
        }

        public Builder updateDates(String dateStr) {
            if (dateStr == null) return this;
            if (minDate == null || dateStr.compareTo(minDate) < 0) minDate = dateStr;
            if (maxDate == null || dateStr.compareTo(maxDate) > 0) maxDate = dateStr;
            return this;
        }

        public GeoCountry build() {
            return new GeoCountry(country, countryName, lat, lng, total, count, minDate, maxDate);
        }
    }
}
