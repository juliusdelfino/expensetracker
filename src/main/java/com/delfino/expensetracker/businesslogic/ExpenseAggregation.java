package com.delfino.expensetracker.businesslogic;

import com.delfino.expensetracker.model.Expense;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Pure utility for computing aggregated expense statistics.
 * Contains no Spring beans — safe to use in any context.
 */
public final class ExpenseAggregation {

    private ExpenseAggregation() {}

    /**
     * Returns the effective (display) amount for a single expense:
     * the base-currency amount when available, the original amount next,
     * otherwise {@link BigDecimal#ZERO}.
     */
    public static BigDecimal effectiveAmount(Expense e) {
        if (e.getAmountInBase() != null) return e.getAmountInBase();
        return e.getAmount() != null ? e.getAmount() : BigDecimal.ZERO;
    }

    /**
     * Sums the effective amounts for the given list, rounded to 2 decimal places.
     */
    public static BigDecimal totalBaseAmount(List<Expense> expenses) {
        return expenses.stream()
                .map(ExpenseAggregation::effectiveAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Groups expenses by category (sorted alphabetically), summing the effective
     * amount per group. Expenses with a blank/null category fall under
     * {@code "Uncategorized"}.
     */
    public static Map<String, BigDecimal> byCategory(List<Expense> expenses) {
        Map<String, BigDecimal> result = new TreeMap<>();
        for (Expense e : expenses) {
            String cat = StringUtils.hasText(e.getCategory()) ? e.getCategory() : "Uncategorized";
            result.merge(cat, effectiveAmount(e), BigDecimal::add);
        }
        return result;
    }
}

