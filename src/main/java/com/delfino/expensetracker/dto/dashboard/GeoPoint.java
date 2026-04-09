package com.delfino.expensetracker.dto.dashboard;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record GeoPoint(
        Double lat,
        Double lng,
        String name,
        BigDecimal amount,
        String currency,
        LocalDateTime date,
        String country,
        String countryName) {}
