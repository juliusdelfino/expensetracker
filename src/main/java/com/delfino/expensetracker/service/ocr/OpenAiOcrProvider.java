package com.delfino.expensetracker.service.ocr;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.*;

/**
 * OcrProvider for OpenAI /chat/completions format.
 */
public class OpenAiOcrProvider implements OcrProvider {

    private final ObjectMapper objectMapper;

    public OpenAiOcrProvider(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public Map<String, Object> buildVisionRequestBody(String model, String prompt,
                                                       List<byte[]> imageBytesList, String mediaType,
                                                       Map<String, Object> toolDef, boolean useTools, boolean disableThinking) {
        List<Object> contentParts = new ArrayList<>();
        contentParts.add(Map.of("type", "text", "text", prompt));
        for (byte[] b : imageBytesList) {
            String dataUrl = "data:" + mediaType + ";base64," + Base64.getEncoder().encodeToString(b);
            contentParts.add(Map.of(
                    "type", "image_url",
                    "image_url", Map.of("url", dataUrl)
            ));
        }
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("role", "user");
        message.put("content", contentParts);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("messages", List.of(message));
        if (disableThinking) {
            body.put("enable_thinking", false);
        }
        if (useTools) {
            body.put("tools", List.of(toolDef));
            body.put("tool_choice", Map.of("type", "function",
                    "function", Map.of("name", "submit_receipt")));
        }
        return body;
    }

    @Override
    public Map<String, Object> buildTextRequestBody(String model, String prompt, String pdfText,
                                                     Map<String, Object> toolDef, boolean useTools, boolean disableThinking) {
        String truncated = pdfText.length() > 12_000 ? pdfText.substring(0, 12_000) + "\n[truncated]" : pdfText;
        String fullPrompt = prompt + "\n\nReceipt text:\n" + truncated;

        Map<String, Object> message = new LinkedHashMap<>();
        message.put("role", "user");
        message.put("content", fullPrompt);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("messages", List.of(message));
        if (disableThinking) {
            body.put("enable_thinking", false);
        }
        if (useTools) {
            body.put("tools", List.of(toolDef));
            body.put("tool_choice", Map.of("type", "function",
                    "function", Map.of("name", "submit_receipt")));
        }
        return body;
    }

    @Override
    public JsonNode extractAssistantMessage(JsonNode responseRoot) {
        return responseRoot.path("choices").path(0).path("message");
    }

    @Override
    public JsonNode extractToolCallArgs(JsonNode assistantMsg) throws JsonProcessingException {
        JsonNode toolCalls = assistantMsg.path("tool_calls");
        if (toolCalls.isMissingNode() || !toolCalls.isArray() || toolCalls.isEmpty()) return null;
        JsonNode argsNode = toolCalls.get(0).path("function").path("arguments");
        if (argsNode.isMissingNode()) return null;
        // OpenAI returns arguments as a JSON string
        if (argsNode.isTextual()) {
            return objectMapper.readTree(argsNode.asText());
        }
        return argsNode;
    }

    @Override
    public String extractToolCallId(JsonNode assistantMsg) {
        JsonNode toolCalls = assistantMsg.path("tool_calls");
        if (toolCalls.isMissingNode() || !toolCalls.isArray() || toolCalls.isEmpty()) return "tool_0";
        return toolCalls.get(0).path("id").asText("tool_0");
    }

    @Override
    public boolean requiresToolCallId() {
        return true;
    }
}

