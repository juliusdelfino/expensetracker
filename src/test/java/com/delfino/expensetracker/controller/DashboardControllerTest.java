package com.delfino.expensetracker.controller;

import com.delfino.expensetracker.BaseControllerTest;
import com.delfino.expensetracker.model.Expense;
import com.delfino.expensetracker.model.ExpenseStatus;
import com.delfino.expensetracker.model.Store;
import com.delfino.expensetracker.model.User;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class DashboardControllerTest extends BaseControllerTest {

    @Test
    void dashboard_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/dashboard"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void dashboard_empty_returnsEmptyStructure() throws Exception {
        createTestUser("alice", "pass");
        MockHttpSession session = loginAs("alice", "pass");

        mockMvc.perform(get("/api/dashboard").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.monthlyTotals").isMap())
                .andExpect(jsonPath("$.weeklyTotals").isMap())
                .andExpect(jsonPath("$.annualTotals").isMap())
                .andExpect(jsonPath("$.categoryTotals").isMap())
                .andExpect(jsonPath("$.timeline").isMap())
                .andExpect(jsonPath("$.geoData").isArray())
                .andExpect(jsonPath("$.geoByCountry").isArray())
                .andExpect(jsonPath("$.topShops").isArray())
                .andExpect(jsonPath("$.topItems").isArray())
                .andExpect(jsonPath("$.topExpenses").isArray())
                .andExpect(jsonPath("$.totalExpenses").value(0))
                .andExpect(jsonPath("$.categories").isArray())
                .andExpect(jsonPath("$.discoveryCards").isArray());
    }

    @Test
    void dashboard_withExpenses_populatesAggregates() throws Exception {
        User user = createTestUser("alice", "pass");
        Expense e1 = createCompletedExpense(user.getId(), "Food", BigDecimal.valueOf(50),
                LocalDateTime.of(2026, 3, 15, 12, 0));
        Expense e2 = createCompletedExpense(user.getId(), "Transport", BigDecimal.valueOf(20),
                LocalDateTime.of(2026, 3, 20, 12, 0));
        MockHttpSession session = loginAs("alice", "pass");

        mockMvc.perform(get("/api/dashboard").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalExpenses").value(2))
                .andExpect(jsonPath("$.categoryTotals.Food").value(e1.getAmount().intValue()))
                .andExpect(jsonPath("$.categoryTotals.Transport").value(e2.getAmount().intValue()))
                .andExpect(jsonPath("$.monthlyTotals['2026-03']").value(e1.getAmount().add(e2.getAmount()).intValue()))
                .andExpect(jsonPath("$.annualTotals['2026']").value(e1.getAmount().add(e2.getAmount()).intValue()));
    }

    @Test
    void dashboard_filterByDateRange() throws Exception {
        User user = createTestUser("alice", "pass");
        createCompletedExpense(user.getId(), "Old", BigDecimal.valueOf(100),
                LocalDateTime.of(2024, 6, 1, 12, 0));
        createCompletedExpense(user.getId(), "Recent", BigDecimal.valueOf(30),
                LocalDateTime.of(2026, 4, 1, 12, 0));
        MockHttpSession session = loginAs("alice", "pass");

        mockMvc.perform(get("/api/dashboard?startDate=2026-01-01&endDate=2026-12-31").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalExpenses").value(1))
                .andExpect(jsonPath("$.categoryTotals.Recent").value(30));
    }

    @Test
    void dashboard_filterByCategory() throws Exception {
        User user = createTestUser("alice", "pass");
        createCompletedExpense(user.getId(), "Food", BigDecimal.valueOf(50),
                LocalDateTime.of(2026, 3, 15, 12, 0));
        createCompletedExpense(user.getId(), "Transport", BigDecimal.valueOf(20),
                LocalDateTime.of(2026, 3, 20, 12, 0));
        MockHttpSession session = loginAs("alice", "pass");

        mockMvc.perform(get("/api/dashboard?category=Food").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalExpenses").value(1))
                .andExpect(jsonPath("$.categoryTotals.Food").value(50));
    }

    @Test
    void dashboard_topShops_returnsUpToFive() throws Exception {
        User user = createTestUser("alice", "pass");
        Store store = createTestStore(user.getId(), "FairPrice", "Singapore", "SG");

        for (int i = 0; i < 3; i++) {
            Expense e = createCompletedExpense(user.getId(), "Groceries", BigDecimal.TEN,
                    LocalDateTime.of(2026, 4, 1 + i, 12, 0));
            linkExpenseToStore(e, store);
        }

        MockHttpSession session = loginAs("alice", "pass");

        mockMvc.perform(get("/api/dashboard").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.topShops.length()").value(1))
                .andExpect(jsonPath("$.topShops[0].name").value("FairPrice"))
                .andExpect(jsonPath("$.topShops[0].visits").value(3));
    }

    @Test
    void dashboard_topExpenses_onlyCompleted() throws Exception {
        User user = createTestUser("alice", "pass");
        createCompletedExpense(user.getId(), "Expensive", BigDecimal.valueOf(500),
                LocalDateTime.of(2026, 4, 1, 12, 0));

        Expense draft = createTestExpense(user.getId(), "Draft", BigDecimal.valueOf(1000), "USD");
        draft.setStatus(ExpenseStatus.DRAFT);
        expenseRepository.save(draft);

        MockHttpSession session = loginAs("alice", "pass");

        mockMvc.perform(get("/api/dashboard").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.topExpenses.length()").value(1))
                .andExpect(jsonPath("$.topExpenses[0].category").value("Expensive"));
    }

    @Test
    void dashboard_geoData_withCoordinates() throws Exception {
        User user = createTestUser("alice", "pass");
        Store store = createTestStore(user.getId(), "Shop", "Singapore", "SG");
        Expense expense = createCompletedExpense(user.getId(), "Food", BigDecimal.TEN,
                LocalDateTime.of(2026, 4, 1, 12, 0));
        linkExpenseToStore(expense, store);

        MockHttpSession session = loginAs("alice", "pass");

        mockMvc.perform(get("/api/dashboard").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.geoData.length()").value(1))
                .andExpect(jsonPath("$.geoData[0].lat").value(1.3521))
                .andExpect(jsonPath("$.geoData[0].lng").value(103.8198));
    }

    @Test
    void dashboard_geoByCountry_aggregates() throws Exception {
        User user = createTestUser("alice", "pass");
        Store store1 = createTestStore(user.getId(), "Shop1", "Singapore", "SG");
        Store store2 = createTestStore(user.getId(), "Shop2", "Singapore", "SG");
        Expense e1 = createCompletedExpense(user.getId(), "Food", BigDecimal.TEN,
                LocalDateTime.of(2026, 4, 1, 12, 0));
        Expense e2 = createCompletedExpense(user.getId(), "Drink", BigDecimal.valueOf(5),
                LocalDateTime.of(2026, 4, 2, 12, 0));
        linkExpenseToStore(e1, store1);
        linkExpenseToStore(e2, store2);

        MockHttpSession session = loginAs("alice", "pass");

        mockMvc.perform(get("/api/dashboard").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.geoByCountry.length()").value(1))
                .andExpect(jsonPath("$.geoByCountry[0].country").value("SG"))
                .andExpect(jsonPath("$.geoByCountry[0].count").value(2));
    }

    @Test
    void dashboard_minMaxDate() throws Exception {
        User user = createTestUser("alice", "pass");
        createCompletedExpense(user.getId(), "A", BigDecimal.TEN,
                LocalDateTime.of(2026, 1, 1, 12, 0));
        createCompletedExpense(user.getId(), "B", BigDecimal.TEN,
                LocalDateTime.of(2026, 12, 31, 12, 0));
        MockHttpSession session = loginAs("alice", "pass");

        mockMvc.perform(get("/api/dashboard").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.minDate").value("2026-01-01"))
                .andExpect(jsonPath("$.maxDate").value("2026-12-31"));
    }

    @Test
    void dashboard_perMonthStats() throws Exception {
        User user = createTestUser("alice", "pass");
        createCompletedExpense(user.getId(), "Food", BigDecimal.TEN,
                LocalDateTime.of(2026, 3, 15, 12, 0));
        createCompletedExpense(user.getId(), "Food", BigDecimal.valueOf(20),
                LocalDateTime.of(2026, 3, 20, 12, 0));
        MockHttpSession session = loginAs("alice", "pass");

        mockMvc.perform(get("/api/dashboard").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.perMonthTxCount['2026-03']").value(2))
                .andExpect(jsonPath("$.perMonthTopCategory['2026-03']").value("Food"));
    }

    @Test
    void dashboard_topItems_fromExpenseItems() throws Exception {
        User user = createTestUser("alice", "pass");
        Expense e1 = createCompletedExpense(user.getId(), "Groceries", BigDecimal.TEN,
                LocalDateTime.of(2026, 4, 1, 12, 0));
        createTestItem(e1.getId(), "Milk", BigDecimal.valueOf(2), BigDecimal.valueOf(3));
        createTestItem(e1.getId(), "Bread", BigDecimal.ONE, BigDecimal.valueOf(2));
        MockHttpSession session = loginAs("alice", "pass");

        mockMvc.perform(get("/api/dashboard").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.topItems.length()").value(2));
    }

    // ---- Private helper ----

    private Expense createCompletedExpense(long userId, String category, BigDecimal amount, LocalDateTime datetime) {
        Expense expense = createTestExpense(userId, category, amount, "USD");
        expense.setTransactionDatetime(datetime);
        expense.setStatus(ExpenseStatus.COMPLETED);
        return expenseRepository.save(expense);
    }
}

