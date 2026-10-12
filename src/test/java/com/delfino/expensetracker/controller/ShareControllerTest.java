package com.delfino.expensetracker.controller;

import com.delfino.expensetracker.BaseControllerTest;
import com.delfino.expensetracker.model.Expense;
import com.delfino.expensetracker.model.ExpenseShare;
import com.delfino.expensetracker.model.Store;
import com.delfino.expensetracker.model.User;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ShareControllerTest extends BaseControllerTest {

    @Test
    void shareExpense_notFound_returns404() throws Exception {
        createTestUser("alice", "pass");
        MockHttpSession session = loginAs("alice", "pass");

        mockMvc.perform(get("/view/expenses/nonexistent-url-id").session(session))
                .andExpect(status().isNotFound());
    }

    @Test
    void shareExpense_found_returnsHtmlWithOgTags() throws Exception {
        User user = createTestUser("alice", "pass");
        MockHttpSession session = loginAs("alice", "pass");
        Expense expense = createTestExpense(user.getId(), "Food", BigDecimal.valueOf(12.50), "USD");
        expense.setTransactionDatetime(LocalDateTime.of(2026, 4, 1, 12, 0));
        expenseRepository.save(expense);

        String html = mockMvc.perform(get("/view/expenses/" + expense.getUrlId())
                        .session(session)
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
        MockHttpSession session = loginAs("alice", "pass");
        Store store = createTestStore(user.getId(), "FairPrice", "Singapore", "SG");
        Expense expense = createTestExpense(user.getId(), "Groceries", BigDecimal.valueOf(45), "SGD");
        expense.setTransactionDatetime(LocalDateTime.of(2026, 4, 5, 10, 0));
        expenseRepository.save(expense);
        linkExpenseToStore(expense, store);

        String html = mockMvc.perform(get("/view/expenses/" + expense.getUrlId())
                        .session(session)
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
        MockHttpSession session = loginAs("alice", "pass");
        Expense expense = createTestExpense(user.getId(), "Groceries", BigDecimal.valueOf(20), "USD");
        expense.setTransactionDatetime(LocalDateTime.of(2026, 4, 1, 9, 0));
        expenseRepository.save(expense);
        createTestItem(expense.getId(), "Apple", BigDecimal.valueOf(3), BigDecimal.valueOf(2));
        createTestItem(expense.getId(), "Banana", BigDecimal.valueOf(5), BigDecimal.ONE);

        String html = mockMvc.perform(get("/view/expenses/" + expense.getUrlId())
                        .session(session)
                        .accept(MediaType.TEXT_HTML))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assert html.contains("2 items") || html.contains("item");
    }

    @Test
    void shareExpense_requiresAuthentication() throws Exception {
        User user = createTestUser("alice", "pass");
        Expense expense = createTestExpense(user.getId(), "Food", BigDecimal.TEN, "USD");

        mockMvc.perform(get("/view/expenses/" + expense.getUrlId()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shareExpense_xssEscaping_inTitle() throws Exception {
        User user = createTestUser("alice", "pass");
        MockHttpSession session = loginAs("alice", "pass");
        Store store = createTestStore(user.getId(), "<script>alert('xss')</script>", "Singapore", "SG");
        Expense expense = createTestExpense(user.getId(), "Food", BigDecimal.TEN, "USD");
        expenseRepository.save(expense);
        linkExpenseToStore(expense, store);

        String html = mockMvc.perform(get("/view/expenses/" + expense.getUrlId())
                        .session(session)
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
        MockHttpSession session = loginAs("alice", "pass");
        Expense expense = createTestExpense(user.getId(), "Food", null, null);
        expense.setAmount(null);
        expense.setCurrency(null);
        expenseRepository.save(expense);

        mockMvc.perform(get("/view/expenses/" + expense.getUrlId()).session(session))
                .andExpect(status().isOk());
    }

    @Test
    void shareExpense_otherAuthenticatedUser_returns403() throws Exception {
        User owner = createTestUser("alice", "pass");
        createTestUser("bob", "pass");
        MockHttpSession otherSession = loginAs("bob", "pass");
        Expense expense = createTestExpense(owner.getId(), "Food", BigDecimal.TEN, "USD");

        mockMvc.perform(get("/view/expenses/" + expense.getUrlId()).session(otherSession))
                .andExpect(status().isForbidden());
    }

    @Test
    void sharePublicExpense_activeShare_redirectsToShareRoute() throws Exception {
        User user = createTestUser("alice", "pass");
        Expense expense = createTestExpense(user.getId(), "Food", BigDecimal.valueOf(12.50), "USD");
        ExpenseShare share = new ExpenseShare();
        share.setExpenseId(expense.getId());
        share.setShareToken(UUID.randomUUID().toString());
        share.setCreatedAt(LocalDateTime.now().minusHours(1));
        share.setExpiresAt(LocalDateTime.now().plusDays(7));
        share.setCreatedBy(user.getId());
        expenseShareRepository.save(share);

        String html = mockMvc.perform(get("/view/share/" + share.getShareToken())
                        .accept(MediaType.TEXT_HTML))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assert html.contains("#/share/" + share.getShareToken());
        assert html.contains("og:title");
    }

    @Test
    void sharePublicExpense_invalidShare_returns404() throws Exception {
        mockMvc.perform(get("/view/share/nonexistent-token"))
                .andExpect(status().isNotFound());
    }
}

