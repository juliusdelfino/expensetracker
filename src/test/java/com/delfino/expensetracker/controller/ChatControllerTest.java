package com.delfino.expensetracker.controller;

import com.delfino.expensetracker.BaseControllerTest;
import com.delfino.expensetracker.model.ChatMessage;
import com.delfino.expensetracker.model.Expense;
import com.delfino.expensetracker.model.User;
import com.github.tomakehurst.wiremock.client.WireMock;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Tests for ChatController.
 * All tests use the real ChatService; the Ollama LLM endpoint is stubbed via WireMock
 * so no service classes are mocked.
 */
class ChatControllerTest extends BaseControllerTest {

    // ==================== POST /api/chat ====================

    @Test
    void sendMessage_unauthenticated_returns401() throws Exception {
        mockMvc.perform(post("/api/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("message", "hello"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Not authenticated"));
    }

    @Test
    void sendMessage_blankMessage_returnsBadRequest() throws Exception {
        createTestUser("alice", "pass");
        MockHttpSession session = loginAs("alice", "pass");

        mockMvc.perform(post("/api/chat")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("message", "   "))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Message is required"));
    }

    @Test
    void sendMessage_missingMessage_returnsBadRequest() throws Exception {
        createTestUser("alice", "pass");
        MockHttpSession session = loginAs("alice", "pass");

        mockMvc.perform(post("/api/chat")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Message is required"));
    }

    @Test
    void sendMessage_authenticated_ollamaReturnsPlainText_responseSaved() throws Exception {
        // Default stub: Ollama returns plain text (already set in registerDefaultStubs)
        createTestUser("alice", "pass");
        MockHttpSession session = loginAs("alice", "pass");

        mockMvc.perform(post("/api/chat")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("message", "hello"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.message.text").isString())
                .andExpect(jsonPath("$.expenseCards").isArray())
                .andExpect(jsonPath("$.expenseCards.length()").value(0));

        // Both user message and bot reply should be persisted
        assertThat(chatMessageRepository.count()).isEqualTo(2);
    }

    @Test
    void sendMessage_ollamaReturnsExpenseJson_expenseSavedAndLinked() throws Exception {
        // Override the default Ollama stub to return an expense-creation JSON.
        // Pass plain Java string to ollamaChatResponse() which handles JSON escaping.
        String expenseJsonContent =
                "{\"expenses\":[{\"transactionDatetime\":\"2026-04-08T12:00:00\"," +
                "\"amount\":10.0,\"currency\":\"USD\",\"category\":\"Lunch\"," +
                "\"notes\":\"test expense\"}]," +
                "\"summary\":\"Recorded your lunch!\"}";

        WIRE_MOCK.stubFor(WireMock.post(urlPathEqualTo("/api/chat")).atPriority(1)
                .willReturn(okJson(ollamaChatResponse(expenseJsonContent))
                        .withHeader("Connection", "close")));

        createTestUser("alice", "pass");
        MockHttpSession session = loginAs("alice", "pass");

        mockMvc.perform(post("/api/chat")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("message", "lunch 10 USD"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.expenseCards.length()").value(1))
                .andExpect(jsonPath("$.expenseCards[0].category").value("Lunch"))
                .andExpect(jsonPath("$.expenseCards[0].urlId").isString());

        // Expense should have been persisted by ChatService
        assertThat(expenseRepository.count()).isEqualTo(1);
    }

    @Test
    void sendMessage_ollamaCallFails_returnsErrorMessage() throws Exception {
        // Override stub to simulate Ollama being unavailable
        WIRE_MOCK.stubFor(WireMock.post(urlPathEqualTo("/api/chat")).atPriority(1)
                .willReturn(serverError().withHeader("Connection", "close")));

        createTestUser("alice", "pass");
        MockHttpSession session = loginAs("alice", "pass");

        mockMvc.perform(post("/api/chat")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("message", "hello"))))
                .andExpect(status().isOk())  // ChatService catches exceptions and returns a graceful message
                .andExpect(jsonPath("$.message.text").value(
                        org.hamcrest.Matchers.containsString("trouble processing")));
    }

    // ==================== GET /api/chat/history ====================

    @Test
    void getHistory_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/chat/history"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Not authenticated"));
    }

    @Test
    void getHistory_defaultPagination_emptyHistory() throws Exception {
        createTestUser("alice", "pass");
        MockHttpSession session = loginAs("alice", "pass");

        mockMvc.perform(get("/api/chat/history").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.messages").isArray())
                .andExpect(jsonPath("$.messages.length()").value(0))
                .andExpect(jsonPath("$.hasMore").value(false))
                .andExpect(jsonPath("$.total").value(0))
                .andExpect(jsonPath("$.expenses").isMap());
    }

    @Test
    void getHistory_withMessages_returnsChronologicalList() throws Exception {
        User user = createTestUser("alice", "pass");
        saveChatMessage(user.getId(), "USER", "What did I spend?", List.of());
        saveChatMessage(user.getId(), "BOT", "You spent $50 on Food.", List.of());
        MockHttpSession session = loginAs("alice", "pass");

        mockMvc.perform(get("/api/chat/history?limit=10&offset=0").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.messages.length()").value(2))
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.hasMore").value(false));
    }

    @Test
    void getHistory_withLinkedExpenses_populatesExpenseMap() throws Exception {
        User user = createTestUser("alice", "pass");
        Expense expense = createTestExpense(user.getId(), "Food", BigDecimal.TEN, "USD");
        saveChatMessage(user.getId(), "BOT", "Here is your expense.", List.of(expense.getId()));
        MockHttpSession session = loginAs("alice", "pass");

        mockMvc.perform(get("/api/chat/history").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.expenses").isMap())
                // expense map keyed by expense ID string
                .andExpect(jsonPath("$.expenses['" + expense.getId() + "'].category").value("Food"))
                .andExpect(jsonPath("$.expenses['" + expense.getId() + "'].urlId").isString());
    }

    @Test
    void getHistory_pagination_hasMoreWhenNotLastPage() throws Exception {
        User user = createTestUser("alice", "pass");
        for (int i = 0; i < 5; i++) {
            saveChatMessage(user.getId(), "USER", "Message " + i, List.of());
        }
        MockHttpSession session = loginAs("alice", "pass");

        mockMvc.perform(get("/api/chat/history?limit=3&offset=0").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(5))
                .andExpect(jsonPath("$.hasMore").value(true));
    }

    // ---- Private helper ----

    private ChatMessage saveChatMessage(long userId, String role, String text, List<Long> linkedIds) {
        ChatMessage msg = new ChatMessage();
        msg.setUserId(userId);
        msg.setRole(role);
        msg.setText(text);
        msg.setLinkedExpenseIds(linkedIds != null ? linkedIds : List.of());
        msg.setCreatedAt(LocalDateTime.now());
        return chatMessageRepository.save(msg);
    }
}

