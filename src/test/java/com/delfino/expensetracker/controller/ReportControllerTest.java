package com.delfino.expensetracker.controller;

import com.delfino.expensetracker.BaseControllerTest;
import com.delfino.expensetracker.model.Report;
import com.delfino.expensetracker.model.ReportGroupBy;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ReportControllerTest extends BaseControllerTest {

    @Test
    void createReport_shouldPersistOwnedExpenseSnapshot() throws Exception {
        var alice = createTestUser("alice", "pass");
        var expense = createTestExpense(alice.getId(), "Food", BigDecimal.valueOf(12.50), "USD");
        MockHttpSession session = loginAs("alice", "pass");

        mockMvc.perform(post("/api/reports")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "title", "April Food Report",
                                "description", "Monthly food snapshot",
                                "expenseIds", List.of(expense.getId()),
                                "chartDefinitions", List.of(Map.of(
                                        "id", "spend-by-category",
                                        "type", "DOUGHNUT"
                                )),
                                "groupBy", "CATEGORY",
                                "filterSnapshot", Map.of("startDate", "2026-04-01", "endDate", "2026-04-30"),
                                "insights", List.of(Map.of("text", "Top category was Food"))
                        ))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("April Food Report"))
                .andExpect(jsonPath("$.groupBy").value("CATEGORY"))
                .andExpect(jsonPath("$.expenseIds[0]").value(expense.getId()))
                .andExpect(jsonPath("$.chartDefinitions[0].id").value("spend-by-category"));

        List<Report> reports = reportRepository.findByUserIdOrderByCreatedAtDesc(alice.getId());
        assertThat(reports).hasSize(1);
        assertThat(reports.get(0).getExpenseIds()).containsExactly(expense.getId());
    }

    @Test
    void createReport_shouldRejectExpensesOwnedByAnotherUser() throws Exception {
        createTestUser("alice", "pass");
        var bob = createTestUser("bob", "pass");
        var bobExpense = createTestExpense(bob.getId(), "Travel", BigDecimal.valueOf(80), "USD");
        MockHttpSession session = loginAs("alice", "pass");

        mockMvc.perform(post("/api/reports")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "title", "Invalid",
                                "expenseIds", List.of(bobExpense.getId()),
                                "chartDefinitions", List.of(),
                                "groupBy", "KEYWORD"
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("One or more expenses are invalid for this user"));

        assertThat(reportRepository.findByUserIdOrderByCreatedAtDesc(bob.getId())).isEmpty();
    }

    @Test
    void createReport_shouldGenerateFromFiltersWithAggregatesChartsAndInsights() throws Exception {
        var alice = createTestUser("alice", "pass");
        var sgNorth = createTestStore(alice.getId(), "NTUC", "Tampines", "SG");
        var sgCenter = createTestStore(alice.getId(), "Starbucks", "Orchard", "SG");
        var jpStore = createTestStore(alice.getId(), "7-Eleven", "Tokyo", "JP");

        var expense1 = createTestExpense(alice.getId(), "Food", BigDecimal.valueOf(15), "USD");
        setExpenseDateAndStore(expense1, LocalDateTime.of(2026, 4, 10, 12, 0), sgNorth.getId());

        var expense2 = createTestExpense(alice.getId(), "Travel", BigDecimal.valueOf(30), "USD");
        setExpenseDateAndStore(expense2, LocalDateTime.of(2026, 4, 11, 9, 0), sgCenter.getId());

        var expense3 = createTestExpense(alice.getId(), "Food", BigDecimal.valueOf(20), "USD");
        setExpenseDateAndStore(expense3, LocalDateTime.of(2026, 4, 12, 18, 30), sgNorth.getId());

        var expenseOutsideFilter = createTestExpense(alice.getId(), "Shopping", BigDecimal.valueOf(100), "USD");
        setExpenseDateAndStore(expenseOutsideFilter, LocalDateTime.of(2026, 4, 13, 14, 0), jpStore.getId());

        MockHttpSession session = loginAs("alice", "pass");

        mockMvc.perform(post("/api/reports")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "title", "Singapore April Report",
                                "groupBy", "CATEGORY",
                                "startDate", "2026-04-10",
                                "endDate", "2026-04-12",
                                "country", "Singapore"
                        ))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("Singapore April Report"))
                .andExpect(jsonPath("$.expenseIds.length()").value(3))
                .andExpect(jsonPath("$.summary.totalAmount").value(65))
                .andExpect(jsonPath("$.summary.expenseCount").value(3))
                .andExpect(jsonPath("$.summary.averageAmount").value(21.67))
                .andExpect(jsonPath("$.summary.minAmount").value(15))
                .andExpect(jsonPath("$.summary.maxAmount").value(30))
                .andExpect(jsonPath("$.summary.topCategory").value("Food"))
                .andExpect(jsonPath("$.summary.topLocation").value("Tampines, Singapore"))
                .andExpect(jsonPath("$.summary.activeDaysCount").value(3))
                .andExpect(jsonPath("$.chartDefinitions[0].id").value("spend-by-category"))
                .andExpect(jsonPath("$.charts[0].labels[0]").value("Food"))
                .andExpect(jsonPath("$.charts[0].values[0]").value(35))
                .andExpect(jsonPath("$.insights[0]").value("Most spending happened in Tampines, Singapore."))
                .andExpect(jsonPath("$.expenses.length()").value(3))
                .andExpect(jsonPath("$.expenses[0].locationLabel").value("Tampines, Singapore"));
    }

    @Test
    void createReport_shouldReturnEmptyAnalyticsWhenNoExpensesMatchFilters() throws Exception {
        var alice = createTestUser("alice", "pass");
        createTestExpense(alice.getId(), "Food", BigDecimal.valueOf(12.50), "USD");
        MockHttpSession session = loginAs("alice", "pass");

        mockMvc.perform(post("/api/reports")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "groupBy", "KEYWORD",
                                "search", "nonexistent-keyword"
                        ))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.expenseIds.length()").value(0))
                .andExpect(jsonPath("$.summary.totalAmount").value(0))
                .andExpect(jsonPath("$.summary.expenseCount").value(0))
                .andExpect(jsonPath("$.insights[0]").value("No expenses matched this report."))
                .andExpect(jsonPath("$.charts.length()").value(3))
                .andExpect(jsonPath("$.charts[0].labels.length()").value(0))
                .andExpect(jsonPath("$.expenses.length()").value(0));
    }

    @Test
    void createReport_shouldFilterByCityForTripReports() throws Exception {
        var alice = createTestUser("alice", "pass");
        var viennaStore = createTestStore(alice.getId(), "Spar", "Vienna", "AT");
        var grazStore = createTestStore(alice.getId(), "Billa", "Graz", "AT");

        var viennaExpense = createTestExpense(alice.getId(), "Food", BigDecimal.valueOf(18), "USD");
        setExpenseDateAndStore(viennaExpense, LocalDateTime.of(2026, 2, 10, 10, 0), viennaStore.getId());

        var grazExpense = createTestExpense(alice.getId(), "Food", BigDecimal.valueOf(22), "USD");
        setExpenseDateAndStore(grazExpense, LocalDateTime.of(2026, 2, 11, 10, 0), grazStore.getId());

        MockHttpSession session = loginAs("alice", "pass");

        mockMvc.perform(post("/api/reports")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "groupBy", "STORE_LOCATION",
                                "startDate", "2026-02-01",
                                "endDate", "2026-02-28",
                                "country", "Austria",
                                "city", "Vienna"
                        ))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.expenseIds.length()").value(1))
                .andExpect(jsonPath("$.summary.totalAmount").value(18))
                .andExpect(jsonPath("$.summary.topLocation").value("Vienna, Austria"))
                .andExpect(jsonPath("$.expenses.length()").value(1))
                .andExpect(jsonPath("$.expenses[0].locationLabel").value("Vienna, Austria"))
                .andExpect(jsonPath("$.filterSnapshot.city").value("Vienna"));
    }

    @Test
    void listAndGetReport_shouldOnlyExposeCurrentUsersReports() throws Exception {
        var alice = createTestUser("alice", "pass");
        var bob = createTestUser("bob", "pass");
        Report alicesReport = saveReport(alice.getId(), "Alice report", LocalDateTime.now().minusHours(1));
        Report bobsReport = saveReport(bob.getId(), "Bob report", LocalDateTime.now());
        MockHttpSession session = loginAs("alice", "pass");

        mockMvc.perform(get("/api/reports").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(alicesReport.getId()))
                .andExpect(jsonPath("$[0].title").value("Alice report"))
                .andExpect(jsonPath("$[0].expenseCount").value(0));

        mockMvc.perform(get("/api/reports/" + alicesReport.getId()).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(alicesReport.getId()))
                .andExpect(jsonPath("$.title").value("Alice report"));

        mockMvc.perform(get("/api/reports/" + bobsReport.getId()).session(session))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Report not found"));
    }

    @Test
    void exportReportPdf_shouldReturnOwnedReportAsPdf() throws Exception {
        var alice = createTestUser("alice", "pass", "SGD");
        var store = createTestStore(alice.getId(), "NTUC", "Orchard", "SG");
        var expense = createTestExpense(alice.getId(), "Travel", BigDecimal.valueOf(42.50), "USD");
        expense.setNotes("Airport transfer");
        setExpenseDateAndStore(expense, LocalDateTime.of(2026, 4, 12, 9, 30), store.getId());

        Report report = new Report();
        report.setUserId(alice.getId());
        report.setTitle("Singapore Spending Report");
        report.setDescription("April travel summary");
        report.setExpenseIds(List.of(expense.getId()));
        report.setChartDefinitions(objectMapper.valueToTree(List.of(Map.of(
                "id", "spend-by-location",
                "title", "Spend by Location",
                "type", "BAR",
                "metric", "TOTAL_AMOUNT",
                "groupBy", "LOCATION",
                "limit", 10,
                "sort", "DESC"
        ))));
        report.setGroupBy(ReportGroupBy.STORE_LOCATION);
        report.setFilterSnapshot(objectMapper.valueToTree(Map.of(
                "mode", "FILTERS",
                "groupBy", "STORE_LOCATION",
                "country", "Singapore",
                "startDate", "2026-04-01",
                "endDate", "2026-04-30"
        )));
        report.setInsights(objectMapper.valueToTree(List.of("Most spending happened in Orchard, Singapore.")));
        report.setCreatedAt(LocalDateTime.of(2026, 4, 30, 10, 0));
        report.setUpdatedAt(LocalDateTime.of(2026, 4, 30, 10, 0));
        report = reportRepository.save(report);

        MockHttpSession session = loginAs("alice", "pass");

        MvcResult result = mockMvc.perform(get("/api/reports/" + report.getId() + "/pdf").session(session))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PDF))
                .andExpect(header().string("Content-Disposition", containsString("report-" + report.getId())))
                .andReturn();

        byte[] pdfBytes = result.getResponse().getContentAsByteArray();
        assertThat(new String(pdfBytes, 0, 4, StandardCharsets.US_ASCII)).isEqualTo("%PDF");

        try (PDDocument document = PDDocument.load(pdfBytes)) {
            String text = new PDFTextStripper().getText(document);
            assertThat(text)
                    .contains("Singapore Spending Report")
                    .contains("April travel summary")
                    .contains("Most spending happened in Orchard, Singapore.")
                    .contains("Travel")
                    .contains("Orchard, Singapore")
                    .contains("42.50 USD")
                    .contains("42.50 SGD");
        }
    }

    @Test
    void exportReportPdf_shouldHideForeignReport() throws Exception {
        createTestUser("alice", "pass");
        var bob = createTestUser("bob", "pass");
        Report bobsReport = saveReport(bob.getId(), "Bob report", LocalDateTime.now());
        MockHttpSession session = loginAs("alice", "pass");

        mockMvc.perform(get("/api/reports/" + bobsReport.getId() + "/pdf").session(session))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Report not found"));
    }

    @Test
    void deleteReport_shouldDeleteOwnedReportAndHideForeignReport() throws Exception {
        var alice = createTestUser("alice", "pass");
        var bob = createTestUser("bob", "pass");
        Report alicesReport = saveReport(alice.getId(), "Alice report", LocalDateTime.now().minusHours(1));
        Report bobsReport = saveReport(bob.getId(), "Bob report", LocalDateTime.now());
        MockHttpSession session = loginAs("alice", "pass");

        mockMvc.perform(delete("/api/reports/" + bobsReport.getId()).session(session))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Report not found"));

        mockMvc.perform(delete("/api/reports/" + alicesReport.getId()).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Report deleted"));

        assertThat(reportRepository.findById(alicesReport.getId())).isEmpty();
        assertThat(reportRepository.findById(bobsReport.getId())).isPresent();
    }

    @Test
    void reportEndpoints_unauthenticatedShouldReturn401() throws Exception {
        mockMvc.perform(get("/api/reports"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/reports/1/pdf"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/reports")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "title", "Unauthenticated",
                                "expenseIds", List.of(),
                                "chartDefinitions", List.of(),
                                "groupBy", "KEYWORD"
                        ))))
                .andExpect(status().isUnauthorized());
    }

    private Report saveReport(Long userId, String title, LocalDateTime createdAt) {
        Report report = new Report();
        report.setUserId(userId);
        report.setTitle(title);
        report.setDescription("Description for " + title);
        report.setExpenseIds(List.of());
        report.setChartDefinitions(objectMapper.createArrayNode());
        report.setGroupBy(ReportGroupBy.KEYWORD);
        report.setFilterSnapshot(objectMapper.createObjectNode().put("search", title.toLowerCase()));
        report.setInsights(objectMapper.createArrayNode());
        report.setCreatedAt(createdAt);
        report.setUpdatedAt(createdAt);
        return reportRepository.save(report);
    }

    private void setExpenseDateAndStore(com.delfino.expensetracker.model.Expense expense,
                                        LocalDateTime transactionDatetime,
                                        Long storeId) {
        expense.setTransactionDatetime(transactionDatetime);
        expense.setStoreId(storeId);
        expense.setUpdatedAt(LocalDateTime.now());
        expenseRepository.save(expense);
    }
}



