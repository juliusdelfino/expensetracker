package com.delfino.expensetracker.config;

import com.delfino.expensetracker.service.mcp.ExpenseCrudToolService;
import com.delfino.expensetracker.service.mcp.ExpenseToolService;
import com.delfino.expensetracker.service.mcp.ProfileToolService;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Registers @Tool-annotated beans with Spring AI so that:
 * 1) The ChatClient can invoke them during tool-calling conversations
 * 2) The MCP server exposes them for external MCP clients (e.g. Ollama directly)
 * Tool services are split by concern:
 * - ExpenseToolService: read-only queries (item price, totals, list, summary)
 * - ExpenseCrudToolService: CRUD on expenses, items, stores
 * - ProfileToolService: view/update user profile
 */
@Configuration
public class AiConfig {

    /**
     * Override the auto-configured OllamaApi bean to inject the API key as an
     * Authorization: Bearer header on every request.
     * Only active when the "ollama" Spring profile is selected.
     */
    @Bean
    @Profile("ollama")
    public OllamaApi ollamaApi(
            @Value("${spring.ai.ollama.base-url:http://localhost:11434}") String baseUrl,
            ChatBotProperties chatBotProperties,
            ObjectProvider<RestClient.Builder> restClientBuilderProvider,
            ObjectProvider<WebClient.Builder> webClientBuilderProvider) {

        String apiKey = chatBotProperties.getApiKey();

        RestClient.Builder restClientBuilder = restClientBuilderProvider
                .getIfAvailable(RestClient::builder);

        OllamaApi.Builder apiBuilder = OllamaApi.builder()
                .baseUrl(baseUrl);

        if (apiKey != null && !apiKey.isBlank()) {
            restClientBuilder.defaultHeader("Authorization", "Bearer " + apiKey);

            WebClient.Builder webClientBuilder = webClientBuilderProvider.getIfAvailable();
            if (webClientBuilder != null) {
                apiBuilder.webClientBuilder(
                        webClientBuilder.defaultHeader("Authorization", "Bearer " + apiKey));
            }
        }

        return apiBuilder.restClientBuilder(restClientBuilder).build();
    }

    /**
     * Mark the Ollama chat model as @Primary when the "ollama" profile is active,
     * so that ChatClientAutoConfiguration doesn't complain about two ChatModel beans.
     */
    @Bean
    @Primary
    @Profile({"ollama", "test"})
    public ChatModel primaryChatModel(OllamaChatModel ollamaChatModel) {
        return ollamaChatModel;
    }

    /**
     * Mark the OpenAI chat model as @Primary when the "openai" profile is active.
     */
    @Bean("primaryChatModel")
    @Primary
    @Profile("openai")
    public ChatModel primaryChatModelOpenAi(OpenAiChatModel openAiChatModel) {
        return openAiChatModel;
    }

    @Bean
    public ToolCallbackProvider expenseToolCallbackProvider(
            ExpenseToolService expenseToolService,
            ExpenseCrudToolService expenseCrudToolService,
            ProfileToolService profileToolService) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(expenseToolService, expenseCrudToolService, profileToolService)
                .build();
    }
}

