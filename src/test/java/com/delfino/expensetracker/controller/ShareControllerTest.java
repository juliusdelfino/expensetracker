package com.delfino.expensetracker.controller;

import com.delfino.expensetracker.BaseControllerTest;
import com.delfino.expensetracker.model.Expense;
import com.delfino.expensetracker.model.Store;
import com.delfino.expensetracker.model.User;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ShareControllerTest extends BaseControllerTest {

    @Test
    void shareExpense_notFound_returns404() throws Exception {
        mockMvc.perform(get("/view/expenses/nonexistent-url-id"))
                .andExpect(status().isNotFound());
    }

    @Test
    void shareExpense_found_returnsHtmlWithOgTags() throws Exception {
        User user = createTestUser("alice", "pass");
        Expense expense = createTestExpense(user.getId(), "Food", BigDecimal.valueOf(12.50), "USD");
        expense.setTransactionDatetime(LocalDateTime.of(2026, 4, 1, 12, 0));
        expenseRepository.save(expense);

        String html = mockMvc.perform(get("/view/expenses/" + expense.getUrlId())
                        .accept(MediaType.TEXT_HTML))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andReturn()
                .getResponse()
                .getContentAsString();

        // Verify OG meta tags
        assert html.contains("og:type");
        assert html.contains("og:title");
        assert html.contains("og:description");
        assert html.contains("twitter:card");
        assert html.contains("Redirecting");
    }

    @Test
    void shareExpense_withStore_includesStoreNameInTitle() throws Exception {
        User user = createTestUser("alice", "pass");
        Store store = createTestStore(user.getId(), "FairPrice", "Singapore", "SG");
        Expense expense = createTestExpense(user.getId(), "Groceries", BigDecimal.valueOf(45), "SGD");
        expense.setTransactionDatetime(LocalDateTime.of(2026, 4, 5, 10, 0));
        expenseRepository.save(expense);
        linkExpenseToStore(expense, store);

        String html = mockMvc.perform(get("/view/expenses/" + expense.getUrlId())
                        .accept(MediaType.TEXT_HTML))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assert html.contains("FairPrice");
        assert html.contains("45");
        assert html.contains("SGD");
    }

    @Test
    void shareExpense_withItems_includesItemCountInDescription() throws Exception {
        User user = createTestUser("alice", "pass");
        Expense expense = createTestExpense(user.getId(), "Groceries", BigDecimal.valueOf(20), "USD");
        expense.setTransactionDatetime(LocalDateTime.of(2026, 4, 1, 9, 0));
        expenseRepository.save(expense);
        createTestItem(expense.getId(), "Apple", BigDecimal.valueOf(3), BigDecimal.valueOf(2));
        createTestItem(expense.getId(), "Banana", BigDecimal.valueOf(5), BigDecimal.ONE);

        String html = mockMvc.perform(get("/view/expenses/" + expense.getUrlId())
                        .accept(MediaType.TEXT_HTML))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assert html.contains("2 items") || html.contains("item");
    }

    @Test
    void shareExpense_noAuthRequired() throws Exception {
        User user = createTestUser("alice", "pass");
        Expense expense = createTestExpense(user.getId(), "Food", BigDecimal.TEN, "USD");

        // No session provided — should still succeed (public page)
        mockMvc.perform(get("/view/expenses/" + expense.getUrlId()))
                .andExpect(status().isOk());
    }

    @Test
    void shareExpense_xssEscaping_inTitle() throws Exception {
        User user = createTestUser("alice", "pass");
        Store store = createTestStore(user.getId(), "<script>alert('xss')</script>", "Singapore", "SG");
        Expense expense = createTestExpense(user.getId(), "Food", BigDecimal.TEN, "USD");
        expenseRepository.save(expense);
        linkExpenseToStore(expense, store);

        String html = mockMvc.perform(get("/view/expenses/" + expense.getUrlId())
                        .accept(MediaType.TEXT_HTML))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        // Script tag should be escaped
        assert !html.contains("<script>alert('xss')</script>");
        assert html.contains("&lt;script&gt;") || html.contains("&lt;");
    }

    @Test
    void shareExpense_nullAmountAndCurrency_noErrors() throws Exception {
        User user = createTestUser("alice", "pass");
        Expense expense = createTestExpense(user.getId(), "Food", null, null);
        expense.setAmount(null);
        expense.setCurrency(null);
        expenseRepository.save(expense);

        mockMvc.perform(get("/view/expenses/" + expense.getUrlId()))
                .andExpect(status().isOk());
    }
}

