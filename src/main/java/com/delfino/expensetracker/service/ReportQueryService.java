package com.delfino.expensetracker.service;

import com.delfino.expensetracker.dto.report.ReportAggregateSummaryResponse;
import com.delfino.expensetracker.dto.report.ReportChartResponse;
import com.delfino.expensetracker.dto.report.ReportExpenseResponse;
import com.delfino.expensetracker.dto.report.ReportResponse;
import com.delfino.expensetracker.dto.report.ReportSummaryResponse;
import com.delfino.expensetracker.model.Expense;
import com.delfino.expensetracker.model.Report;
import com.delfino.expensetracker.model.Store;
import com.delfino.expensetracker.repository.ExpenseRepository;
import com.delfino.expensetracker.repository.ReportRepository;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@Service
public class ReportQueryService {

    private final ReportRepository reportRepository;
    private final ExpenseRepository expenseRepository;
    private final ExpenseService expenseService;
    private final ReportAggregationService reportAggregationService;
    private final ReportInsightService reportInsightService;

    public ReportQueryService(ReportRepository reportRepository,
                              ExpenseRepository expenseRepository,
                              ExpenseService expenseService,
                              ReportAggregationService reportAggregationService,
                              ReportInsightService reportInsightService) {
        this.reportRepository = reportRepository;
        this.expenseRepository = expenseRepository;
        this.expenseService = expenseService;
        this.reportAggregationService = reportAggregationService;
        this.reportInsightService = reportInsightService;
    }

    @Transactional(readOnly = true)
    public List<ReportSummaryResponse> listReports(Long userId) {
        return reportRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(this::toSummaryResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public Optional<ReportResponse> getReport(Long userId, Long reportId) {
        return reportRepository.findByIdAndUserId(reportId, userId)
                .map(this::toResponse);
    }

    public ReportResponse toResponse(Report report) {
        List<Expense> expenses = loadReportExpenses(report);
        Map<Long, Store> storeMap = expenseService.getStoreMapForUser(report.getUserId());
        ReportAggregateSummaryResponse summary = reportAggregationService.buildSummary(expenses, storeMap);
        List<ReportChartResponse> charts = reportAggregationService.buildCharts(expenses, storeMap, report.getChartDefinitions());
        List<String> insights = extractInsights(report.getInsights());
        if (insights.isEmpty()) {
            insights = reportInsightService.buildInsights(expenses, storeMap, summary);
        }

        return new ReportResponse(
                report.getId(),
                report.getTitle(),
                report.getDescription(),
                report.getExpenseIds(),
                report.getChartDefinitions(),
                report.getGroupBy(),
                report.getFilterSnapshot(),
                summary,
                charts,
                insights,
                mapExpenses(expenses, storeMap),
                report.getCreatedAt(),
                report.getUpdatedAt()
        );
    }

    private List<Expense> loadReportExpenses(Report report) {
        if (report.getExpenseIds() == null || report.getExpenseIds().isEmpty()) {
            return List.of();
        }
        Map<Long, Expense> expensesById = new LinkedHashMap<>();
        expenseRepository.findAllById(report.getExpenseIds()).forEach(expense -> expensesById.put(expense.getId(), expense));
        return report.getExpenseIds().stream()
                .map(expensesById::get)
                .filter(Objects::nonNull)
                .toList();
    }

    private List<ReportExpenseResponse> mapExpenses(List<Expense> expenses, Map<Long, Store> storeMap) {
        return expenses.stream().map(expense -> {
            Store store = expense.getStoreId() != null ? storeMap.get(expense.getStoreId()) : null;
            return new ReportExpenseResponse(
                    expense.getId(),
                    expense.getUrlId(),
                    expense.getTransactionDatetime(),
                    expense.getAmount(),
                    expense.getCurrency(),
                    expense.getAmountInBase(),
                    expense.getCategory(),
                    expense.getNotes(),
                    expense.getTags(),
                    store != null ? store.getName() : null,
                    reportAggregationService.resolveLocationLabel(store),
                    expense.isDeleted()
            );
        }).toList();
    }

    private List<String> extractInsights(JsonNode insightsNode) {
        if (insightsNode == null || !insightsNode.isArray()) {
            return List.of();
        }
        return java.util.stream.StreamSupport.stream(insightsNode.spliterator(), false)
                .map(node -> {
                    if (node.isTextual()) {
                        return node.asText();
                    }
                    if (node.hasNonNull("text")) {
                        return node.get("text").asText();
                    }
                    return node.toString();
                })
                .toList();
    }

    private ReportSummaryResponse toSummaryResponse(Report report) {
        return new ReportSummaryResponse(
                report.getId(),
                report.getTitle(),
                report.getDescription(),
                report.getGroupBy(),
                report.getExpenseIds() != null ? report.getExpenseIds().size() : 0,
                report.getCreatedAt(),
                report.getUpdatedAt()
        );
    }
}


