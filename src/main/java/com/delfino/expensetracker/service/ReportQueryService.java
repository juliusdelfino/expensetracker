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
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class ReportQueryService {

    private static final int DEFAULT_LIST_LIMIT = 50;
    private static final int MAX_LIST_LIMIT = 200;

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
    public List<ReportSummaryResponse> listReports(Long userId, Integer requestedLimit) {
        int limit = sanitizeListLimit(requestedLimit);
        List<Report> reports = reportRepository.findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(0, limit));
        Map<Long, Expense> expensesById = loadExpensesByIds(reports.stream()
                .flatMap(report -> report.getExpenseIds() != null ? report.getExpenseIds().stream() : Stream.empty())
                .collect(Collectors.toCollection(LinkedHashSet::new)));

        return reports.stream()
                .map(report -> toSummaryResponse(report, expensesById))
                .toList();
    }

    private int sanitizeListLimit(Integer requestedLimit) {
        if (requestedLimit == null || requestedLimit <= 0) {
            return DEFAULT_LIST_LIMIT;
        }
        return Math.min(requestedLimit, MAX_LIST_LIMIT);
    }

    @Transactional(readOnly = true)
    public Optional<ReportResponse> getReport(Long userId, Long reportId) {
        return reportRepository.findByIdAndUserId(reportId, userId)
                .map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public long countReportsCreatedSince(Long userId, LocalDateTime createdAfter) {
        return reportRepository.countByUserIdAndCreatedAtAfter(userId, createdAfter);
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
        Map<Long, Expense> expensesById = loadExpensesByIds(report.getExpenseIds());
        return report.getExpenseIds().stream()
                .map(expensesById::get)
                .filter(Objects::nonNull)
                .toList();
    }

    private Map<Long, Expense> loadExpensesByIds(Collection<Long> expenseIds) {
        if (expenseIds == null || expenseIds.isEmpty()) {
            return Map.of();
        }

        Map<Long, Expense> expensesById = new LinkedHashMap<>();
        expenseRepository.findAllById(expenseIds).forEach(expense -> expensesById.put(expense.getId(), expense));
        return expensesById;
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

    private ReportSummaryResponse toSummaryResponse(Report report, Map<Long, Expense> expensesById) {
        List<Expense> expenses = loadReportExpenses(report, expensesById);
        return new ReportSummaryResponse(
                report.getId(),
                report.getTitle(),
                report.getDescription(),
                report.getGroupBy(),
                report.getExpenseIds() != null ? report.getExpenseIds().size() : 0,
                expenses.stream()
                        .map(Expense::getBaseAmountOrAmount)
                        .reduce(BigDecimal.ZERO, BigDecimal::add),
                report.getCreatedAt(),
                report.getUpdatedAt()
        );
    }

    private List<Expense> loadReportExpenses(Report report, Map<Long, Expense> expensesById) {
        if (report.getExpenseIds() == null || report.getExpenseIds().isEmpty()) {
            return List.of();
        }
        return report.getExpenseIds().stream()
                .map(expensesById::get)
                .filter(Objects::nonNull)
                .toList();
    }
}


