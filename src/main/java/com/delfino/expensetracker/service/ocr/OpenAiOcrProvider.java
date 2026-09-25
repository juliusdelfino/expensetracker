package com.delfino.expensetracker.service.ocr;

import com.delfino.expensetracker.dto.ocr.ParsedReceiptDto;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * OcrProvider for OpenAI /chat/completions format.
 */
public class OpenAiOcrProvider implements OcrProvider {

    private static final String TYPE_FUNCTION = "function";
    private static final String SUBMIT_RECEIPT = "submit_receipt";

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
        for (byte[] bytes : imageBytesList) {
            String dataUrl = "data:" + mediaType + ";base64," + Base64.getEncoder().encodeToString(bytes);
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
        if (useTools) {
            body.put("tools", List.of(toolDef));
            if (disableThinking) {
                body.put("enable_thinking", false);
                body.put("tool_choice", Map.of(
                        "type", TYPE_FUNCTION,
                        "function", Map.of("name", SUBMIT_RECEIPT)
                ));
            }
        } else if (disableThinking) {
            body.put("enable_thinking", false);
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
            if (disableThinking) {
                body.put("enable_thinking", false);
                body.put("tool_choice", Map.of(
                        "type", TYPE_FUNCTION,
                        "function", Map.of("name", SUBMIT_RECEIPT)
                ));
            }
        } else if (disableThinking) {
            body.put("enable_thinking", false);
        }
        return body;
    }

    @Override
    public JsonNode extractAssistantMessage(JsonNode responseRoot) {
        return responseRoot.path("choices").path(0).path("message");
    }

    @Override
    public ParsedReceiptDto extractToolCallArgs(JsonNode assistantMsg) throws JsonProcessingException {
        JsonNode toolCalls = assistantMsg.path("tool_calls");
        if (toolCalls.isMissingNode() || !toolCalls.isArray() || toolCalls.isEmpty()) return null;

        JsonNode argsNode = toolCalls.get(0).path("function").path("arguments");
        if (argsNode.isMissingNode()) return null;

        // OpenAI returns arguments as a JSON string
        if (argsNode.isTextual()) {
            return objectMapper.readValue(argsNode.asText(), ParsedReceiptDto.class);
        }
        return objectMapper.treeToValue(argsNode, ParsedReceiptDto.class);
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
