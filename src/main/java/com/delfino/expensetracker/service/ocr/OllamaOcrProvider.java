package com.delfino.expensetracker.service.ocr;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.*;

/**
 * OcrProvider for Ollama /api/chat format.
 */
public class OllamaOcrProvider implements OcrProvider {

    private final ObjectMapper objectMapper;

    public OllamaOcrProvider(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public Map<String, Object> buildVisionRequestBody(String model, String prompt,
                                                       List<byte[]> imageBytesList, String mediaType,
                                                       Map<String, Object> toolDef, boolean useTools, boolean disableThinking) {
        List<String> base64Images = new ArrayList<>();
        for (byte[] b : imageBytesList) {
            base64Images.add(Base64.getEncoder().encodeToString(b));
        }
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("role", "user");
        message.put("content", prompt);
        message.put("images", base64Images);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("messages", List.of(message));
        if (useTools) {
            body.put("tools", List.of(toolDef));
        }
        body.put("stream", false);
        if (disableThinking) {
            body.put("think", false);
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
        if (useTools) {
            body.put("tools", List.of(toolDef));
        }
        body.put("stream", false);
        if (disableThinking) {
            body.put("think", false);
        }
        return body;
    }

    @Override
    public JsonNode extractAssistantMessage(JsonNode responseRoot) {
        return responseRoot.path("message");
    }

    @Override
    public JsonNode extractToolCallArgs(JsonNode assistantMsg) throws JsonProcessingException {
        JsonNode toolCalls = assistantMsg.path("tool_calls");
        if (toolCalls.isMissingNode() || !toolCalls.isArray() || toolCalls.isEmpty()) return null;
        JsonNode argsNode = toolCalls.get(0).path("function").path("arguments");
        if (argsNode.isMissingNode()) return null;
        // Ollama returns arguments as a JSON object (not a string)
        if (argsNode.isTextual()) {
            return objectMapper.readTree(argsNode.asText());
        }
        return argsNode;
    }

    @Override
    public String extractToolCallId(JsonNode assistantMsg) {
        return "tool_0"; // Ollama doesn't provide tool_call IDs
    }

    @Override
    public boolean requiresToolCallId() {
        return false;
    }
}

