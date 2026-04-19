package com.delfino.expensetracker.dto.dashboard;

import java.math.BigDecimal;
import java.util.List;

public record TopItem(String name, BigDecimal count, List<TopItemTransaction> recentTransactions) {

    public record TopItemTransaction(Long expenseId, String urlId, String date,
                                     BigDecimal unitPrice, String currency, String storeName) {}
}
