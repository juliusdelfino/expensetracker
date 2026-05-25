package com.delfino.expensetracker.config;

import com.delfino.expensetracker.service.ocr.OcrProvider;
import com.delfino.expensetracker.service.ocr.OllamaOcrProvider;
import com.delfino.expensetracker.service.ocr.OpenAiOcrProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.util.Map;

@Configuration
public class OcrConfig {

    @Bean
    @SuppressWarnings("unchecked")
    public Map<String, Object> ocrToolSchema(
            @Value("classpath:ocr-tool-schema.json") Resource toolSchemaResource,
            ObjectMapper objectMapper) throws IOException {
        return objectMapper.readValue(toolSchemaResource.getInputStream(), Map.class);
    }

    @Bean
    public OcrProvider ocrProvider(@Value("${ocr.api.format:ollama}") String format,
                                   ObjectMapper objectMapper) {
        if ("openai".equalsIgnoreCase(format)) {
            return new OpenAiOcrProvider(objectMapper);
        }
        return new OllamaOcrProvider(objectMapper);
    }
}

