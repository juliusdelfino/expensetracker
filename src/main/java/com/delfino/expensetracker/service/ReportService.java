package com.delfino.expensetracker.service;

import com.delfino.expensetracker.dto.report.CreateReportRequest;
import com.delfino.expensetracker.dto.report.ReportFilterRequest;
import com.delfino.expensetracker.dto.report.UpdateReportRequest;
import com.delfino.expensetracker.model.Expense;
import com.delfino.expensetracker.model.Report;
import com.delfino.expensetracker.model.ReportGroupBy;
import com.delfino.expensetracker.model.Store;
import com.delfino.expensetracker.repository.ExpenseRepository;
import com.delfino.expensetracker.repository.ReportRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class ReportService {

    private final ReportRepository reportRepository;
    private final ExpenseRepository expenseRepository;
    private final ReportExpenseFilterService reportExpenseFilterService;
    private final ReportAggregationService reportAggregationService;
    private final ReportInsightService reportInsightService;
    private final ExpenseService expenseService;
    private final ObjectMapper objectMapper;
    private final int maxExpensesPerReport;

    public ReportService(ReportRepository reportRepository,
                         ExpenseRepository expenseRepository,
                         ReportExpenseFilterService reportExpenseFilterService,
                         ReportAggregationService reportAggregationService,
                         ReportInsightService reportInsightService,
                         ExpenseService expenseService,
                          ObjectMapper objectMapper,
                          @Value("${reports.max-expenses:500}") int maxExpensesPerReport) {
        this.reportRepository = reportRepository;
        this.expenseRepository = expenseRepository;
        this.reportExpenseFilterService = reportExpenseFilterService;
        this.reportAggregationService = reportAggregationService;
        this.reportInsightService = reportInsightService;
        this.expenseService = expenseService;
        this.objectMapper = objectMapper;
        this.maxExpensesPerReport = maxExpensesPerReport;
    }

    @Transactional
    public Report createReport(Long userId, CreateReportRequest request) {
        List<Expense> expenses = resolveExpenses(userId, request);
        validateExpenseCount(expenses.size());
        List<Long> expenseIds = expenses.stream().map(Expense::getId).toList();
        Map<Long, Store> storeMap = expenseService.getStoreMapForUser(userId);
        JsonNode chartDefinitions = resolveChartDefinitions(request.chartDefinitions(), request.groupBy());
        JsonNode filterSnapshot = request.filterSnapshot() != null
                ? request.filterSnapshot()
                : buildFilterSnapshot(request, expenseIds);
        JsonNode insights = request.insights() != null
                ? request.insights()
                : reportInsightService.buildInsightNode(
                        expenses,
                        storeMap,
                        reportAggregationService.buildSummary(expenses, storeMap)
                );

        LocalDateTime now = LocalDateTime.now();
        Report report = new Report();
        report.setUserId(userId);
        report.setTitle(resolveTitle(request.title(), request.groupBy()));
        report.setDescription(request.description());
        report.setExpenseIds(expenseIds);
        report.setChartDefinitions(chartDefinitions);
        report.setGroupBy(request.groupBy());
        report.setFilterSnapshot(filterSnapshot);
        report.setInsights(insights);
        report.setCreatedAt(now);
        report.setUpdatedAt(now);
        return reportRepository.save(report);
    }

    @Transactional
    public boolean deleteReport(Long userId, Long reportId) {
        return reportRepository.findByIdAndUserId(reportId, userId)
                .map(report -> {
                    reportRepository.delete(report);
                    return true;
                })
                .orElse(false);
    }

    @Transactional
    public Report updateReport(Long userId, Long reportId, UpdateReportRequest request) {
        Report report = reportRepository.findByIdAndUserId(reportId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Report not found"));

        ReportGroupBy groupBy = request.groupBy() != null ? request.groupBy() : report.getGroupBy();
        ReportFilterRequest filterRequest = mergeWithExistingFilter(report, request);
        rejectOversizedUnboundedFilter(userId, filterRequest);
        List<Expense> expenses = reportExpenseFilterService.filterExpenses(userId, filterRequest);
        validateExpenseCount(expenses.size());
        List<Long> expenseIds = expenses.stream().map(Expense::getId).toList();

        report.setTitle(resolveUpdatedTitle(report.getTitle(), request.title(), groupBy));
        report.setDescription(request.description() != null ? request.description().trim() : null);
        report.setGroupBy(groupBy);
        report.setExpenseIds(expenseIds);
        report.setChartDefinitions(resolveUpdatedChartDefinitions(report, request, groupBy));
        report.setFilterSnapshot(buildFilterSnapshot(filterRequest, groupBy));
        report.setUpdatedAt(LocalDateTime.now());
        return reportRepository.save(report);
    }

    private List<Expense> resolveExpenses(Long userId, CreateReportRequest request) {
        if (request.expenseIds() != null) {
            List<Long> expenseIds = normalizeExpenseIds(request.expenseIds());
            validateExpenseCount(expenseIds.size());
            return validateExpenseOwnership(userId, expenseIds);
        }
        ReportFilterRequest filterRequest = request.toFilterRequest();
        rejectOversizedUnboundedFilter(userId, filterRequest);
        return reportExpenseFilterService.filterExpenses(userId, filterRequest);
    }

    private List<Long> normalizeExpenseIds(List<Long> expenseIds) {
        if (expenseIds == null) {
            throw new IllegalArgumentException("expenseIds is required");
        }

        if (expenseIds.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("expenseIds must not contain null values");
        }

        if (expenseIds.stream().anyMatch(id -> id <= 0)) {
            throw new IllegalArgumentException("expenseIds must contain only positive values");
        }

        return List.copyOf(new LinkedHashSet<>(expenseIds));
    }

    private List<Expense> validateExpenseOwnership(Long userId, List<Long> expenseIds) {
        if (expenseIds.isEmpty()) {
            return List.of();
        }

        List<Expense> expenses = expenseRepository.findAllById(expenseIds);
        if (expenses.size() != expenseIds.size()) {
            throw new IllegalArgumentException("One or more expenses were not found");
        }

        boolean hasForeignOrDeletedExpense = expenses.stream()
                .anyMatch(expense -> !userId.equals(expense.getUserId()) || expense.isDeleted());
        if (hasForeignOrDeletedExpense) {
            throw new IllegalArgumentException("One or more expenses are invalid for this user");
        }

        Map<Long, Expense> byId = new LinkedHashMap<>();
        expenses.forEach(expense -> byId.put(expense.getId(), expense));
        return expenseIds.stream().map(byId::get).filter(Objects::nonNull).toList();
    }

    private void validateExpenseCount(int expenseCount) {
        if (expenseCount > maxExpensesPerReport) {
            throw new IllegalArgumentException("Report cannot include more than " + maxExpensesPerReport + " expenses");
        }
    }

    private void rejectOversizedUnboundedFilter(Long userId, ReportFilterRequest filterRequest) {
        if (!isUnboundedFilter(filterRequest)) {
            return;
        }
        long activeExpenseCount = expenseService.countActiveExpenses(userId);
        if (activeExpenseCount > maxExpensesPerReport) {
            throw new IllegalArgumentException(
                    "Refine filters before creating this report. Unfiltered reports are limited to "
                            + maxExpensesPerReport + " expenses");
        }
    }

    private boolean isUnboundedFilter(ReportFilterRequest filterRequest) {
        return isBlank(filterRequest.startDate())
                && isBlank(filterRequest.endDate())
                && isBlank(filterRequest.category())
                && isBlank(filterRequest.country())
                && isBlank(filterRequest.city())
                && isBlank(filterRequest.storeName())
                && isBlank(filterRequest.search())
                && (filterRequest.searchKeywords() == null
                || filterRequest.searchKeywords().stream().map(ReportFilterSupport::trimToNull).allMatch(Objects::isNull));
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String resolveTitle(String title, ReportGroupBy groupBy) {
        if (title != null && !title.isBlank()) {
            return title.trim();
        }
        String prefix = switch (groupBy) {
            case CATEGORY -> "Category Report";
            case STORE_LOCATION -> "Location Report";
            case KEYWORD -> "Keyword Report";
        };
        return prefix + " " + LocalDate.now();
    }

    private JsonNode resolveChartDefinitions(JsonNode chartDefinitions, ReportGroupBy groupBy) {
        return chartDefinitions != null ? chartDefinitions : buildDefaultChartDefinitions(groupBy);
    }

    private JsonNode buildFilterSnapshot(CreateReportRequest request, List<Long> expenseIds) {
        return ReportFilterSupport.buildFilterSnapshot(objectMapper, request, expenseIds);
    }

    private JsonNode buildFilterSnapshot(ReportFilterRequest filterRequest, ReportGroupBy groupBy) {
        return ReportFilterSupport.buildFilterSnapshot(objectMapper, groupBy, filterRequest);
    }

    private ReportFilterRequest mergeWithExistingFilter(Report report, UpdateReportRequest request) {
        JsonNode snapshot = report.getFilterSnapshot();
        return new ReportFilterRequest(
                coalesce(request.startDate(), snapshotText(snapshot, "startDate")),
                coalesce(request.endDate(), snapshotText(snapshot, "endDate")),
                coalesce(request.category(), snapshotText(snapshot, "category")),
                coalesce(request.country(), snapshotText(snapshot, "country")),
                coalesce(request.city(), snapshotText(snapshot, "city")),
                coalesce(request.storeName(), snapshotText(snapshot, "storeName")),
                coalesce(request.search(), snapshotText(snapshot, "search")),
                request.searchKeywords() != null ? request.searchKeywords() : snapshotTextArray(snapshot, "searchKeywords")
        );
    }

    private JsonNode resolveUpdatedChartDefinitions(Report report, UpdateReportRequest request, ReportGroupBy groupBy) {
        if (request.chartDefinitions() != null) {
            return request.chartDefinitions();
        }
        if (report.getChartDefinitions() != null) {
            return report.getChartDefinitions();
        }
        return buildDefaultChartDefinitions(groupBy);
    }

    private String resolveUpdatedTitle(String currentTitle, String requestedTitle, ReportGroupBy groupBy) {
        if (requestedTitle != null && !requestedTitle.isBlank()) {
            return requestedTitle.trim();
        }
        if (currentTitle != null && !currentTitle.isBlank()) {
            return currentTitle;
        }
        return resolveTitle(null, groupBy);
    }

    private String snapshotText(JsonNode snapshot, String field) {
        if (snapshot == null || !snapshot.hasNonNull(field)) return null;
        String value = snapshot.get(field).asText(null);
        return value != null && !value.isBlank() ? value.trim() : null;
    }

    private List<String> snapshotTextArray(JsonNode snapshot, String field) {
        if (snapshot == null || !snapshot.has(field) || !snapshot.get(field).isArray()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        snapshot.get(field).forEach(node -> {
            String value = node.asText(null);
            if (value != null && !value.isBlank()) {
                values.add(value.trim());
            }
        });
        return values;
    }

    private String coalesce(String primary, String fallback) {
        return primary != null ? primary : fallback;
    }

    private JsonNode buildDefaultChartDefinitions(ReportGroupBy groupBy) {
        ArrayNode definitions = objectMapper.createArrayNode();
        switch (groupBy) {
            case CATEGORY -> {
                definitions.add(chartDefinition("spend-by-category", "DOUGHNUT", "Spend by Category", "TOTAL_AMOUNT", "CATEGORY", 10, "DESC"));
                definitions.add(chartDefinition("daily-spending", "LINE", "Daily Spending", "TOTAL_AMOUNT", "DAY", null, "ASC"));
                definitions.add(chartDefinition("top-locations", "BAR", "Top Locations", "TOTAL_AMOUNT", "LOCATION", 10, "DESC"));
            }
            case STORE_LOCATION -> {
                definitions.add(chartDefinition("spend-by-location", "BAR", "Spend by Location", "TOTAL_AMOUNT", "LOCATION", 10, "DESC"));
                definitions.add(chartDefinition("daily-spending", "LINE", "Daily Spending", "TOTAL_AMOUNT", "DAY", null, "ASC"));
                definitions.add(chartDefinition("category-mix", "DOUGHNUT", "Category Mix", "TOTAL_AMOUNT", "CATEGORY", 10, "DESC"));
            }
            case KEYWORD -> {
                definitions.add(chartDefinition("daily-spending", "LINE", "Daily Spending", "TOTAL_AMOUNT", "DAY", null, "ASC"));
                definitions.add(chartDefinition("spend-by-category", "DOUGHNUT", "Spend by Category", "TOTAL_AMOUNT", "CATEGORY", 10, "DESC"));
                definitions.add(chartDefinition("matching-locations", "BAR", "Matching Locations", "TOTAL_AMOUNT", "LOCATION", 10, "DESC"));
            }
        }
        return definitions;
    }

    private ObjectNode chartDefinition(String id,
                                       String type,
                                       String title,
                                       String metric,
                                       String groupBy,
                                       Integer limit,
                                       String sort) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("id", id);
        node.put("type", type);
        node.put("title", title);
        node.put("metric", metric);
        node.put("groupBy", groupBy);
        if (limit != null) {
            node.put("limit", limit);
        }
        if (sort != null) {
            node.put("sort", sort);
        }
        return node;
    }

}


