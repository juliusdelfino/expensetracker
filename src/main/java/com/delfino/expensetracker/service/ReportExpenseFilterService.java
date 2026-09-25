package com.delfino.expensetracker.service;

import com.delfino.expensetracker.dto.report.ReportFilterRequest;
import com.delfino.expensetracker.model.Expense;
import com.delfino.expensetracker.model.Store;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ReportExpenseFilterService {

    private final ExpenseService expenseService;
    private final CountryService countryService;

    public ReportExpenseFilterService(ExpenseService expenseService, CountryService countryService) {
        this.expenseService = expenseService;
        this.countryService = countryService;
    }

    public List<Expense> filterExpenses(Long userId, ReportFilterRequest filter) {
        List<String> keywords = ReportFilterSupport.normalizeKeywords(filter.searchKeywords(), filter.search());
        List<Expense> searchMatches = searchByKeywords(userId, keywords);
        List<Expense> expenses = filterByDateRange(
                new ArrayList<>(searchMatches),
                filter.startDate(),
                filter.endDate()
        );

        String category = ReportFilterSupport.trimToNull(filter.category());
        if (category != null) {
            expenses = expenses.stream()
                    .filter(e -> e.getCategory() != null && e.getCategory().equalsIgnoreCase(category))
                    .toList();
        }

        Map<Long, Store> storeMap = expenseService.getStoreMapForUser(userId);

        String country = ReportFilterSupport.trimToNull(filter.country());
        if (country != null) {
            String countryLower = country.toLowerCase();
            String resolvedCode = countryService.findCodeByName(country);
            expenses = expenses.stream()
                    .filter(e -> {
                        if (e.getStoreId() == null) return false;
                        Store store = storeMap.get(e.getStoreId());
                        return store != null && matchesCountry(store, countryLower, resolvedCode);
                    })
                    .toList();
        }

        String city = ReportFilterSupport.trimToNull(filter.city());
        if (city != null) {
            String cityLower = city.toLowerCase();
            expenses = expenses.stream()
                    .filter(e -> {
                        if (e.getStoreId() == null) return false;
                        Store store = storeMap.get(e.getStoreId());
                        return store != null && store.getCity() != null
                                && store.getCity().toLowerCase().contains(cityLower);
                    })
                    .toList();
        }

        String storeName = ReportFilterSupport.trimToNull(filter.storeName());
        if (storeName != null) {
            String storeLower = storeName.toLowerCase();
            expenses = expenses.stream()
                    .filter(e -> {
                        if (e.getStoreId() == null) return false;
                        Store store = storeMap.get(e.getStoreId());
                        return store != null && store.getName() != null
                                && store.getName().toLowerCase().contains(storeLower);
                    })
                    .toList();
        }

        List<Expense> sortedExpenses = new ArrayList<>(expenses);
        sortedExpenses.sort(Comparator.comparing(Expense::getTransactionDatetime,
                Comparator.nullsLast(Comparator.reverseOrder())));
        return sortedExpenses;
    }

    private List<Expense> searchByKeywords(Long userId, List<String> keywords) {
        if (keywords.isEmpty()) {
            return expenseService.search(userId, null, false);
        }
        Map<Long, Expense> merged = new LinkedHashMap<>();
        for (String keyword : keywords) {
            for (Expense expense : expenseService.search(userId, keyword, false)) {
                merged.put(expense.getId(), expense);
            }
        }
        return new ArrayList<>(merged.values());
    }

    private List<Expense> filterByDateRange(List<Expense> expenses, String startDate, String endDate) {
        try {
            LocalDate start = startDate != null && !startDate.isBlank() ? LocalDate.parse(startDate) : null;
            LocalDate end = endDate != null && !endDate.isBlank() ? LocalDate.parse(endDate) : null;

            if (start != null && end != null && start.isAfter(end)) {
                throw new IllegalArgumentException("startDate must be on or before endDate");
            }

            if (start != null) {
                expenses = expenses.stream()
                        .filter(e -> e.getTransactionDatetime() != null
                                && !e.getTransactionDatetime().toLocalDate().isBefore(start))
                        .toList();
            }
            if (end != null) {
                expenses = expenses.stream()
                        .filter(e -> e.getTransactionDatetime() != null
                                && !e.getTransactionDatetime().toLocalDate().isAfter(end))
                        .toList();
            }
            return expenses;
        } catch (DateTimeParseException ex) {
            throw new IllegalArgumentException("Invalid date format. Expected yyyy-MM-dd");
        }
    }

    private boolean matchesCountry(Store store, String countryFilterLower, String resolvedCode) {
        if (store.getCountry() == null) return false;
        String storeCountry = store.getCountry().toLowerCase();
        if (storeCountry.contains(countryFilterLower)) return true;
        if (storeCountry.equalsIgnoreCase(resolvedCode)) return true;
        String countryName = countryService.getName(store.getCountry());
        return countryName != null && countryName.toLowerCase().contains(countryFilterLower);
    }
}


