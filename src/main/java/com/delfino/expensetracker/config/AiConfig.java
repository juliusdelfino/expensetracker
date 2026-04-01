package com.delfino.expensetracker.config;

import com.delfino.expensetracker.service.ExpenseCrudToolService;
import com.delfino.expensetracker.service.ExpenseToolService;
import com.delfino.expensetracker.service.ProfileToolService;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registers @Tool-annotated beans with Spring AI so that:
 * 1) The ChatClient can invoke them during tool-calling conversations
 * 2) The MCP server exposes them for external MCP clients (e.g. Ollama directly)
 *
 * Tool services are split by concern:
 * - ExpenseToolService: read-only queries (item price, totals, list, summary)
 * - ExpenseCrudToolService: CRUD on expenses, items, stores
 * - ProfileToolService: view/update user profile
 */
@Configuration
public class AiConfig {

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

