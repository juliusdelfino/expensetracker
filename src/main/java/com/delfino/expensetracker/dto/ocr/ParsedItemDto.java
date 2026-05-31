package com.delfino.expensetracker.dto.ocr;
import java.math.BigDecimal;
/**
 * A single line item extracted from a scanned receipt.
 */
public record ParsedItemDto(
        String itemName,
        BigDecimal quantity,
        BigDecimal unitPrice,
        BigDecimal adjustment) {
}
