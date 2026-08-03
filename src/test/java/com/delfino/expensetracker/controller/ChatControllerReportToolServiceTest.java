package com.delfino.expensetracker.controller;

import com.delfino.expensetracker.BaseControllerTest;
import com.delfino.expensetracker.model.Report;
import com.delfino.expensetracker.model.ReportGroupBy;
import com.delfino.expensetracker.model.User;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ChatControllerReportToolServiceTest extends BaseControllerTest {

    @Test
    void generateExpenseReport_toolInvoked_createsReportAndReturnsReportCard() throws Exception {
        User user = createTestUser("alice", "pass");
        createTestExpense(user.getId(), "Food", BigDecimal.valueOf(12.00), "USD");
        createTestExpense(user.getId(), "Food", BigDecimal.valueOf(18.00), "USD");

        WIRE_MOCK.stubFor(WireMock.post(urlPathEqualTo("/api/chat"))
                .inScenario("generateExpenseReport")
                .whenScenarioStateIs(Scenario.STARTED)
                .atPriority(1)
                .willReturn(okJson(ollamaToolCallResponse("generateExpenseReport",
                        "{\"groupBy\":\"CATEGORY\",\"startDate\":\"\",\"endDate\":\"\",\"keyword\":\"\",\"category\":\"Food\",\"country\":\"\",\"storeName\":\"\"}"))
                        .withHeader("Connection", "close"))
                .willSetStateTo("toolCalled"));

        WIRE_MOCK.stubFor(WireMock.post(urlPathEqualTo("/api/chat"))
                .inScenario("generateExpenseReport")
                .whenScenarioStateIs("toolCalled")
                .atPriority(1)
                .willReturn(okJson(ollamaChatResponse(
                        "Done — I created your Food report and attached it below."))
                        .withHeader("Connection", "close")));

        MockHttpSession session = loginAs("alice", "pass");

        mockMvc.perform(post("/api/chat")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("message", "Create a food report"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message.text").value(Matchers.containsString("Food report")))
                .andExpect(jsonPath("$.message.linkedReportIds.length()").value(1))
                .andExpect(jsonPath("$.reportCards.length()").value(1))
                .andExpect(jsonPath("$.reportCards[0].title").value(Matchers.containsString("Category Report")))
                .andExpect(jsonPath("$.reportCards[0].groupBy").value("CATEGORY"))
                .andExpect(jsonPath("$.reportCards[0].expenseCount").value(2));

        assertThat(reportRepository.count()).isEqualTo(1);
        Report saved = reportRepository.findByUserIdOrderByCreatedAtDesc(user.getId()).get(0);
        assertThat(saved.getTitle()).contains("Category Report");
        assertThat(saved.getExpenseIds()).hasSize(2);
    }

    @Test
    void listReports_toolInvoked_returnsReferencedReportCards() throws Exception {
        User user = createTestUser("alice", "pass");
        Report first = saveReport(user.getId(), "Japan Trip", LocalDateTime.now().minusDays(1));
        Report second = saveReport(user.getId(), "Groceries Overview", LocalDateTime.now());

        WIRE_MOCK.stubFor(WireMock.post(urlPathEqualTo("/api/chat"))
                .inScenario("listReports")
                .whenScenarioStateIs(Scenario.STARTED)
                .atPriority(1)
                .willReturn(okJson(ollamaToolCallResponse("listReports", "{\"limit\":5}"))
                        .withHeader("Connection", "close"))
                .willSetStateTo("toolCalled"));

        WIRE_MOCK.stubFor(WireMock.post(urlPathEqualTo("/api/chat"))
                .inScenario("listReports")
                .whenScenarioStateIs("toolCalled")
                .atPriority(1)
                .willReturn(okJson(ollamaChatResponse(
                        "Here are your latest saved reports."))
                        .withHeader("Connection", "close")));

        MockHttpSession session = loginAs("alice", "pass");

        mockMvc.perform(post("/api/chat")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("message", "Show my saved reports"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reportCards.length()").value(2))
                .andExpect(jsonPath("$.reportCards[0].title").value(second.getTitle()))
                .andExpect(jsonPath("$.reportCards[1].title").value(first.getTitle()));
    }

    private Report saveReport(Long userId, String title, LocalDateTime createdAt) {
        Report report = new Report();
        report.setUserId(userId);
        report.setTitle(title);
        report.setDescription("Saved report " + title);
        report.setExpenseIds(List.of());
        report.setChartDefinitions(objectMapper.createArrayNode());
        report.setGroupBy(ReportGroupBy.KEYWORD);
        report.setFilterSnapshot(objectMapper.createObjectNode().put("search", title.toLowerCase()));
        report.setInsights(objectMapper.createArrayNode());
        report.setCreatedAt(createdAt);
        report.setUpdatedAt(createdAt);
        return reportRepository.save(report);
    }
}


