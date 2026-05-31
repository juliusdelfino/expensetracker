package com.delfino.expensetracker.service;

import com.delfino.expensetracker.dto.report.ReportAggregateSummaryResponse;
import com.delfino.expensetracker.model.Expense;
import com.delfino.expensetracker.model.Store;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

@Service
public class ReportInsightService {

    private final ObjectMapper objectMapper;

    public ReportInsightService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public JsonNode buildInsightNode(List<Expense> expenses,
                                     Map<Long, Store> storeMap,
                                     ReportAggregateSummaryResponse summary) {
        ArrayNode arrayNode = objectMapper.createArrayNode();
        buildInsights(expenses, storeMap, summary).forEach(arrayNode::add);
        return arrayNode;
    }

    public List<String> buildInsights(List<Expense> expenses,
                                      Map<Long, Store> storeMap,
                                      ReportAggregateSummaryResponse summary) {
        if (expenses.isEmpty()) {
            return List.of("No expenses matched this report.");
        }

        List<String> insights = new ArrayList<>();
        if (summary.topLocation() != null) {
            insights.add("Most spending happened in " + summary.topLocation() + ".");
        }

        Expense largestExpense = expenses.stream()
                .max(Comparator.comparing(Expense::getBaseAmountOrAmount))
                .orElse(null);
        String dateText = largestExpense.getTransactionDatetime() != null
                ? largestExpense.getTransactionDatetime().toLocalDate().toString()
                : "an unknown date";
        String category = largestExpense.getCategory() != null ? largestExpense.getCategory() : "Uncategorized";
        String location = storeMap.containsKey(largestExpense.getStoreId())
                ? storeMap.get(largestExpense.getStoreId()).getName()
                : null;
        insights.add("Largest expense was " + largestExpense.getBaseAmountOrAmount().setScale(2, RoundingMode.HALF_UP)
                + " in " + category + " on " + dateText
                + (location != null && !location.isBlank() ? " at " + location : "") + ".");

        if (summary.averageAmount() != null) {
            insights.add("Average spend per expense was " + summary.averageAmount().setScale(2, RoundingMode.HALF_UP) + ".");
        }

        if (summary.topCategory() != null && summary.totalAmount() != null && summary.totalAmount().compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal topCategoryTotal = expenses.stream()
                    .filter(e -> summary.topCategory().equals(e.getCategory() != null ? e.getCategory() : "Uncategorized"))
                    .map(Expense::getBaseAmountOrAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal share = topCategoryTotal
                    .multiply(BigDecimal.valueOf(100))
                    .divide(summary.totalAmount(), 1, RoundingMode.HALF_UP);
            insights.add("Top category " + summary.topCategory() + " accounted for " + share + "% of the total.");
        }

        return insights;
    }
}


