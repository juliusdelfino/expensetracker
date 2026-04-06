package com.delfino.expensetracker.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Binds chatbot configuration from application.yml.
 * Supports API key management for any LLM provider (Ollama, OpenAI, Anthropic, etc).
 */
@Component
@ConfigurationProperties(prefix = "chatbot.api")
public class ChatBotProperties {

    private String apiKey;
    private String systemPrompt;

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getSystemPrompt() {
        return systemPrompt;
    }

    public void setSystemPrompt(String systemPrompt) {
        this.systemPrompt = systemPrompt;
    }
}

