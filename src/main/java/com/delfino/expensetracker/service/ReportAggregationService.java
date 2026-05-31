package com.delfino.expensetracker.service;

import com.delfino.expensetracker.dto.report.ReportAggregateSummaryResponse;
import com.delfino.expensetracker.dto.report.ReportChartResponse;
import com.delfino.expensetracker.model.Expense;
import com.delfino.expensetracker.model.Store;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;
import java.util.stream.Collectors;

@Service
public class ReportAggregationService {

    private static final String UNKNOWN_LOCATION = "Unknown location";
    private static final String UNCATEGORIZED = "Uncategorized";

    private final CountryService countryService;

    public ReportAggregationService(CountryService countryService) {
        this.countryService = countryService;
    }

    public ReportAggregateSummaryResponse buildSummary(List<Expense> expenses, Map<Long, Store> storeMap) {
        if (expenses.isEmpty()) {
            return new ReportAggregateSummaryResponse(
                    BigDecimal.ZERO,
                    0,
                    BigDecimal.ZERO,
                    null,
                    null,
                    null,
                    null,
                    0,
                    null,
                    null
            );
        }

        BigDecimal totalAmount = expenses.stream()
                .map(Expense::getBaseAmountOrAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        int expenseCount = expenses.size();
        BigDecimal averageAmount = totalAmount.divide(BigDecimal.valueOf(expenseCount), 2, RoundingMode.HALF_UP);
        BigDecimal minAmount = expenses.stream().map(Expense::getBaseAmountOrAmount).min(BigDecimal::compareTo).orElse(null);
        BigDecimal maxAmount = expenses.stream().map(Expense::getBaseAmountOrAmount).max(BigDecimal::compareTo).orElse(null);

        String topCategory = topLabel(groupTotalsByCategory(expenses));
        String topLocation = topLabel(groupTotalsByLocation(expenses, storeMap));
        int activeDaysCount = new TreeSet<>(expenses.stream()
                .map(Expense::getTransactionDatetime)
                .filter(Objects::nonNull)
                .map(dt -> dt.toLocalDate().toString())
                .toList()).size();

        LocalDate startDate = expenses.stream()
                .map(Expense::getTransactionDatetime)
                .filter(Objects::nonNull)
                .map(java.time.LocalDateTime::toLocalDate)
                .min(LocalDate::compareTo)
                .orElse(null);
        LocalDate endDate = expenses.stream()
                .map(Expense::getTransactionDatetime)
                .filter(Objects::nonNull)
                .map(java.time.LocalDateTime::toLocalDate)
                .max(LocalDate::compareTo)
                .orElse(null);

        return new ReportAggregateSummaryResponse(
                totalAmount,
                expenseCount,
                averageAmount,
                minAmount,
                maxAmount,
                topCategory,
                topLocation,
                activeDaysCount,
                startDate != null ? startDate.toString() : null,
                endDate != null ? endDate.toString() : null
        );
    }

    public List<ReportChartResponse> buildCharts(List<Expense> expenses,
                                                 Map<Long, Store> storeMap,
                                                 JsonNode chartDefinitions) {
        if (chartDefinitions == null || !chartDefinitions.isArray()) {
            return List.of();
        }

        List<ReportChartResponse> charts = new ArrayList<>();
        for (JsonNode definition : chartDefinitions) {
            String id = definition.path("id").asText("chart");
            String title = definition.path("title").asText(id);
            String type = definition.path("type").asText("BAR");
            String groupBy = definition.path("groupBy").asText("CATEGORY");
            String metric = definition.path("metric").asText("TOTAL_AMOUNT");
            int limit = definition.has("limit") ? definition.path("limit").asInt(10) : 10;
            String sort = definition.path("sort").asText("DESC");

            Map<String, BigDecimal> series = buildSeries(expenses, storeMap, groupBy, metric, limit, sort);
            charts.add(new ReportChartResponse(
                    id,
                    title,
                    type,
                    new ArrayList<>(series.keySet()),
                    new ArrayList<>(series.values())
            ));
        }
        return charts;
    }

    public String resolveLocationLabel(Expense expense, Map<Long, Store> storeMap) {
        if (expense.getStoreId() == null) {
            return UNKNOWN_LOCATION;
        }
        Store store = storeMap.get(expense.getStoreId());
        return resolveLocationLabel(store);
    }

    public String resolveLocationLabel(Store store) {
        if (store == null) {
            return UNKNOWN_LOCATION;
        }
        if (store.getCity() != null && !store.getCity().isBlank() && store.getCountry() != null && !store.getCountry().isBlank()) {
            return store.getCity() + ", " + countryService.getName(store.getCountry());
        }
        if (store.getCountry() != null && !store.getCountry().isBlank()) {
            return countryService.getName(store.getCountry());
        }
        if (store.getName() != null && !store.getName().isBlank()) {
            return store.getName();
        }
        return UNKNOWN_LOCATION;
    }

    private Map<String, BigDecimal> buildSeries(List<Expense> expenses,
                                                Map<Long, Store> storeMap,
                                                String groupBy,
                                                String metric,
                                                int limit,
                                                String sort) {
        Map<String, List<Expense>> grouped = switch (groupBy.toUpperCase(Locale.ROOT)) {
            case "DAY" -> expenses.stream()
                    .filter(e -> e.getTransactionDatetime() != null)
                    .collect(Collectors.groupingBy(e -> e.getTransactionDatetime().toLocalDate().toString(), LinkedHashMap::new, Collectors.toList()));
            case "LOCATION" -> expenses.stream()
                    .collect(Collectors.groupingBy(e -> resolveLocationLabel(e, storeMap), LinkedHashMap::new, Collectors.toList()));
            case "CATEGORY" -> expenses.stream()
                    .collect(Collectors.groupingBy(e -> e.getCategory() != null ? e.getCategory() : UNCATEGORIZED, LinkedHashMap::new, Collectors.toList()));
            default -> expenses.stream()
                    .collect(Collectors.groupingBy(e -> e.getCategory() != null ? e.getCategory() : UNCATEGORIZED, LinkedHashMap::new, Collectors.toList()));
        };

        Map<String, BigDecimal> series = new LinkedHashMap<>();
        grouped.forEach((label, groupedExpenses) -> series.put(label, computeMetric(groupedExpenses, metric)));

        if ("DAY".equalsIgnoreCase(groupBy)) {
            return series.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .collect(Collectors.toMap(
                            Map.Entry::getKey,
                            Map.Entry::getValue,
                            (a, b) -> a,
                            LinkedHashMap::new
                    ));
        }

        Comparator<Map.Entry<String, BigDecimal>> comparator = Map.Entry.comparingByValue();
        if (!"ASC".equalsIgnoreCase(sort)) {
            comparator = comparator.reversed();
        }

        return series.entrySet().stream()
                .sorted(comparator.thenComparing(Map.Entry::getKey))
                .limit(limit > 0 ? limit : series.size())
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (a, b) -> a,
                        LinkedHashMap::new
                ));
    }

    private BigDecimal computeMetric(Collection<Expense> expenses, String metric) {
        return switch (metric.toUpperCase(Locale.ROOT)) {
            case "EXPENSE_COUNT" -> BigDecimal.valueOf(expenses.size());
            case "AVERAGE_AMOUNT" -> {
                if (expenses.isEmpty()) {
                    yield BigDecimal.ZERO;
                }
                BigDecimal total = expenses.stream().map(Expense::getBaseAmountOrAmount)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
                yield total.divide(BigDecimal.valueOf(expenses.size()), 2, RoundingMode.HALF_UP);
            }
            default -> expenses.stream().map(Expense::getBaseAmountOrAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
        };
    }

    private Map<String, BigDecimal> groupTotalsByCategory(List<Expense> expenses) {
        return expenses.stream().collect(Collectors.groupingBy(
                e -> e.getCategory() != null ? e.getCategory() : UNCATEGORIZED,
                LinkedHashMap::new,
                Collectors.mapping(Expense::getBaseAmountOrAmount,
                        Collectors.reducing(BigDecimal.ZERO, BigDecimal::add))
        ));
    }

    private Map<String, BigDecimal> groupTotalsByLocation(List<Expense> expenses, Map<Long, Store> storeMap) {
        return expenses.stream().collect(Collectors.groupingBy(
                e -> resolveLocationLabel(e, storeMap),
                LinkedHashMap::new,
                Collectors.mapping(Expense::getBaseAmountOrAmount,
                        Collectors.reducing(BigDecimal.ZERO, BigDecimal::add))
        ));
    }

    private String topLabel(Map<String, BigDecimal> totals) {
        return totals.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(null);
    }
}


