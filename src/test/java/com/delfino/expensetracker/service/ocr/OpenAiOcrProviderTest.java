package com.delfino.expensetracker.service.ocr;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class OpenAiOcrProviderTest {

    private final OpenAiOcrProvider provider = new OpenAiOcrProvider(new ObjectMapper());

    @Test
    void buildTextRequestBody_thinkingEnabled_doesNotForceToolChoiceObject() {
        Map<String, Object> body = provider.buildTextRequestBody(
                "qwen3.5-397b-a17b",
                "Parse receipt",
                "Total: 12.50 USD",
                Map.of("type", "function"),
                true,
                false
        );

        assertThat(body)
                .containsKey("tools")
                .doesNotContainKey("tool_choice");
    }

    @Test
    void buildTextRequestBody_thinkingDisabled_forcesSubmitReceiptToolChoice() {
        Map<String, Object> body = provider.buildTextRequestBody(
                "qwen3.5-397b-a17b",
                "Parse receipt",
                "Total: 12.50 USD",
                Map.of("type", "function"),
                true,
                true
        );

        assertThat(body).containsEntry("tool_choice", Map.of(
                "type", "function",
                "function", Map.of("name", "submit_receipt")
        ));
    }

    @Test
    void buildVisionRequestBody_thinkingEnabled_doesNotForceToolChoiceObject() {
        Map<String, Object> body = provider.buildVisionRequestBody(
                "qwen3.5-397b-a17b",
                "Parse receipt",
                List.of("img".getBytes(StandardCharsets.UTF_8)),
                "image/jpeg",
                Map.of("type", "function"),
                true,
                false
        );

        assertThat(body)
                .containsKey("tools")
                .doesNotContainKey("tool_choice");
    }
}
