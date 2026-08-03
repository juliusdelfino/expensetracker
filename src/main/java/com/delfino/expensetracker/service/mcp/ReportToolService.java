package com.delfino.expensetracker.service.mcp;

import com.delfino.expensetracker.dto.auth.UserContext;
import com.delfino.expensetracker.dto.report.CreateReportRequest;
import com.delfino.expensetracker.dto.report.ReportResponse;
import com.delfino.expensetracker.dto.report.ReportSummaryResponse;
import com.delfino.expensetracker.model.Report;
import com.delfino.expensetracker.model.ReportGroupBy;
import com.delfino.expensetracker.service.ReportQueryService;
import com.delfino.expensetracker.service.ReportService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class ReportToolService {

    private static final Logger log = LoggerFactory.getLogger(ReportToolService.class);

    private final ReportService reportService;
    private final ReportQueryService reportQueryService;
    private final UserContext userContext;
    private final ChatReportContext chatReportContext;

    public ReportToolService(ReportService reportService,
                             ReportQueryService reportQueryService,
                             UserContext userContext,
                             ChatReportContext chatReportContext) {
        this.reportService = reportService;
        this.reportQueryService = reportQueryService;
        this.userContext = userContext;
        this.chatReportContext = chatReportContext;
    }

    @Tool(description = "Generate and save a new expense report for the current user. " +
            "Use this when the user asks to create, generate, or save a report for a date range, trip, category, keyword, store, city, or country.")
    public String generateExpenseReport(
            @ToolParam(description = "How to group the report. Use CATEGORY, STORE_LOCATION, or KEYWORD. Pass empty string if unclear.") String groupBy,
            @ToolParam(description = "Start date in ISO format yyyy-MM-dd. Pass empty string if not specified.") String startDate,
            @ToolParam(description = "End date in ISO format yyyy-MM-dd. Pass empty string if not specified.") String endDate,
            @ToolParam(description = "Keyword or search phrase to filter by. Pass empty string if not specified.") String keyword,
            @ToolParam(description = "Expense category to filter by. Pass empty string if not specified.") String category,
            @ToolParam(description = "Country name or code to filter by. Pass empty string if not specified.") String country,
            @ToolParam(description = "Store name to filter by. Pass empty string if not specified.") String storeName) {

        Long userId = userContext.getUserId();
        log.info("Tool call: generateExpenseReport(groupBy={}, startDate={}, endDate={}, keyword={}, category={}, country={}, storeName={}, userId={})",
                groupBy, startDate, endDate, keyword, category, country, storeName, userId);

        try {
            ReportGroupBy resolvedGroupBy = resolveGroupBy(groupBy, category, country, storeName, keyword);
            Report report = reportService.createReport(userId, new CreateReportRequest(
                    null,
                    null,
                    null,
                    null,
                    resolvedGroupBy,
                    trimToNull(startDate),
                    trimToNull(endDate),
                    trimToNull(category),
                    trimToNull(country),
                    null,
                    trimToNull(storeName),
                    trimToNull(keyword),
                    null,
                    null
            ));
            chatReportContext.trackReport(report.getId());
            return "Report created successfully. "
                    + "Report ID: " + report.getId()
                    + ", title: " + report.getTitle()
                    + ", group: " + report.getGroupBy()
                    + ", expenses included: " + (report.getExpenseIds() != null ? report.getExpenseIds().size() : 0) + ".";
        } catch (IllegalArgumentException ex) {
            return "Unable to generate the report: " + ex.getMessage();
        }
    }

    @Tool(description = "List saved reports for the current user. Use this when the user asks to see their reports or recent reports.")
    public String listReports(
            @ToolParam(description = "Maximum number of reports to return. Use 5 or 10 as a default.") int limit) {

        Long userId = userContext.getUserId();
        log.info("Tool call: listReports(limit={}, userId={})", limit, userId);

        List<ReportSummaryResponse> reports = reportQueryService.listReports(userId);
        if (reports.isEmpty()) {
            return "No saved reports found.";
        }

        int safeLimit = limit > 0 ? Math.min(limit, 20) : 5;
        List<ReportSummaryResponse> limited = reports.stream().limit(safeLimit).toList();
        limited.forEach(report -> chatReportContext.trackReport(report.id()));

        StringBuilder sb = new StringBuilder("Saved reports:\n");
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
        for (ReportSummaryResponse report : limited) {
            sb.append("- #")
                    .append(report.id())
                    .append(" — ")
                    .append(report.title())
                    .append(" (")
                    .append(report.groupBy())
                    .append(", ")
                    .append(report.expenseCount())
                    .append(" expenses, created ")
                    .append(report.createdAt() != null ? report.createdAt().format(formatter) : "unknown time")
                    .append(")\n");
        }
        return sb.toString().trim();
    }

    @Tool(description = "Get a saved report summary by report ID. Use this when the user asks about a specific saved report.")
    public String getReportSummary(
            @ToolParam(description = "The numeric ID of the saved report.") Long reportId) {

        Long userId = userContext.getUserId();
        log.info("Tool call: getReportSummary(reportId={}, userId={})", reportId, userId);

        if (reportId == null || reportId <= 0) {
            return "A valid report ID is required.";
        }

        return reportQueryService.getReport(userId, reportId)
                .map(report -> buildSummaryResponse(reportId, report))
                .orElse("Report not found.");
    }

    private String buildSummaryResponse(Long reportId, ReportResponse report) {
        chatReportContext.trackReport(reportId);
        return "Report summary for '" + report.title() + "': "
                + "grouped by " + report.groupBy()
                + ", " + report.summary().expenseCount() + " expenses"
                + ", total spend " + report.summary().totalAmount()
                + ", top category " + safeValue(report.summary().topCategory())
                + ", top location " + safeValue(report.summary().topLocation())
                + ".";
    }

    private ReportGroupBy resolveGroupBy(String groupBy,
                                         String category,
                                         String country,
                                         String storeName,
                                         String keyword) {
        if (StringUtils.hasText(groupBy)) {
            try {
                return ReportGroupBy.valueOf(groupBy.trim().toUpperCase());
            } catch (IllegalArgumentException ignored) {
                throw new IllegalArgumentException("groupBy must be CATEGORY, STORE_LOCATION, or KEYWORD");
            }
        }
        if (StringUtils.hasText(category)) {
            return ReportGroupBy.CATEGORY;
        }
        if (StringUtils.hasText(country) || StringUtils.hasText(storeName)) {
            return ReportGroupBy.STORE_LOCATION;
        }
        if (StringUtils.hasText(keyword)) {
            return ReportGroupBy.KEYWORD;
        }
        return ReportGroupBy.KEYWORD;
    }

    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private String safeValue(String value) {
        return StringUtils.hasText(value) ? value : "n/a";
    }
}


