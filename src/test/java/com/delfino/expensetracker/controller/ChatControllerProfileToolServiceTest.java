package com.delfino.expensetracker.controller;

import com.delfino.expensetracker.BaseControllerTest;
import com.delfino.expensetracker.model.User;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;

import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for ChatController → ChatService → ProfileToolService tool calls.
 * <p>
 * Both {@code getProfile} and {@code updateProfile} are exercised through the real
 * chat endpoint. The two-turn Ollama conversation is simulated with a WireMock
 * {@link Scenario}: Turn 1 returns a tool-call request; Turn 2 returns the LLM's
 * final plain-text answer after receiving the tool result.
 */
class ChatControllerProfileToolServiceTest extends BaseControllerTest {

    // ── getProfile ─────────────────────────────────────────────────────────────

    /**
     * The LLM calls the parameterless {@code getProfile()} tool.
     * The tool reads the current user's profile from the DB and the LLM summarises it.
     */
    @Test
    void getProfile_toolInvoked_returnsUserProfileInfo() throws Exception {
        User user = createTestUser("alice", "pass", "USD");
        // Enrich the profile with phone, city and country to exercise all fields
        user.setPhoneNumber("+1234567890");
        user.setBaseCity("Singapore");
        user.setBaseCountry("SG");
        userRepository.save(user);

        // Turn 1: LLM requests getProfile — no arguments needed for a parameterless tool
        WIRE_MOCK.stubFor(WireMock.post(urlPathEqualTo("/api/chat"))
                .inScenario("getProfile")
                .whenScenarioStateIs(Scenario.STARTED)
                .atPriority(1)
                .willReturn(okJson(ollamaToolCallResponse("getProfile", "{}"))
                        .withHeader("Connection", "close"))
                .willSetStateTo("toolCalled"));

        // Turn 2: LLM returns the formatted profile summary
        WIRE_MOCK.stubFor(WireMock.post(urlPathEqualTo("/api/chat"))
                .inScenario("getProfile")
                .whenScenarioStateIs("toolCalled")
                .atPriority(1)
                .willReturn(okJson(ollamaChatResponse(
                        "Your profile: Username: alice, Email: alice@test.com, " +
                        "Phone: +1234567890, Base Currency: USD, " +
                        "Base City: Singapore, Base Country: SG."))
                        .withHeader("Connection", "close")));

        MockHttpSession session = loginAs("alice", "pass");

        mockMvc.perform(post("/api/chat")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("message", "Show me my profile"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message.text").value(Matchers.containsString("alice")));
    }

    // ── updateProfile ──────────────────────────────────────────────────────────

    /**
     * The LLM calls {@code updateProfile("", "", "SGD", "", "")} to change the user's
     * base currency. The DB row must reflect {@code baseCurrency = "SGD"} afterwards.
     */
    @Test
    void updateProfile_toolInvoked_baseCurrencyChangedInDb() throws Exception {
        User user = createTestUser("alice", "pass", "USD");

        // Turn 1: LLM requests updateProfile with only baseCurrency filled in
        WIRE_MOCK.stubFor(WireMock.post(urlPathEqualTo("/api/chat"))
                .inScenario("updateProfile")
                .whenScenarioStateIs(Scenario.STARTED)
                .atPriority(1)
                .willReturn(okJson(ollamaToolCallResponse("updateProfile",
                        "{\"email\":\"\",\"phoneNumber\":\"\",\"baseCurrency\":\"SGD\"," +
                        "\"baseCity\":\"\",\"baseCountry\":\"\"}"))
                        .withHeader("Connection", "close"))
                .willSetStateTo("toolCalled"));

        // Turn 2: LLM confirms the update
        WIRE_MOCK.stubFor(WireMock.post(urlPathEqualTo("/api/chat"))
                .inScenario("updateProfile")
                .whenScenarioStateIs("toolCalled")
                .atPriority(1)
                .willReturn(okJson(ollamaChatResponse(
                        "Profile updated: Base Currency → SGD."))
                        .withHeader("Connection", "close")));

        MockHttpSession session = loginAs("alice", "pass");

        mockMvc.perform(post("/api/chat")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("message", "Update my base currency to SGD"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message.text").value(Matchers.containsString("SGD")));

        // Verify the DB mutation
        User updated = userRepository.findById(user.getId()).orElseThrow();
        assertThat(updated.getBaseCurrency()).isEqualTo("SGD");
    }
}

