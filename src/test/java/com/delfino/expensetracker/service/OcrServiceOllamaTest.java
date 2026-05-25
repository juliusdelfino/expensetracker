package com.delfino.expensetracker.service;

import com.delfino.expensetracker.BaseControllerTest;
import com.delfino.expensetracker.model.Expense;
import com.delfino.expensetracker.model.ExpenseItem;
import com.delfino.expensetracker.model.ExpenseStatus;
import com.delfino.expensetracker.model.ExpenseType;
import com.delfino.expensetracker.model.User;
import com.github.tomakehurst.wiremock.client.WireMock;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for OcrService receipt processing.
 * Uses the default test profile (ollama format) from BaseControllerTest.
 * Tests both Ollama tool_call responses and content-only (fallback) responses.
 */
class OcrServiceOllamaTest extends BaseControllerTest {

    @Autowired
    private OcrService ocrService;

    private static final String RECEIPT_ARGS =
            "{\"transactionDatetime\":\"2026-04-01T12:00:00\"," +
            "\"amount\":12.50,\"currency\":\"USD\",\"category\":\"Food\"," +
            "\"receiptNumber\":\"001\"," +
            "\"items\":[{\"itemName\":\"Test Item\",\"quantity\":1,\"unitPrice\":12.50,\"adjustment\":0}]," +
            "\"store\":{\"name\":\"TestShop\",\"city\":\"Singapore\",\"country\":\"SG\"}}";

    private Path createTestImage() throws IOException {
        // Minimal valid JPEG (SOI + EOI markers)
        byte[] minimalJpeg = new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xD9};
        Path dir = Path.of("target/test-data/receipts");
        Files.createDirectories(dir);
        Path imgPath = dir.resolve("ocr-test-" + System.nanoTime() + ".jpg");
        Files.write(imgPath, minimalJpeg);
        return imgPath;
    }

    private Expense createProcessingExpense(long userId) {
        Expense expense = new Expense();
        expense.setUserId(userId);
        expense.setType(ExpenseType.RECEIPT_SCAN);
        expense.setStatus(ExpenseStatus.PROCESSING);
        expense.setCreatedAt(LocalDateTime.now());
        expense.setUpdatedAt(LocalDateTime.now());
        return expenseRepository.save(expense);
    }

    @Test
    void processReceiptSync_ollamaToolCall_savesExpenseAndItems() throws Exception {
        User user = createTestUser("ocruser1", "pass");
        Expense expense = createProcessingExpense(user.getId());
        Path imgPath = createTestImage();

        // Stub is already set up in BaseControllerTest (Ollama tool_call format)
        ocrService.processReceiptSync(expense.getId(), imgPath.toString());

        Expense updated = expenseRepository.findById(expense.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(ExpenseStatus.COMPLETED);
        assertThat(updated.getAmount()).isEqualByComparingTo("12.50");
        assertThat(updated.getCurrency()).isEqualTo("USD");
        assertThat(updated.getCategory()).isEqualTo("Food");
        assertThat(updated.getReceiptNumber()).isEqualTo("001");

        List<ExpenseItem> items = expenseItemRepository.findByExpenseIdAndDeletedFalse(expense.getId());
        assertThat(items).hasSize(1);
        assertThat(items.get(0).getItemName()).isEqualTo("Test Item");
        assertThat(items.get(0).getUnitPrice()).isEqualByComparingTo("12.50");
    }

    @Test
    void processReceiptSync_ollamaContentFallback_savesExpense() throws Exception {
        User user = createTestUser("ocruser2", "pass");
        Expense expense = createProcessingExpense(user.getId());
        Path imgPath = createTestImage();

        // Override the OCR stub to return content-only (no tool_calls) — simulates Gemma4 behavior
        WIRE_MOCK.stubFor(WireMock.post(urlPathEqualTo("/api/chat"))
                .withRequestBody(containing("submit_receipt"))
                .atPriority(0) // highest priority to override the default
                .willReturn(okJson(ollamaChatResponse(RECEIPT_ARGS))
                        .withHeader("Connection", "close")));

        ocrService.processReceiptSync(expense.getId(), imgPath.toString());

        Expense updated = expenseRepository.findById(expense.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(ExpenseStatus.COMPLETED);
        assertThat(updated.getAmount()).isEqualByComparingTo("12.50");
        assertThat(updated.getCurrency()).isEqualTo("USD");

        List<ExpenseItem> items = expenseItemRepository.findByExpenseIdAndDeletedFalse(expense.getId());
        assertThat(items).hasSize(1);
    }

    @Test
    void processReceiptSync_ollamaApiError_marksExpenseFailed() throws Exception {
        User user = createTestUser("ocruser3", "pass");
        Expense expense = createProcessingExpense(user.getId());
        Path imgPath = createTestImage();

        // Override stub to return 500
        WIRE_MOCK.stubFor(WireMock.post(urlPathEqualTo("/api/chat"))
                .withRequestBody(containing("submit_receipt"))
                .atPriority(0)
                .willReturn(aResponse().withStatus(500).withBody("{\"error\":\"test error\"}")
                        .withHeader("Connection", "close")));

        ocrService.processReceiptSync(expense.getId(), imgPath.toString());

        Expense updated = expenseRepository.findById(expense.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(ExpenseStatus.FAILED);
    }

    @Test
    void processReceiptSync_invalidAmountInContent_retries() throws Exception {
        User user = createTestUser("ocruser4", "pass");
        Expense expense = createProcessingExpense(user.getId());
        Path imgPath = createTestImage();

        // First response: wrong amount (triggers validation failure)
        String badArgs = "{\"transactionDatetime\":\"2026-04-01T12:00:00\"," +
                "\"amount\":99.99,\"currency\":\"USD\",\"category\":\"Food\"," +
                "\"items\":[{\"itemName\":\"Item\",\"quantity\":1,\"unitPrice\":12.50,\"adjustment\":0}]," +
                "\"store\":{\"name\":\"Shop\"}}";
        String goodArgs = RECEIPT_ARGS;

        // First call returns bad amount in tool_call, second call returns corrected
        WIRE_MOCK.stubFor(WireMock.post(urlPathEqualTo("/api/chat"))
                .withRequestBody(containing("submit_receipt"))
                .withRequestBody(WireMock.notMatching(".*Validation failed.*"))
                .atPriority(0)
                .willReturn(okJson(ollamaToolCallResponse("submit_receipt", badArgs))
                        .withHeader("Connection", "close")));

        WIRE_MOCK.stubFor(WireMock.post(urlPathEqualTo("/api/chat"))
                .withRequestBody(containing("Validation failed"))
                .atPriority(0)
                .willReturn(okJson(ollamaToolCallResponse("submit_receipt", goodArgs))
                        .withHeader("Connection", "close")));

        ocrService.processReceiptSync(expense.getId(), imgPath.toString());

        Expense updated = expenseRepository.findById(expense.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(ExpenseStatus.COMPLETED);
        assertThat(updated.getAmount()).isEqualByComparingTo("12.50");
    }
}

