package com.delfino.expensetracker.controller;

import com.delfino.expensetracker.BaseControllerTest;
import com.delfino.expensetracker.model.Expense;
import com.delfino.expensetracker.model.User;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AttachmentControllerTest extends BaseControllerTest {

    @Test
    void getReceipt_fileNotFound_returns404() throws Exception {
        User user = createTestUser("alice", "pass");
        MockHttpSession session = loginAs("alice", "pass");

        mockMvc.perform(get("/api/attachments/receipts/{userId}/{yearMonth}/{filename}", user.getId(), "2026_05", "nonexistent.jpg")
                        .session(session))
                .andExpect(status().isNotFound());
    }

    @Test
    void getAttachment_fileNotFound_returns404() throws Exception {
        User user = createTestUser("alice", "pass");
        Expense expense = createTestExpense(user.getId(), "Food", java.math.BigDecimal.TEN, "USD");
        MockHttpSession session = loginAs("alice", "pass");

        mockMvc.perform(get("/api/attachments/{expenseUrlId}/{filename}", expense.getUrlId(), "nonexistent.pdf")
                        .session(session))
                .andExpect(status().isNotFound());
    }

    @Test
    void getReceipt_existingFile_returnsContent() throws Exception {
        User user = createTestUser("alice", "pass");
        MockHttpSession session = loginAs("alice", "pass");
        String storedPath = "receipts/" + user.getId() + "/2026_05/test-receipt.txt";
        Expense expense = createTestExpense(user.getId(), "Food", java.math.BigDecimal.TEN, "USD");
        expense.setImagePath(storedPath);
        expenseRepository.save(expense);

        Path receiptDir = Path.of("target/test-data/receipts", String.valueOf(user.getId()), "2026_05");
        Files.createDirectories(receiptDir);
        Path testFile = receiptDir.resolve("test-receipt.txt");
        Files.writeString(testFile, "test receipt content");

        try {
            mockMvc.perform(get("/api/attachments/receipts/{userId}/{yearMonth}/{filename}", user.getId(), "2026_05", "test-receipt.txt")
                            .session(session))
                    .andExpect(status().isOk())
                    .andExpect(content().string("test receipt content"));
        } finally {
            Files.deleteIfExists(testFile);
        }
    }

    @Test
    void getAttachment_existingFile_returnsContent() throws Exception {
        User user = createTestUser("alice", "pass");
        Expense expense = createTestExpense(user.getId(), "Food", java.math.BigDecimal.TEN, "USD");
        MockHttpSession session = loginAs("alice", "pass");

        Path attachDir = Path.of("target/test-data/attachments", expense.getUrlId());
        Files.createDirectories(attachDir);
        Path testFile = attachDir.resolve("test.txt");
        Files.writeString(testFile, "attachment content");
        expense.setAttachments(List.of(testFile.toString()));
        expenseRepository.save(expense);

        try {
            mockMvc.perform(get("/api/attachments/{expenseUrlId}/{filename}", expense.getUrlId(), "test.txt")
                            .session(session))
                    .andExpect(status().isOk())
                    .andExpect(content().string("attachment content"));
        } finally {
            Files.deleteIfExists(testFile);
        }
    }

    @Test
    void getReceipt_requiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/attachments/receipts/1/2026_05/anything.jpg"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getAttachment_requiresAuthentication() throws Exception {
        User user = createTestUser("alice", "pass");
        Expense expense = createTestExpense(user.getId(), "Food", java.math.BigDecimal.TEN, "USD");

        mockMvc.perform(get("/api/attachments/{expenseUrlId}/{filename}", expense.getUrlId(), "anything.txt"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getReceipt_otherUsersReceipt_returns403() throws Exception {
        User owner = createTestUser("alice", "pass");
        User other = createTestUser("bob", "pass");
        MockHttpSession otherSession = loginAs("bob", "pass");
        Expense expense = createTestExpense(owner.getId(), "Food", java.math.BigDecimal.TEN, "USD");
        expense.setImagePath("receipts/" + owner.getId() + "/2026_05/test-receipt.txt");
        expenseRepository.save(expense);

        Path receiptDir = Path.of("target/test-data/receipts", String.valueOf(owner.getId()), "2026_05");
        Files.createDirectories(receiptDir);
        Files.writeString(receiptDir.resolve("test-receipt.txt"), "owner receipt content");

        mockMvc.perform(get("/api/attachments/receipts/{userId}/{yearMonth}/{filename}", owner.getId(), "2026_05", "test-receipt.txt")
                        .session(otherSession))
                .andExpect(status().isForbidden());

        assertThat(other.getId()).isNotEqualTo(owner.getId());
    }

    @Test
    void getSharedReceipt_viaPublicShareEndpoint_returnsContent() throws Exception {
        User user = createTestUser("alice", "pass");
        Expense expense = createTestExpense(user.getId(), "Food", java.math.BigDecimal.TEN, "USD");
        expense.setImagePath("receipts/" + user.getId() + "/2026_05/shared.txt");
        expenseRepository.save(expense);

        com.delfino.expensetracker.model.ExpenseShare share = new com.delfino.expensetracker.model.ExpenseShare();
        share.setExpenseId(expense.getId());
        share.setShareToken(java.util.UUID.randomUUID().toString());
        share.setCreatedAt(java.time.LocalDateTime.now().minusHours(1));
        share.setExpiresAt(java.time.LocalDateTime.now().plusDays(7));
        share.setCreatedBy(user.getId());
        expenseShareRepository.save(share);

        Path receiptDir = Path.of("target/test-data/receipts", String.valueOf(user.getId()), "2026_05");
        Files.createDirectories(receiptDir);
        Files.writeString(receiptDir.resolve("shared.txt"), "shared receipt content");

        mockMvc.perform(get("/api/share/{shareToken}/receipts/{filename}", share.getShareToken(), "shared.txt"))
                .andExpect(status().isOk())
                .andExpect(content().string("shared receipt content"));
    }

    @Test
    void getAttachment_otherUsersAttachment_returns403() throws Exception {
        User owner = createTestUser("alice", "pass");
        createTestUser("bob", "pass");
        MockHttpSession otherSession = loginAs("bob", "pass");
        Expense expense = createTestExpense(owner.getId(), "Food", java.math.BigDecimal.TEN, "USD");

        Path attachDir = Path.of("target/test-data/attachments", expense.getUrlId());
        Files.createDirectories(attachDir);
        Path testFile = attachDir.resolve("owner.txt");
        Files.writeString(testFile, "owner attachment content");
        expense.setAttachments(List.of(testFile.toString()));
        expenseRepository.save(expense);

        mockMvc.perform(get("/api/attachments/{expenseUrlId}/{filename}", expense.getUrlId(), "owner.txt")
                        .session(otherSession))
                .andExpect(status().isForbidden());
    }
}


