package com.delfino.expensetracker.service;

import com.delfino.expensetracker.model.ChatMessage;
import com.delfino.expensetracker.model.Expense;
import com.delfino.expensetracker.model.User;
import com.delfino.expensetracker.repository.ChatMessageRepository;
import com.delfino.expensetracker.repository.ExpenseRepository;
import com.delfino.expensetracker.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);
    private final ChatMessageRepository chatMessageRepository;
    private final ExpenseService expenseService;
    private final ExpenseRepository expenseRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;
    private final ChatClient chatClient;

    @Value("${chatbot.api.system-prompt:You are an expense tracking assistant.}")
    private String systemPrompt;

    public ChatService(ChatMessageRepository chatMessageRepository, ExpenseService expenseService,
                       ExpenseRepository expenseRepository, UserRepository userRepository,
                       ObjectMapper objectMapper, ChatClient.Builder chatClientBuilder,
                       ToolCallbackProvider toolCallbackProvider) {
        this.chatMessageRepository = chatMessageRepository;
        this.expenseService = expenseService;
        this.expenseRepository = expenseRepository;
        this.userRepository = userRepository;
        this.objectMapper = objectMapper;

        // Build the ChatClient with all registered tool callbacks
        this.chatClient = chatClientBuilder
                .defaultToolCallbacks(toolCallbackProvider)
                .build();
    }

    public List<ChatMessage> getHistory(UUID userId, int limit) {
        List<ChatMessage> recent = chatMessageRepository.findTop50ByUserIdOrderByCreatedAtDesc(userId);
        List<ChatMessage> result = new ArrayList<>(recent);
        Collections.reverse(result);
        return result;
    }

    public List<ChatMessage> getHistoryPage(UUID userId, int limit, int offset) {
        // Fetch newest-first with offset, then reverse to get chronological order
        var pageable = org.springframework.data.domain.PageRequest.of(offset / limit, limit);
        List<ChatMessage> page = chatMessageRepository.findByUserIdPageable(userId, pageable);
        List<ChatMessage> result = new ArrayList<>(page);
        Collections.reverse(result);
        return result;
    }

    public long countHistory(UUID userId) {
        return chatMessageRepository.countByUserId(userId);
    }

    public ChatMessage processUserMessage(UUID userId, String messageText) {
        // Save user message
        ChatMessage userMsg = new ChatMessage();
        userMsg.setId(UUID.randomUUID());
        userMsg.setUserId(userId);
        userMsg.setRole("USER");
        userMsg.setText(messageText);
        userMsg.setCreatedAt(LocalDateTime.now());
        chatMessageRepository.save(userMsg);

        String baseCurrency = userRepository.findById(userId)
                .map(User::getBaseCurrency).orElse("USD");

        try {
            String resolvedSystemPrompt = systemPrompt
                    .replace("{today}", LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE))
                    .replace("{currency}", baseCurrency);

            log.info("Processing chat message for user {}: '{}'", userId, messageText);

            // Call the LLM via Spring AI ChatClient — tool calls are handled automatically.
            // If the LLM decides to call findItemPrice / totalExpenses / etc., Spring AI
            // will invoke the @Tool method and feed the result back before returning.
            String llmResponse = chatClient.prompt()
                    .system(resolvedSystemPrompt)
                    .user(messageText)
                    .call()
                    .content();

            log.info("LLM response for user {}: {}", userId, llmResponse);

            // Try to parse as JSON (expense-creation flow) — the LLM may still return
            // structured JSON when the user wants to log new expenses.
            List<UUID> savedExpenseIds = new ArrayList<>();
            String botText = llmResponse;

            if (looksLikeExpenseJson(llmResponse)) {
                try {
                    String cleaned = cleanJsonResponse(llmResponse);
                    JsonNode parsed = objectMapper.readTree(cleaned);

                    if (parsed.has("expenses") && parsed.get("expenses").isArray()) {
                        String summary = parsed.has("summary") ? parsed.get("summary").asText()
                                : "Expenses recorded.";

                        for (JsonNode expNode : parsed.get("expenses")) {
                            Expense expense = new Expense();
                            if (expNode.has("transactionDatetime")) {
                                try {
                                    expense.setTransactionDatetime(LocalDateTime.parse(
                                            expNode.get("transactionDatetime").asText(),
                                            DateTimeFormatter.ISO_LOCAL_DATE_TIME));
                                } catch (Exception e) {
                                    expense.setTransactionDatetime(LocalDateTime.now());
                                }
                            } else {
                                expense.setTransactionDatetime(LocalDateTime.now());
                            }
                            if (expNode.has("amount")) {
                                expense.setAmount(BigDecimal.valueOf(expNode.get("amount").asDouble()));
                            }
                            if (expNode.has("currency")) {
                                expense.setCurrency(expNode.get("currency").asText());
                            } else {
                                expense.setCurrency(baseCurrency);
                            }
                            if (expNode.has("category")) {
                                expense.setCategory(expNode.get("category").asText());
                            }
                            if (expNode.has("notes")) {
                                expense.setNotes(expNode.get("notes").asText());
                            }

                            Expense saved = expenseService.createManualExpense(expense, userId);
                            savedExpenseIds.add(saved.getId());
                        }

                        // Compute daily total
                        LocalDate today = LocalDate.now();
                        List<Expense> todaysExpenses = expenseRepository.findByUserIdAndDeletedFalse(userId);
                        BigDecimal dailyTotal = todaysExpenses.stream()
                                .filter(e -> e.getTransactionDatetime() != null
                                        && e.getTransactionDatetime().toLocalDate().equals(today))
                                .map(e -> e.getAmountInBase() != null ? e.getAmountInBase()
                                        : (e.getAmount() != null ? e.getAmount() : BigDecimal.ZERO))
                                .reduce(BigDecimal.ZERO, BigDecimal::add);

                        botText = summary + "\n\n\uD83D\uDCB0 Today's total: "
                                + dailyTotal.setScale(2, RoundingMode.HALF_UP).toPlainString()
                                + " " + baseCurrency;
                    }
                } catch (Exception e) {
                    // Not valid JSON — treat llmResponse as plain text (likely a query answer)
                    log.debug("LLM response is not parseable as expense JSON, treating as plain text reply");
                }
            }

            return saveBotMessage(userId, botText, savedExpenseIds);

        } catch (Exception e) {
            log.error("Chatbot processing failed", e);
            return saveBotMessage(userId,
                    "Sorry, I had trouble processing that. Could you try rephrasing? " +
                            "For example: \"lunch 12.50 SGD\" or \"How much did I spend on groceries last month?\"",
                    List.of());
        }
    }

    /**
     * Quick heuristic: does the LLM output look like it's a JSON expense-creation response?
     */
    private boolean looksLikeExpenseJson(String text) {
        if (text == null) return false;
        String trimmed = text.strip();
        if (trimmed.startsWith("```")) {
            trimmed = trimmed.replaceAll("^```[a-zA-Z]*\\n?", "").replaceAll("```$", "").strip();
        }
        return trimmed.startsWith("{") && trimmed.contains("\"expenses\"");
    }

    private String cleanJsonResponse(String text) {
        String cleaned = text.strip();
        if (cleaned.startsWith("```")) {
            cleaned = cleaned.replaceAll("^```[a-zA-Z]*\\n?", "").replaceAll("```$", "").strip();
        }
        return cleaned;
    }

    private ChatMessage saveBotMessage(UUID userId, String text, List<UUID> linkedExpenseIds) {
        ChatMessage botMsg = new ChatMessage();
        botMsg.setId(UUID.randomUUID());
        botMsg.setUserId(userId);
        botMsg.setRole("BOT");
        botMsg.setText(text);
        botMsg.setLinkedExpenseIds(linkedExpenseIds);
        botMsg.setCreatedAt(LocalDateTime.now());
        chatMessageRepository.save(botMsg);
        return botMsg;
    }
}

