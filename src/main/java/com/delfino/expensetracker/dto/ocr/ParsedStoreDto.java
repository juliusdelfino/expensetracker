package com.delfino.expensetracker.dto.ocr;
/**
 * Store information extracted from a scanned receipt.
 */
public record ParsedStoreDto(
        String name,
        String address,
        String city,
        String country,
        String postalCode,
        String phoneNumber,
        String website) {
}
