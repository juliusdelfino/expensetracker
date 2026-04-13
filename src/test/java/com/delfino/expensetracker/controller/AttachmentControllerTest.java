package com.delfino.expensetracker.controller;

import com.delfino.expensetracker.BaseControllerTest;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AttachmentControllerTest extends BaseControllerTest {

    @Test
    void getReceipt_fileNotFound_returns404() throws Exception {
        mockMvc.perform(get("/api/attachments/receipts/nonexistent.jpg"))
                .andExpect(status().isNotFound());
    }

    @Test
    void getAttachment_fileNotFound_returns404() throws Exception {
        mockMvc.perform(get("/api/attachments/some-expense-id/nonexistent.pdf"))
                .andExpect(status().isNotFound());
    }

    @Test
    void getReceipt_existingFile_returnsContent() throws Exception {
        // Create test file in the test data directory
        Path receiptDir = Path.of("target/test-data/receipts");
        Files.createDirectories(receiptDir);
        Path testFile = receiptDir.resolve("test-receipt.txt");
        Files.writeString(testFile, "test receipt content");

        try {
            mockMvc.perform(get("/api/attachments/receipts/test-receipt.txt"))
                    .andExpect(status().isOk())
                    .andExpect(content().string("test receipt content"));
        } finally {
            Files.deleteIfExists(testFile);
        }
    }

    @Test
    void getAttachment_existingFile_returnsContent() throws Exception {
        Path attachDir = Path.of("target/test-data/attachments/test-expense-id");
        Files.createDirectories(attachDir);
        Path testFile = attachDir.resolve("test.txt");
        Files.writeString(testFile, "attachment content");

        try {
            mockMvc.perform(get("/api/attachments/test-expense-id/test.txt"))
                    .andExpect(status().isOk())
                    .andExpect(content().string("attachment content"));
        } finally {
            Files.deleteIfExists(testFile);
        }
    }

    @Test
    void getReceipt_noAuthRequired() throws Exception {
        // Attachment endpoints are public (for sharing receipts)
        mockMvc.perform(get("/api/attachments/receipts/anything.jpg"))
                .andExpect(status().isNotFound()); // 404 (not 401) confirms no auth required
    }
}


