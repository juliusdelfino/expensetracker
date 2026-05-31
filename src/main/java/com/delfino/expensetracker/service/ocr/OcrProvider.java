package com.delfino.expensetracker.service.ocr;

import com.delfino.expensetracker.dto.ocr.ParsedReceiptDto;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;
import java.util.Map;

/**
 * Strategy interface for OCR API format differences (OpenAI vs Ollama).
 */
public interface OcrProvider {

    /**
     * Build a vision request body (with base64 images).
     *
     * @param useTools       whether to include the tool definition and tool_choice in the request
     * @param disableThinking whether to suppress chain-of-thought reasoning (model-specific flag)
     */
    Map<String, Object> buildVisionRequestBody(String model, String prompt,
                                                List<byte[]> imageBytesList, String mediaType,
                                                Map<String, Object> toolDef, boolean useTools, boolean disableThinking);

    /**
     * Build a text-only request body (for PDFs with extractable text).
     *
     * @param useTools       whether to include the tool definition and tool_choice in the request
     * @param disableThinking whether to suppress chain-of-thought reasoning (model-specific flag)
     */
    Map<String, Object> buildTextRequestBody(String model, String prompt, String pdfText,
                                              Map<String, Object> toolDef, boolean useTools, boolean disableThinking);

    /**
     * Extract the assistant message node from the API response.
     * Kept as {@link JsonNode} because the same node is passed back verbatim
     * into the conversation history via {@code objectMapper.convertValue(assistantMsg, Map.class)}
     * during the retry loop — converting to a DTO and re-serialising would risk
     * dropping unknown vendor-specific fields.
     */
    JsonNode extractAssistantMessage(JsonNode responseRoot);

    /**
     * Parse the tool-call arguments from the assistant message and deserialise
     * them directly into a {@link ParsedReceiptDto}. Returns {@code null} if the
     * assistant message contains no tool call.
     */
    ParsedReceiptDto extractToolCallArgs(JsonNode assistantMsg) throws JsonProcessingException;

    /**
     * Extract tool_call ID (OpenAI has one; Ollama returns a default).
     */
    String extractToolCallId(JsonNode assistantMsg);

    /**
     * Whether OpenAI-style tool_call_id should be included in tool result messages.
     */
    boolean requiresToolCallId();
}

