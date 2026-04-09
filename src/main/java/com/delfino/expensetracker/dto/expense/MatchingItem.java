package com.delfino.expensetracker.dto.expense;

import java.math.BigDecimal;

public record MatchingItem(
        String itemName,
        BigDecimal unitPrice,
        BigDecimal quantity,
        BigDecimal totalPrice) {}
