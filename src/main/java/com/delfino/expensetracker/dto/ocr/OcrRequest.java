package com.delfino.expensetracker.dto.ocr;

import java.util.Map;

public record OcrRequest (byte[] fileBytes, String mediaType, Map<String, Object> requestBody) {
}
