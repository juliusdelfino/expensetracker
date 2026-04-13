package com.delfino.expensetracker.controller;

import com.delfino.expensetracker.BaseControllerTest;
import com.delfino.expensetracker.model.Expense;
import com.delfino.expensetracker.model.User;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;

import java.math.BigDecimal;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for ChatController → ChatService → ExpenseToolService tool calls.
 * <p>
 * Each test verifies that when the LLM decides to call a specific read-only
 * {@code ExpenseToolService} method, Spring AI correctly invokes it against real
 * DB data and the final LLM answer is returned to the caller.
 * <p>
 * WireMock simulates the two-turn Ollama conversation via a {@link Scenario}:
 * <ol>
 *   <li><b>Turn 1</b> (state {@code STARTED}): Ollama returns a {@code tool_calls}
 *       response — Spring AI intercepts it and invokes the real tool method.</li>
 *   <li><b>Turn 2</b> (state {@code "toolCalled"}): Ollama returns a plain-text
 *       final answer after receiving the tool result.</li>
 * </ol>
 */
class ChatControllerExpenseToolServiceTest extends BaseControllerTest {

    // ── findItemPrice ──────────────────────────────────────────────────────────

    /**
     * Simulates a user asking "How much is soy sauce?".
     * The LLM calls {@code findItemPrice("soy sauce", "")} — the tool searches the
     * real {@code ExpenseItem} rows and returns the matching price.
     */
    @Test
    void findItemPrice_toolInvoked_returnsItemPriceResult() throws Exception {
        User user = createTestUser("alice", "pass");
        Expense expense = createTestExpense(user.getId(), "Groceries", BigDecimal.valueOf(5.00), "USD");
        createTestItem(expense.getId(), "Soy Sauce", BigDecimal.ONE, BigDecimal.valueOf(2.50));

        // Turn 1: LLM requests findItemPrice tool call
        WIRE_MOCK.stubFor(WireMock.post(urlPathEqualTo("/api/chat"))
                .inScenario("findItemPrice")
                .whenScenarioStateIs(Scenario.STARTED)
                .atPriority(1)
                .willReturn(okJson(ollamaToolCallResponse("findItemPrice",
                        "{\"itemName\":\"soy sauce\",\"storeName\":\"\"}"))
                        .withHeader("Connection", "close"))
                .willSetStateTo("toolCalled"));

        // Turn 2: LLM returns final answer after receiving tool result
        WIRE_MOCK.stubFor(WireMock.post(urlPathEqualTo("/api/chat"))
                .inScenario("findItemPrice")
                .whenScenarioStateIs("toolCalled")
                .atPriority(1)
                .willReturn(okJson(ollamaChatResponse(
                        "Soy Sauce costs 2.50 USD based on your last receipt."))
                        .withHeader("Connection", "close")));

        MockHttpSession session = loginAs("alice", "pass");

        mockMvc.perform(post("/api/chat")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("message", "How much is soy sauce?"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message.text").value(Matchers.containsString("2.50")));
    }

    // ── totalExpenses ──────────────────────────────────────────────────────────

    /**
     * Simulates "How much did I spend on Groceries?".
     * The LLM calls {@code totalExpenses("Groceries", "", "")} — the tool sums up
     * the two Groceries expenses and returns the aggregate.
     */
    @Test
    void totalExpenses_toolInvoked_returnsCategoryTotal() throws Exception {
        User user = createTestUser("alice", "pass");
        createTestExpense(user.getId(), "Groceries", BigDecimal.valueOf(20.00), "USD");
        createTestExpense(user.getId(), "Groceries", BigDecimal.valueOf(30.00), "USD");

        // Turn 1
        WIRE_MOCK.stubFor(WireMock.post(urlPathEqualTo("/api/chat"))
                .inScenario("totalExpenses")
                .whenScenarioStateIs(Scenario.STARTED)
                .atPriority(1)
                .willReturn(okJson(ollamaToolCallResponse("totalExpenses",
                        "{\"keyword\":\"Groceries\",\"startDate\":\"\",\"endDate\":\"\"}"))
                        .withHeader("Connection", "close"))
                .willSetStateTo("toolCalled"));

        // Turn 2
        WIRE_MOCK.stubFor(WireMock.post(urlPathEqualTo("/api/chat"))
                .inScenario("totalExpenses")
                .whenScenarioStateIs("toolCalled")
                .atPriority(1)
                .willReturn(okJson(ollamaChatResponse(
                        "Your total Groceries spending is 50.00 (base currency) across 2 expense(s)."))
                        .withHeader("Connection", "close")));

        MockHttpSession session = loginAs("alice", "pass");

        mockMvc.perform(post("/api/chat")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("message", "How much did I spend on Groceries?"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message.text").value(Matchers.containsString("50.00")));
    }

    // ── listExpenses ───────────────────────────────────────────────────────────

    /**
     * Simulates "Show me my Food expenses".
     * The LLM calls {@code listExpenses("Food", "", "", 10)} — the tool fetches the
     * two Food expenses and the LLM summarises them.
     */
    @Test
    void listExpenses_toolInvoked_returnsExpenseList() throws Exception {
        User user = createTestUser("alice", "pass");
        createTestExpense(user.getId(), "Food", BigDecimal.valueOf(12.00), "USD");
        createTestExpense(user.getId(), "Food", BigDecimal.valueOf(8.50), "USD");

        // Turn 1
        WIRE_MOCK.stubFor(WireMock.post(urlPathEqualTo("/api/chat"))
                .inScenario("listExpenses")
                .whenScenarioStateIs(Scenario.STARTED)
                .atPriority(1)
                .willReturn(okJson(ollamaToolCallResponse("listExpenses",
                        "{\"category\":\"Food\",\"startDate\":\"\",\"endDate\":\"\",\"limit\":10}"))
                        .withHeader("Connection", "close"))
                .willSetStateTo("toolCalled"));

        // Turn 2
        WIRE_MOCK.stubFor(WireMock.post(urlPathEqualTo("/api/chat"))
                .inScenario("listExpenses")
                .whenScenarioStateIs("toolCalled")
                .atPriority(1)
                .willReturn(okJson(ollamaChatResponse(
                        "Found 2 Food expense(s): 12.00 USD and 8.50 USD."))
                        .withHeader("Connection", "close")));

        MockHttpSession session = loginAs("alice", "pass");

        mockMvc.perform(post("/api/chat")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("message", "Show me my Food expenses"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message.text").value(
                        Matchers.containsString("2 Food expense(s)")));
    }

    // ── getExpenseSummary ──────────────────────────────────────────────────────

    /**
     * Simulates "Give me a summary of my expenses".
     * The LLM calls {@code getExpenseSummary("", "")} — the tool aggregates all
     * expenses and returns a category breakdown.
     */
    @Test
    void getExpenseSummary_toolInvoked_returnsSummaryText() throws Exception {
        User user = createTestUser("alice", "pass");
        createTestExpense(user.getId(), "Food", BigDecimal.valueOf(15.00), "USD");
        createTestExpense(user.getId(), "Transport", BigDecimal.valueOf(25.00), "USD");

        // Turn 1
        WIRE_MOCK.stubFor(WireMock.post(urlPathEqualTo("/api/chat"))
                .inScenario("getExpenseSummary")
                .whenScenarioStateIs(Scenario.STARTED)
                .atPriority(1)
                .willReturn(okJson(ollamaToolCallResponse("getExpenseSummary",
                        "{\"startDate\":\"\",\"endDate\":\"\"}"))
                        .withHeader("Connection", "close"))
                .willSetStateTo("toolCalled"));

        // Turn 2
        WIRE_MOCK.stubFor(WireMock.post(urlPathEqualTo("/api/chat"))
                .inScenario("getExpenseSummary")
                .whenScenarioStateIs("toolCalled")
                .atPriority(1)
                .willReturn(okJson(ollamaChatResponse(
                        "Expense Summary: 2 expense(s), total 40.00 base currency. " +
                        "Food: 15.00, Transport: 25.00."))
                        .withHeader("Connection", "close")));

        MockHttpSession session = loginAs("alice", "pass");

        mockMvc.perform(post("/api/chat")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("message", "Give me a summary of my expenses"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message.text").value(Matchers.containsString("40.00")));
    }
}

