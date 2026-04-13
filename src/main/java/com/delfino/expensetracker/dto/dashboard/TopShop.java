package com.delfino.expensetracker.dto.dashboard;

import java.util.List;

public record TopShop(String name, int visits, List<TopShopTransaction> recentTransactions) {

    public record TopShopTransaction(Long expenseId, String urlId, String category, String date,
                                     java.math.BigDecimal amount, String currency) {}
}
