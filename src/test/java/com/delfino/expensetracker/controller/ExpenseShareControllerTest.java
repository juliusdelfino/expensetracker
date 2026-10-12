package com.delfino.expensetracker.controller;

import com.delfino.expensetracker.BaseControllerTest;
import com.delfino.expensetracker.model.Expense;
import com.delfino.expensetracker.model.ExpenseShare;
import com.delfino.expensetracker.model.User;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ExpenseShareControllerTest extends BaseControllerTest {

    @Test
    void createShare_authenticatedOwner_returnsShareTokenAndUrl() throws Exception {
        User user = createTestUser("alice", "pass");
        Expense expense = createTestExpense(user.getId(), "Food", BigDecimal.TEN, "USD");
        MockHttpSession session = loginAs("alice", "pass");

        mockMvc.perform(post("/api/expenses/{urlId}/share", expense.getUrlId())
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.shareToken").isString())
                .andExpect(jsonPath("$.shareUrl").value(org.hamcrest.Matchers.containsString("/view/share/")));

        assertThat(expenseShareRepository.findAll()).hasSize(1);
    }

    @Test
    void getShare_withoutActiveShare_returnsInactiveStatus() throws Exception {
        User user = createTestUser("alice", "pass");
        Expense expense = createTestExpense(user.getId(), "Food", BigDecimal.TEN, "USD");
        MockHttpSession session = loginAs("alice", "pass");

        mockMvc.perform(get("/api/expenses/{urlId}/share", expense.getUrlId()).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false))
                .andExpect(jsonPath("$.shareToken").doesNotExist());
    }

    @Test
    void revokeShare_marksShareInactive() throws Exception {
        User user = createTestUser("alice", "pass");
        Expense expense = createTestExpense(user.getId(), "Food", BigDecimal.TEN, "USD");
        MockHttpSession session = loginAs("alice", "pass");

        mockMvc.perform(post("/api/expenses/{urlId}/share", expense.getUrlId())
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/expenses/{urlId}/share", expense.getUrlId()).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false))
                .andExpect(jsonPath("$.revokedAt").isNotEmpty());

        ExpenseShare share = expenseShareRepository.findAll().get(0);
        assertThat(share.getRevokedAt()).isNotNull();
    }

    @Test
    void createShare_otherUsersExpense_returns403() throws Exception {
        User owner = createTestUser("alice", "pass");
        User other = createTestUser("bob", "pass");
        Expense expense = createTestExpense(owner.getId(), "Food", BigDecimal.TEN, "USD");
        MockHttpSession session = loginAs("bob", "pass");

        mockMvc.perform(post("/api/expenses/{urlId}/share", expense.getUrlId())
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());

        assertThat(other.getId()).isNotEqualTo(owner.getId());
    }

    @Test
    void createShare_customTtlDays_setsRequestedExpiryWindow() throws Exception {
        User user = createTestUser("alice", "pass");
        Expense expense = createTestExpense(user.getId(), "Food", BigDecimal.TEN, "USD");
        MockHttpSession session = loginAs("alice", "pass");

        mockMvc.perform(post("/api/expenses/{urlId}/share", expense.getUrlId())
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("ttlDays", 7))))
                .andExpect(status().isOk());

        ExpenseShare share = expenseShareRepository.findAll().get(0);
        long days = java.time.Duration.between(share.getCreatedAt(), share.getExpiresAt()).toDays();
        assertThat(days).isEqualTo(7);
    }

    @Test
    void createShare_ttlLongerThanDefault_isClampedToConfiguredDefault() throws Exception {
        User user = createTestUser("alice", "pass");
        Expense expense = createTestExpense(user.getId(), "Food", BigDecimal.TEN, "USD");
        MockHttpSession session = loginAs("alice", "pass");

        mockMvc.perform(post("/api/expenses/{urlId}/share", expense.getUrlId())
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("ttlDays", 365))))
                .andExpect(status().isOk());

        ExpenseShare share = expenseShareRepository.findAll().get(0);
        long days = java.time.Duration.between(share.getCreatedAt(), share.getExpiresAt()).toDays();
        assertThat(days).isEqualTo(30);
    }

    @Test
    void createShare_unauthenticated_returns401() throws Exception {
        User user = createTestUser("alice", "pass");
        Expense expense = createTestExpense(user.getId(), "Food", BigDecimal.TEN, "USD");

        mockMvc.perform(post("/api/expenses/{urlId}/share", expense.getUrlId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }
}


