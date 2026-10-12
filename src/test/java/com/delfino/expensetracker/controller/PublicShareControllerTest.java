package com.delfino.expensetracker.controller;

import com.delfino.expensetracker.BaseControllerTest;
import com.delfino.expensetracker.model.Expense;
import com.delfino.expensetracker.model.ExpenseShare;
import com.delfino.expensetracker.model.User;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.assertj.core.api.Assertions.assertThat;

class PublicShareControllerTest extends BaseControllerTest {

    @Test
    void getSharedExpense_activeShare_returnsExpenseData() throws Exception {
        User user = createTestUser("alice", "pass");
        Expense expense = createTestExpense(user.getId(), "Food", BigDecimal.TEN, "USD");
        createTestItem(expense.getId(), "Bread", BigDecimal.ONE, BigDecimal.valueOf(2));

        ExpenseShare share = new ExpenseShare();
        share.setExpenseId(expense.getId());
        share.setShareToken(UUID.randomUUID().toString());
        share.setCreatedAt(LocalDateTime.now().minusDays(1));
        share.setExpiresAt(LocalDateTime.now().plusDays(7));
        share.setCreatedBy(user.getId());
        expenseShareRepository.save(share);

        mockMvc.perform(get("/api/share/{shareToken}/expense", share.getShareToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.expense.category").value("Food"))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.isOwner").value(false));

        assertThat(shareAccessLogRepository.findAll())
                .singleElement()
                .satisfies(log -> {
                    assertThat(log.isAllowed()).isTrue();
                    assertThat(log.getShareId()).isEqualTo(share.getId());
                    assertThat(log.getResourceType()).isEqualTo("EXPENSE");
                    assertThat(log.getStatusCode()).isEqualTo(200);
                });
    }

    @Test
    void getSharedExpense_revokedShare_returns404() throws Exception {
        User user = createTestUser("alice", "pass");
        Expense expense = createTestExpense(user.getId(), "Food", BigDecimal.TEN, "USD");

        ExpenseShare share = new ExpenseShare();
        share.setExpenseId(expense.getId());
        share.setShareToken(UUID.randomUUID().toString());
        share.setCreatedAt(LocalDateTime.now().minusDays(2));
        share.setExpiresAt(LocalDateTime.now().plusDays(7));
        share.setRevokedAt(LocalDateTime.now().minusHours(1));
        share.setCreatedBy(user.getId());
        expenseShareRepository.save(share);

        mockMvc.perform(get("/api/share/{shareToken}/expense", share.getShareToken()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Share link is expired, revoked, or invalid"));
    }

    @Test
    void getSharedReceipt_matchingReceipt_returnsContent() throws Exception {
        User user = createTestUser("alice", "pass");
        Expense expense = createTestExpense(user.getId(), "Food", BigDecimal.TEN, "USD");
        expense.setImagePath("receipts/" + user.getId() + "/2026_05/shared-receipt.txt");
        expenseRepository.save(expense);

        ExpenseShare share = new ExpenseShare();
        share.setExpenseId(expense.getId());
        share.setShareToken(UUID.randomUUID().toString());
        share.setCreatedAt(LocalDateTime.now().minusDays(1));
        share.setExpiresAt(LocalDateTime.now().plusDays(7));
        share.setCreatedBy(user.getId());
        expenseShareRepository.save(share);

        Path receiptDir = Path.of("target/test-data/receipts", String.valueOf(user.getId()), "2026_05");
        Files.createDirectories(receiptDir);
        Files.writeString(receiptDir.resolve("shared-receipt.txt"), "shared receipt content");

        mockMvc.perform(get("/api/share/{shareToken}/receipts/{filename}", share.getShareToken(), "shared-receipt.txt"))
                .andExpect(status().isOk())
                .andExpect(content().string("shared receipt content"));
    }

    @Test
    void getSharedReceipt_nonMatchingReceipt_returns404() throws Exception {
        User user = createTestUser("alice", "pass");
        Expense expense = createTestExpense(user.getId(), "Food", BigDecimal.TEN, "USD");

        ExpenseShare share = new ExpenseShare();
        share.setExpenseId(expense.getId());
        share.setShareToken(UUID.randomUUID().toString());
        share.setCreatedAt(LocalDateTime.now().minusDays(1));
        share.setExpiresAt(LocalDateTime.now().plusDays(7));
        share.setCreatedBy(user.getId());
        expenseShareRepository.save(share);

        mockMvc.perform(get("/api/share/{shareToken}/receipts/{filename}", share.getShareToken(), "missing.txt"))
                .andExpect(status().isNotFound());
    }

    @Test
    void getSharedExpense_rateLimitExceeded_returns429() throws Exception {
        User user = createTestUser("alice", "pass");
        Expense expense = createTestExpense(user.getId(), "Food", BigDecimal.TEN, "USD");

        ExpenseShare share = new ExpenseShare();
        share.setExpenseId(expense.getId());
        share.setShareToken(UUID.randomUUID().toString());
        share.setCreatedAt(LocalDateTime.now().minusDays(1));
        share.setExpiresAt(LocalDateTime.now().plusDays(7));
        share.setCreatedBy(user.getId());
        expenseShareRepository.save(share);

        String ip = "203.0.113.10";
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(get("/api/share/{shareToken}/expense", share.getShareToken())
                            .header("X-Forwarded-For", ip))
                    .andExpect(status().isOk());
        }

        mockMvc.perform(get("/api/share/{shareToken}/expense", share.getShareToken())
                        .header("X-Forwarded-For", ip))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error").value("Too many share-link requests. Please try again shortly."));
    }
}

