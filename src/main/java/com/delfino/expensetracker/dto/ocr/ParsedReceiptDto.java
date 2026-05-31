package com.delfino.expensetracker.dto.ocr;

import java.math.BigDecimal;
import java.util.List;

/**
 * Structured data extracted by the OCR/LLM pipeline from a scanned receipt.
 * {@code transactionDatetime} is kept as a {@code String} so that a malformed
 * value from the model does not abort the entire parse; the service layer
 * handles the conversion with a try/catch.
 */
public record ParsedReceiptDto(
        String transactionDatetime,
        BigDecimal amount,
        String currency,
        String receiptNumber,
        String category,
        List<ParsedItemDto> items,
        ParsedStoreDto store) {
}
