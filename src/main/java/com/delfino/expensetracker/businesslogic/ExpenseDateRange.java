package com.delfino.expensetracker.businesslogic;

import com.delfino.expensetracker.model.Expense;
import org.springframework.util.StringUtils;

import java.time.LocalDate;

/**
 * Pure utility for date-range parsing and filtering of expenses.
 * Contains no Spring beans — safe to use in any context.
 */
public final class ExpenseDateRange {

    private ExpenseDateRange() {}

    /**
     * Parses an ISO-8601 date string ({@code yyyy-MM-dd}) into a {@link LocalDate},
     * or returns {@code null} if the input is blank or absent.
     */
    public static LocalDate parseOrNull(String dateStr) {
        return StringUtils.hasText(dateStr) ? LocalDate.parse(dateStr) : null;
    }

    /**
     * Returns {@code true} if the expense falls within the inclusive date range
     * [{@code start}, {@code end}]. Either bound may be {@code null} (open-ended).
     * Expenses without a transaction date are always considered in-range.
     */
    public static boolean isWithin(Expense expense, LocalDate start, LocalDate end) {
        if (expense.getTransactionDatetime() == null) return true;
        LocalDate date = expense.getTransactionDatetime().toLocalDate();
        if (start != null && date.isBefore(start)) return false;
        return end == null || !date.isAfter(end);
    }
}

