package com.delfino.expensetracker.service;

import com.delfino.expensetracker.config.ChatBotProperties;
import com.delfino.expensetracker.model.ChatMessage;
import com.delfino.expensetracker.model.Expense;
import com.delfino.expensetracker.model.ExpenseItem;
import com.delfino.expensetracker.model.User;
import com.delfino.expensetracker.repository.ChatMessageRepository;
import com.delfino.expensetracker.repository.ExpenseItemRepository;
import com.delfino.expensetracker.repository.ExpenseRepository;
import com.delfino.expensetracker.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.data.domain.PageRequest;
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
    private final ChatBotProperties chatBotProperties;
    private final ExpenseItemRepository expenseItemRepository;

    public ChatService(ChatMessageRepository chatMessageRepository, ExpenseService expenseService,
                       ExpenseRepository expenseRepository, UserRepository userRepository,
                       ObjectMapper objectMapper, ChatClient.Builder chatClientBuilder,
                       ToolCallbackProvider toolCallbackProvider,
                       ChatBotProperties chatBotProperties,
                       ExpenseItemRepository expenseItemRepository) {
        this.chatMessageRepository = chatMessageRepository;
        this.expenseService = expenseService;
        this.expenseRepository = expenseRepository;
        this.userRepository = userRepository;
        this.objectMapper = objectMapper;
        this.chatBotProperties = chatBotProperties;
        this.expenseItemRepository = expenseItemRepository;

        // Build the ChatClient with all registered tool callbacks
        this.chatClient = chatClientBuilder
                .defaultToolCallbacks(toolCallbackProvider)
                .build();
    }

    public List<ChatMessage> getHistoryPage(Long userId, int limit, int offset) {
        // Fetch newest-first with offset, then reverse to get chronological order
        var pageable = PageRequest.of(offset / limit, limit);
        List<ChatMessage> page = chatMessageRepository.findByUserIdPageable(userId, pageable);
        List<ChatMessage> result = new ArrayList<>(page);
        Collections.reverse(result);
        return result;
    }

    public long countHistory(Long userId) {
        return chatMessageRepository.countByUserId(userId);
    }

    public ChatMessage processUserMessage(Long userId, String messageText) {
        // Save user message
        ChatMessage userMsg = new ChatMessage();
        userMsg.setUserId(userId);
        userMsg.setRole("USER");
        userMsg.setText(messageText);
        userMsg.setCreatedAt(LocalDateTime.now());
        chatMessageRepository.save(userMsg);

        User user = userRepository.findById(userId).get();

        try {
            String resolvedSystemPrompt = chatBotProperties.getSystemPrompt()
                    .replace("{today}", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
                    .replace("{currency}", user.getBaseCurrency());

            log.info("Processing chat message for user {}: '{}'", userId, messageText);

            List<Message> conversationMessages = buildConversationHistory(userId);
            String llmResponse = callLlm(resolvedSystemPrompt, conversationMessages, messageText);

            log.info("LLM response for user {}: {}", userId, llmResponse);

            // Try to parse as JSON (expense-creation flow)
            ProcessedResponse result = processLlmResponse(llmResponse, userId, user);
            return saveBotMessage(userId, result.botText, result.savedExpenseIds);

        } catch (Exception e) {
            log.error("Chatbot processing failed", e);
            return saveBotMessage(userId,
                    "Sorry, I had trouble processing that. Could you try rephrasing? " +
                            "For example: \"lunch 12.50 SGD\" or \"How much did I spend on groceries last month?\"",
                    List.of());
        }
    }

    /**
     * Build conversation history for LLM context (last 10 messages before current).
     */
    private List<Message> buildConversationHistory(Long userId) {
        List<ChatMessage> recentHistory = chatMessageRepository.findTop50ByUserIdOrderByCreatedAtDesc(userId);
        Collections.reverse(recentHistory);
        // Take the last 10 messages (excluding the user message we just saved, which is the last one)
        int historySize = Math.min(recentHistory.size() - 1, 10);
        List<Message> conversationMessages = new ArrayList<>();
        if (historySize > 0) {
            List<ChatMessage> historySlice = recentHistory.subList(
                    Math.max(0, recentHistory.size() - 1 - historySize),
                    recentHistory.size() - 1);
            for (ChatMessage cm : historySlice) {
                if ("USER".equals(cm.getRole())) {
                    conversationMessages.add(new UserMessage(cm.getText()));
                } else {
                    conversationMessages.add(new AssistantMessage(cm.getText()));
                }
            }
        }
        return conversationMessages;
    }

    /**
     * Call the LLM with system prompt, conversation history, and user message.
     */
    private String callLlm(String systemPrompt, List<Message> conversationMessages, String userMessage) {
        return chatClient.prompt()
                .system(systemPrompt)
                .messages(conversationMessages)
                .user(userMessage)
                .call()
                .content();
    }

    /**
     * Process LLM response: parse JSON if it looks like expense data, otherwise treat as plain text.
     */
    private ProcessedResponse processLlmResponse(String llmResponse, Long userId, User user) {
        List<Long> savedExpenseIds = new ArrayList<>();
        String botText = llmResponse;

        if (looksLikeExpenseJson(llmResponse)) {
            try {
                String cleaned = cleanJsonResponse(llmResponse);
                JsonNode parsed = objectMapper.readTree(cleaned);

                if (parsed.has("expenses") && parsed.get("expenses").isArray()) {
                    String summary = parsed.has("summary") ? parsed.get("summary").asText()
                            : "Expenses recorded.";
                    savedExpenseIds = saveExpensesFromJson(parsed.get("expenses"), userId, user);
                    BigDecimal dailyTotal = computeDailyTotal(userId);
                    botText = summary + "\n\n\uD83D\uDCB0 Today's total: "
                            + dailyTotal.setScale(2, RoundingMode.HALF_UP).toPlainString()
                            + " " + user.getBaseCurrency();
                }
            } catch (Exception e) {
                // Not valid JSON — treat llmResponse as plain text (likely a query answer)
                log.debug("LLM response is not parseable as expense JSON, treating as plain text reply");
            }
        }

        return new ProcessedResponse(botText, savedExpenseIds);
    }

    /**
     * Save expenses from JSON array, including their line items.
     */
    private List<Long> saveExpensesFromJson(JsonNode expensesNode, Long userId, User user) {
        List<Long> savedIds = new ArrayList<>();
        for (JsonNode expNode : expensesNode) {
            Expense expense = buildExpenseFromJson(expNode, user);
            Expense saved = expenseService.createManualExpense(expense, userId);
            savedIds.add(saved.getId());
            saveExpenseItems(expNode, saved.getId());
        }
        return savedIds;
    }

    /**
     * Build an Expense object from a JSON node.
     */
    private Expense buildExpenseFromJson(JsonNode expNode, User user) {
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
        } else if (user.getBaseCurrency() != null) {
            expense.setCurrency(user.getBaseCurrency());
        }
        if (expNode.has("category")) {
            expense.setCategory(expNode.get("category").asText());
        }
        if (expNode.has("notes")) {
            expense.setNotes(expNode.get("notes").asText());
        }
        if (expNode.has("storeId") && !expNode.get("storeId").isNull()) {
            try {
                expense.setStoreId(expNode.get("storeId").asLong());
            } catch (Exception ignored) {}
        }

        return expense;
    }

    /**
     * Save expense line items if present in JSON node.
     */
    private void saveExpenseItems(JsonNode expNode, Long expenseId) {
        if (expNode.has("items") && expNode.get("items").isArray()) {
            List<ExpenseItem> items = new ArrayList<>();
            for (JsonNode itemNode : expNode.get("items")) {
                ExpenseItem item = new ExpenseItem();
                item.setExpenseId(expenseId);
                item.setItemName(itemNode.has("itemName") ? itemNode.get("itemName").asText() : "");
                item.setQuantity(itemNode.has("quantity") ? itemNode.get("quantity").decimalValue() : BigDecimal.ONE);
                item.setUnitPrice(itemNode.has("unitPrice") ? itemNode.get("unitPrice").decimalValue() : BigDecimal.ZERO);
                item.setAdjustment(itemNode.has("adjustment") ? itemNode.get("adjustment").decimalValue() : BigDecimal.ZERO);
                item.setDeleted(false);
                items.add(item);
            }
            expenseItemRepository.saveAll(items);
        }
    }

    /**
     * Compute total expenses for today in user's base currency.
     */
    private BigDecimal computeDailyTotal(Long userId) {
        LocalDate today = LocalDate.now();
        List<Expense> todaysExpenses = expenseRepository.findByUserIdAndDeletedFalse(userId);
        return todaysExpenses.stream()
                .filter(e -> e.getTransactionDatetime() != null
                        && e.getTransactionDatetime().toLocalDate().equals(today))
                .map(e -> e.getAmountInBase() != null ? e.getAmountInBase()
                        : (e.getAmount() != null ? e.getAmount() : BigDecimal.ZERO))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
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

    private ChatMessage saveBotMessage(Long userId, String text, List<Long> linkedExpenseIds) {
        ChatMessage botMsg = new ChatMessage();
        botMsg.setUserId(userId);
        botMsg.setRole("BOT");
        botMsg.setText(text);
        botMsg.setLinkedExpenseIds(linkedExpenseIds);
        botMsg.setCreatedAt(LocalDateTime.now());
        chatMessageRepository.save(botMsg);
        return botMsg;
    }

    /**
     * Helper class to hold the result of LLM response processing.
     */
    private static class ProcessedResponse {
        final String botText;
        final List<Long> savedExpenseIds;

        ProcessedResponse(String botText, List<Long> savedExpenseIds) {
            this.botText = botText;
            this.savedExpenseIds = savedExpenseIds;
        }
    }
}

