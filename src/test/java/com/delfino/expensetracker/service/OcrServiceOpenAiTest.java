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
 * Integration tests for OcrService with OpenAI format responses.
 * Uses a user-level AI model override so provider selection happens through the Phase 2 runtime resolver.
 */
class OcrServiceOpenAiTest extends BaseControllerTest {

    @Autowired
    private OcrService ocrService;

    private static final String RECEIPT_ARGS =
            "{\"transactionDatetime\":\"2026-04-01T12:00:00\"," +
            "\"amount\":12.50,\"currency\":\"USD\",\"category\":\"Food\"," +
            "\"receiptNumber\":\"001\"," +
            "\"items\":[{\"itemName\":\"Test Item\",\"quantity\":1,\"unitPrice\":12.50,\"adjustment\":0}]," +
            "\"store\":{\"name\":\"TestShop\",\"city\":\"Singapore\",\"country\":\"SG\"}}";

    private Path createTestImage() throws IOException {
        byte[] minimalJpeg = new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xD9};
        Path dir = Path.of("target/test-data/receipts");
        Files.createDirectories(dir);
        Path imgPath = dir.resolve("ocr-openai-test-" + System.nanoTime() + ".jpg");
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
    void processReceiptSync_openAiToolCall_savesExpenseAndItems() throws Exception {
        User user = createTestUser("openaiocr1", "pass", "USD", com.delfino.expensetracker.model.UserRole.USER, "qwen3.5-397b-a17b");
        Expense expense = createProcessingExpense(user.getId());
        Path imgPath = createTestImage();

        // Stub OpenAI-format tool_call response
        WIRE_MOCK.stubFor(WireMock.post(urlPathMatching("/(v1/)?chat/completions"))
                .withRequestBody(containing("submit_receipt"))
                .atPriority(0)
                .willReturn(okJson(openAiToolCallResponse("submit_receipt", RECEIPT_ARGS))
                        .withHeader("Connection", "close")));

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
    void processReceiptSync_openAiApiError_marksExpenseFailed() throws Exception {
        User user = createTestUser("openaiocr2", "pass", "USD", com.delfino.expensetracker.model.UserRole.USER, "qwen3.5-397b-a17b");
        Expense expense = createProcessingExpense(user.getId());
        Path imgPath = createTestImage();

        WIRE_MOCK.stubFor(WireMock.post(urlPathMatching("/(v1/)?chat/completions"))
                .withRequestBody(containing("submit_receipt"))
                .atPriority(0)
                .willReturn(aResponse().withStatus(400)
                        .withBody("{\"error\":{\"message\":\"test error\"}}")
                        .withHeader("Connection", "close")));

        ocrService.processReceiptSync(expense.getId(), imgPath.toString());

        Expense updated = expenseRepository.findById(expense.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(ExpenseStatus.FAILED);
    }

    @Test
    void processReceiptSync_openAiValidationRetry_correctsAmount() throws Exception {
        User user = createTestUser("openaiocr3", "pass", "USD", com.delfino.expensetracker.model.UserRole.USER, "qwen3.5-397b-a17b");
        Expense expense = createProcessingExpense(user.getId());
        Path imgPath = createTestImage();

        String badArgs = "{\"transactionDatetime\":\"2026-04-01T12:00:00\"," +
                "\"amount\":99.99,\"currency\":\"USD\",\"category\":\"Food\"," +
                "\"items\":[{\"itemName\":\"Item\",\"quantity\":1,\"unitPrice\":12.50,\"adjustment\":0}]," +
                "\"store\":{\"name\":\"Shop\"}}";

        // First call: bad amount
        WIRE_MOCK.stubFor(WireMock.post(urlPathMatching("/(v1/)?chat/completions"))
                .withRequestBody(containing("submit_receipt"))
                .withRequestBody(WireMock.notMatching(".*Validation failed.*"))
                .atPriority(0)
                .willReturn(okJson(openAiToolCallResponse("submit_receipt", badArgs))
                        .withHeader("Connection", "close")));

        // Second call (retry with validation error): corrected amount
        WIRE_MOCK.stubFor(WireMock.post(urlPathMatching("/(v1/)?chat/completions"))
                .withRequestBody(containing("Validation failed"))
                .atPriority(0)
                .willReturn(okJson(openAiToolCallResponse("submit_receipt", RECEIPT_ARGS))
                        .withHeader("Connection", "close")));

        ocrService.processReceiptSync(expense.getId(), imgPath.toString());

        Expense updated = expenseRepository.findById(expense.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(ExpenseStatus.COMPLETED);
        assertThat(updated.getAmount()).isEqualByComparingTo("12.50");
    }
}

