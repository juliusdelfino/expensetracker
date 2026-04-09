package com.delfino.expensetracker.controller;

import com.delfino.expensetracker.BaseControllerTest;
import com.delfino.expensetracker.model.Expense;
import com.delfino.expensetracker.model.ExpenseItem;
import com.delfino.expensetracker.model.Store;
import com.delfino.expensetracker.model.User;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for ChatController → ChatService → ExpenseCrudToolService tool calls.
 * <p>
 * Each test covers one CRUD tool method. The WireMock scenario pattern stubs the
 * two-turn Ollama conversation (tool-call request → final answer), while the actual
 * tool executes against the real in-memory SQLite DB so DB mutations can be verified.
 */
class ChatControllerExpenseCrudToolServiceTest extends BaseControllerTest {

    // ── updateExpense ──────────────────────────────────────────────────────────

    /**
     * The LLM calls {@code updateExpense(id, "Transport", "")} to change an expense's
     * category. The DB row must reflect the new category after the tool executes.
     */
    @Test
    void updateExpense_toolInvoked_categoryChangedInDb() throws Exception {
        User user = createTestUser("alice", "pass");
        Expense expense = createTestExpense(user.getId(), "Food", BigDecimal.TEN, "USD");
        String idStr = String.valueOf(expense.getId());

        // Turn 1: LLM requests updateExpense
        WIRE_MOCK.stubFor(WireMock.post(urlPathEqualTo("/api/chat"))
                .inScenario("updateExpense")
                .whenScenarioStateIs(Scenario.STARTED)
                .atPriority(1)
                .willReturn(okJson(ollamaToolCallResponse("updateExpense",
                        "{\"expenseId\":\"" + idStr + "\",\"category\":\"Transport\",\"notes\":\"\"}"))
                        .withHeader("Connection", "close"))
                .willSetStateTo("toolCalled"));

        // Turn 2: LLM confirms the change
        WIRE_MOCK.stubFor(WireMock.post(urlPathEqualTo("/api/chat"))
                .inScenario("updateExpense")
                .whenScenarioStateIs("toolCalled")
                .atPriority(1)
                .willReturn(okJson(ollamaChatResponse(
                        "Done! Expense updated. Category: Transport."))
                        .withHeader("Connection", "close")));

        MockHttpSession session = loginAs("alice", "pass");

        mockMvc.perform(post("/api/chat")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("message", "Change expense " + idStr + " category to Transport"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message.text").value(Matchers.containsString("Transport")));

        // Verify the DB mutation
        Expense updated = expenseRepository.findById(expense.getId()).orElseThrow();
        assertThat(updated.getCategory()).isEqualTo("Transport");
    }

    // ── deleteExpense ──────────────────────────────────────────────────────────

    /**
     * The LLM calls {@code deleteExpense(id)}. The expense must be soft-deleted in the DB
     * (the row remains but {@code deleted = true}).
     */
    @Test
    void deleteExpense_toolInvoked_expenseSoftDeletedInDb() throws Exception {
        User user = createTestUser("alice", "pass");
        Expense expense = createTestExpense(user.getId(), "Food", BigDecimal.TEN, "USD");
        String idStr = String.valueOf(expense.getId());

        // Turn 1
        WIRE_MOCK.stubFor(WireMock.post(urlPathEqualTo("/api/chat"))
                .inScenario("deleteExpense")
                .whenScenarioStateIs(Scenario.STARTED)
                .atPriority(1)
                .willReturn(okJson(ollamaToolCallResponse("deleteExpense",
                        "{\"expenseId\":\"" + idStr + "\"}"))
                        .withHeader("Connection", "close"))
                .willSetStateTo("toolCalled"));

        // Turn 2
        WIRE_MOCK.stubFor(WireMock.post(urlPathEqualTo("/api/chat"))
                .inScenario("deleteExpense")
                .whenScenarioStateIs("toolCalled")
                .atPriority(1)
                .willReturn(okJson(ollamaChatResponse(
                        "Expense deleted (soft-delete). It can be restored from the Expenses page."))
                        .withHeader("Connection", "close")));

        MockHttpSession session = loginAs("alice", "pass");

        mockMvc.perform(post("/api/chat")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("message", "Delete expense " + idStr))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message.text").value(Matchers.containsString("deleted")));

        // Verify soft-delete (row still exists but flagged)
        Expense softDeleted = expenseRepository.findById(expense.getId()).orElseThrow();
        assertThat(softDeleted.isDeleted()).isTrue();
    }

    // ── getExpenseDetail ───────────────────────────────────────────────────────

    /**
     * The LLM calls {@code getExpenseDetail(id)} for an expense that has an item
     * and a linked store. The tool aggregates the full detail and the LLM presents it.
     */
    @Test
    void getExpenseDetail_toolInvoked_returnsExpenseDetails() throws Exception {
        User user = createTestUser("alice", "pass");
        Expense expense = createTestExpense(user.getId(), "Dining", BigDecimal.valueOf(45.00), "USD");
        createTestItem(expense.getId(), "Steak", BigDecimal.ONE, BigDecimal.valueOf(35.00));
        Store store = createTestStore(user.getId(), "Steakhouse", "Singapore", "SG");
        linkExpenseToStore(expense, store);
        String idStr = String.valueOf(expense.getId());

        // Turn 1
        WIRE_MOCK.stubFor(WireMock.post(urlPathEqualTo("/api/chat"))
                .inScenario("getExpenseDetail")
                .whenScenarioStateIs(Scenario.STARTED)
                .atPriority(1)
                .willReturn(okJson(ollamaToolCallResponse("getExpenseDetail",
                        "{\"expenseId\":\"" + idStr + "\"}"))
                        .withHeader("Connection", "close"))
                .willSetStateTo("toolCalled"));

        // Turn 2
        WIRE_MOCK.stubFor(WireMock.post(urlPathEqualTo("/api/chat"))
                .inScenario("getExpenseDetail")
                .whenScenarioStateIs("toolCalled")
                .atPriority(1)
                .willReturn(okJson(ollamaChatResponse(
                        "Expense Details: 45.00 USD, Dining, Steakhouse Singapore, " +
                        "item: Steak x1 @ 35.00 USD."))
                        .withHeader("Connection", "close")));

        MockHttpSession session = loginAs("alice", "pass");

        mockMvc.perform(post("/api/chat")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("message", "Show me details for expense " + idStr))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message.text").value(Matchers.containsString("45.00")));
    }

    // ── addExpenseItem ─────────────────────────────────────────────────────────

    /**
     * The LLM calls {@code addExpenseItem(id, "Milk", 2.0, 1.50)}.
     * A new {@code ExpenseItem} row must be created in the DB.
     */
    @Test
    void addExpenseItem_toolInvoked_itemCreatedInDb() throws Exception {
        User user = createTestUser("alice", "pass");
        Expense expense = createTestExpense(user.getId(), "Groceries", BigDecimal.TEN, "USD");
        String idStr = String.valueOf(expense.getId());

        // Turn 1
        WIRE_MOCK.stubFor(WireMock.post(urlPathEqualTo("/api/chat"))
                .inScenario("addExpenseItem")
                .whenScenarioStateIs(Scenario.STARTED)
                .atPriority(1)
                .willReturn(okJson(ollamaToolCallResponse("addExpenseItem",
                        "{\"expenseId\":\"" + idStr + "\",\"itemName\":\"Milk\",\"quantity\":2.0,\"unitPrice\":1.50}"))
                        .withHeader("Connection", "close"))
                .willSetStateTo("toolCalled"));

        // Turn 2
        WIRE_MOCK.stubFor(WireMock.post(urlPathEqualTo("/api/chat"))
                .inScenario("addExpenseItem")
                .whenScenarioStateIs("toolCalled")
                .atPriority(1)
                .willReturn(okJson(ollamaChatResponse(
                        "Item added: Milk (qty: 2.0, unit: 1.50, total: 3.00)."))
                        .withHeader("Connection", "close")));

        MockHttpSession session = loginAs("alice", "pass");

        mockMvc.perform(post("/api/chat")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("message", "Add 2 Milk at 1.50 to expense " + idStr))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message.text").value(Matchers.containsString("Milk")));

        // Verify the item was actually persisted
        List<ExpenseItem> items = expenseItemRepository.findByExpenseIdAndDeletedFalse(expense.getId());
        assertThat(items).hasSize(1);
        assertThat(items.get(0).getItemName()).isEqualTo("Milk");
    }

    // ── deleteExpenseItem ──────────────────────────────────────────────────────

    /**
     * The LLM calls {@code deleteExpenseItem(expId, itemId)}.
     * The item must be soft-deleted so that
     * {@code findByExpenseIdAndDeletedFalse} returns an empty list.
     */
    @Test
    void deleteExpenseItem_toolInvoked_itemSoftDeletedInDb() throws Exception {
        User user = createTestUser("alice", "pass");
        Expense expense = createTestExpense(user.getId(), "Groceries", BigDecimal.TEN, "USD");
        ExpenseItem item = createTestItem(expense.getId(), "Bread", BigDecimal.ONE, BigDecimal.valueOf(2.00));
        String expIdStr = String.valueOf(expense.getId());
        String itemIdStr = String.valueOf(item.getId());

        // Turn 1
        WIRE_MOCK.stubFor(WireMock.post(urlPathEqualTo("/api/chat"))
                .inScenario("deleteExpenseItem")
                .whenScenarioStateIs(Scenario.STARTED)
                .atPriority(1)
                .willReturn(okJson(ollamaToolCallResponse("deleteExpenseItem",
                        "{\"expenseId\":\"" + expIdStr + "\",\"itemId\":\"" + itemIdStr + "\"}"))
                        .withHeader("Connection", "close"))
                .willSetStateTo("toolCalled"));

        // Turn 2
        WIRE_MOCK.stubFor(WireMock.post(urlPathEqualTo("/api/chat"))
                .inScenario("deleteExpenseItem")
                .whenScenarioStateIs("toolCalled")
                .atPriority(1)
                .willReturn(okJson(ollamaChatResponse("Item deleted."))
                        .withHeader("Connection", "close")));

        MockHttpSession session = loginAs("alice", "pass");

        mockMvc.perform(post("/api/chat")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("message",
                                        "Delete item " + itemIdStr + " from expense " + expIdStr))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message.text").value(Matchers.containsString("deleted")));

        // Item should no longer appear in the non-deleted view
        List<ExpenseItem> remaining =
                expenseItemRepository.findByExpenseIdAndDeletedFalse(expense.getId());
        assertThat(remaining).isEmpty();
    }

    // ── getStores ──────────────────────────────────────────────────────────────

    /**
     * The LLM calls {@code getStores("", "", "")} to list all visited stores.
     * Two expenses are linked to Supermart and one to FreshGrocer.
     */
    @Test
    void getStores_toolInvoked_returnsVisitedStoreList() throws Exception {
        User user = createTestUser("alice", "pass");
        Store supermart = createTestStore(user.getId(), "Supermart", "Singapore", "SG");
        Store freshGrocer = createTestStore(user.getId(), "FreshGrocer", "Singapore", "SG");

        Expense e1 = createTestExpense(user.getId(), "Groceries", BigDecimal.TEN, "SGD");
        Expense e2 = createTestExpense(user.getId(), "Groceries", BigDecimal.TEN, "SGD");
        Expense e3 = createTestExpense(user.getId(), "Food", BigDecimal.valueOf(5), "SGD");
        linkExpenseToStore(e1, supermart);
        linkExpenseToStore(e2, supermart);
        linkExpenseToStore(e3, freshGrocer);

        // Turn 1
        WIRE_MOCK.stubFor(WireMock.post(urlPathEqualTo("/api/chat"))
                .inScenario("getStores")
                .whenScenarioStateIs(Scenario.STARTED)
                .atPriority(1)
                .willReturn(okJson(ollamaToolCallResponse("getStores",
                        "{\"startDate\":\"\",\"endDate\":\"\",\"country\":\"\"}"))
                        .withHeader("Connection", "close"))
                .willSetStateTo("toolCalled"));

        // Turn 2
        WIRE_MOCK.stubFor(WireMock.post(urlPathEqualTo("/api/chat"))
                .inScenario("getStores")
                .whenScenarioStateIs("toolCalled")
                .atPriority(1)
                .willReturn(okJson(ollamaChatResponse(
                        "Stores visited: Supermart (2 visits), FreshGrocer (1 visit)."))
                        .withHeader("Connection", "close")));

        MockHttpSession session = loginAs("alice", "pass");

        mockMvc.perform(post("/api/chat")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("message", "What stores have I visited?"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message.text").value(Matchers.containsString("Supermart")));
    }
}

