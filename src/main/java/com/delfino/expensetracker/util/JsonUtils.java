package com.delfino.expensetracker.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Utility class for JSON extraction and cleanup.
 */
public final class JsonUtils {

    private JsonUtils() {}

    /**
     * Strip markdown code fences from a string if present.
     */
    public static String stripMarkdownFences(String text) {
        if (text == null) return null;
        text = text.strip();
        if (text.startsWith("```")) {
            text = text.replaceAll("^```[a-zA-Z]*\\n?", "").replaceAll("```$", "").strip();
        }
        return text;
    }

    /**
     * Attempts to parse the assistant's content field as a receipt-like JSON object.
     * Returns the parsed JsonNode if it contains amount+items, or null otherwise.
     */
    public static JsonNode extractJsonFromContent(JsonNode assistantMsg, ObjectMapper objectMapper) {
        String content = assistantMsg.path("content").asText(null);
        if (content == null || content.isBlank()) return null;
        content = stripMarkdownFences(content);
        try {
            JsonNode node = objectMapper.readTree(content);
            if (node.isObject() && node.has("amount") && node.has("items")) return node;
        } catch (Exception ignored) {}
        return null;
    }
}

