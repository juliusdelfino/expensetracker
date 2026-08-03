package com.delfino.expensetracker.service;

import com.delfino.expensetracker.config.ChatBotProperties;
import com.delfino.expensetracker.dto.chat.ChatExpenseDto;
import com.delfino.expensetracker.dto.chat.ChatExpenseItemDto;
import com.delfino.expensetracker.dto.chat.ChatExpenseResponseDto;
import com.delfino.expensetracker.exception.AiQuotaExceededException;
import com.delfino.expensetracker.model.AiUsageType;
import com.delfino.expensetracker.model.ChatMessage;
import com.delfino.expensetracker.model.Expense;
import com.delfino.expensetracker.model.ExpenseItem;
import com.delfino.expensetracker.model.User;
import com.delfino.expensetracker.repository.ChatMessageRepository;
import com.delfino.expensetracker.repository.ExpenseItemRepository;
import com.delfino.expensetracker.repository.ExpenseRepository;
import com.delfino.expensetracker.repository.UserRepository;
import com.delfino.expensetracker.service.mcp.ChatReportContext;
import com.delfino.expensetracker.service.mcp.ChatReportContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
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
    private static final String PROVIDER_TAG = "provider";
    private static final String MODEL_TAG = "model";
    private static final String OUTCOME_TAG = "outcome";
    private final ChatMessageRepository chatMessageRepository;
    private final ExpenseService expenseService;
    private final ExpenseRepository expenseRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;
    private final ChatModelResolver chatModelResolver;
    private final ToolCallbackProvider toolCallbackProvider;
    private final ChatBotProperties chatBotProperties;
    private final ExpenseItemRepository expenseItemRepository;
    private final AiUsageService aiUsageService;
    private final MeterRegistry meterRegistry;
    private final Map<String, ChatClient> chatClients = new HashMap<>();
    private final ChatReportContext chatReportContext;

    public ChatService(ChatMessageRepository chatMessageRepository, ExpenseService expenseService,
                       ExpenseRepository expenseRepository, UserRepository userRepository,
                       ObjectMapper objectMapper, ChatModelResolver chatModelResolver,
                       ToolCallbackProvider toolCallbackProvider,
                       ChatBotProperties chatBotProperties,
                       ExpenseItemRepository expenseItemRepository,
                       AiUsageService aiUsageService,
                       MeterRegistry meterRegistry,
                       ChatReportContext chatReportContext) {
        this.chatMessageRepository = chatMessageRepository;
        this.expenseService = expenseService;
        this.expenseRepository = expenseRepository;
        this.userRepository = userRepository;
        this.objectMapper = objectMapper;
        this.chatModelResolver = chatModelResolver;
        this.toolCallbackProvider = toolCallbackProvider;
        this.chatBotProperties = chatBotProperties;
        this.expenseItemRepository = expenseItemRepository;
        this.aiUsageService = aiUsageService;
        this.meterRegistry = meterRegistry;
        this.chatReportContext = chatReportContext;
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
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalStateException("User not found: " + userId));
        AiUsageService.QuotaCheckResult chatQuota = aiUsageService.checkQuota(userId, AiUsageType.CHAT);
        if (!chatQuota.allowed()) {
            throw new AiQuotaExceededException(
                    AiUsageType.CHAT,
                    chatQuota.usageCount(),
                    chatQuota.quota(),
                    chatQuota.requestedUnits());
        }
        chatReportContext.clear();

        // Save user message
        ChatMessage userMsg = new ChatMessage();
        userMsg.setUserId(userId);
        userMsg.setRole("USER");
        userMsg.setText(messageText);
        userMsg.setCreatedAt(LocalDateTime.now());
        chatMessageRepository.save(userMsg);

        try {
            String resolvedSystemPrompt = chatBotProperties.getSystemPrompt()
                    .replace("{today}", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
                    .replace("{currency}", user.getBaseCurrency());

            log.info("Processing chat message for user {}: '{}'", userId, messageText);

            List<Message> conversationMessages = buildConversationHistory(userId);
            String llmResponse = callLlm(user, resolvedSystemPrompt, conversationMessages, messageText);
            aiUsageService.consume(userId, AiUsageType.CHAT);

            log.info("LLM response for user {}: {}", userId, llmResponse);

            // Try to parse as JSON (expense-creation flow)
            ProcessedResponse result = processLlmResponse(llmResponse, userId, user);
            return saveBotMessage(userId, result.botText(), result.savedExpenseIds(), chatReportContext.getLinkedReportIds());

        } catch (AiQuotaExceededException e) {
            throw e;
        } catch (Exception e) {
            log.error("Chatbot processing failed", e);
            return saveBotMessage(userId,
                    "Sorry, I had trouble processing that. Could you try rephrasing? " +
                            "For example: \"lunch 12.50 SGD\" or \"How much did I spend on groceries last month?\"",
                    List.of(),
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
    private String callLlm(User user, String systemPrompt, List<Message> conversationMessages, String userMessage) {
        ChatModelResolver.ResolvedChatModel resolvedChatModel = chatModelResolver.resolveForUser(user);
        String providerKey = resolvedChatModel.modelDefinition().getProvider().name();
        String modelId = resolvedChatModel.modelDefinition().getId();
        ChatClient chatClient = chatClients.computeIfAbsent(providerKey, ignored -> ChatClient.builder(resolvedChatModel.chatModel())
                .defaultToolCallbacks(toolCallbackProvider)
                .build());

        Timer.Sample sample = Timer.start(meterRegistry);
        log.info("Resolved chat provider for user {}: provider={} model={}", user.getId(), providerKey, modelId);

        try {
            String content = chatClient.prompt()
                    .system(systemPrompt)
                    .options(resolvedChatModel.chatOptions())
                    .messages(conversationMessages)
                    .user(userMessage)
                    .call()
                    .content();

            recordChatMetrics(sample, providerKey, modelId, "success");
            return content;
        } catch (RuntimeException ex) {
            recordChatMetrics(sample, providerKey, modelId, "error");
            throw ex;
        }
    }

    private void recordChatMetrics(Timer.Sample sample, String providerKey, String modelId, String outcome) {
        String normalizedProvider = providerKey.toLowerCase(Locale.ROOT);
        meterRegistry.counter("app.ai.chat.calls", PROVIDER_TAG, normalizedProvider, MODEL_TAG, modelId, OUTCOME_TAG, outcome)
                .increment();
        sample.stop(Timer.builder("app.ai.chat.latency")
                .tag(PROVIDER_TAG, normalizedProvider)
                .tag(MODEL_TAG, modelId)
                .tag(OUTCOME_TAG, outcome)
                .register(meterRegistry));
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
                ChatExpenseResponseDto response = objectMapper.readValue(cleaned, ChatExpenseResponseDto.class);

                if (response.expenses() != null && !response.expenses().isEmpty()) {
                    String summary = response.summary() != null ? response.summary() : "Expenses recorded.";
                    savedExpenseIds = saveExpensesFromDto(response.expenses(), userId, user);
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
     * Save expenses from the DTO list, including their line items.
     */
    private List<Long> saveExpensesFromDto(List<ChatExpenseDto> expenses, Long userId, User user) {
        List<Long> savedIds = new ArrayList<>();
        for (ChatExpenseDto expDto : expenses) {
            Expense expense = buildExpenseFromDto(expDto, user);
            Expense saved = expenseService.createManualExpense(expense, userId);
            savedIds.add(saved.getId());
            saveExpenseItemsFromDto(expDto, saved.getId());
        }
        return savedIds;
    }

    /**
     * Build an Expense entity from a typed DTO.
     */
    private Expense buildExpenseFromDto(ChatExpenseDto dto, User user) {
        Expense expense = new Expense();

        if (dto.transactionDatetime() != null) {
            try {
                expense.setTransactionDatetime(LocalDateTime.parse(
                        dto.transactionDatetime(), DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            } catch (Exception e) {
                expense.setTransactionDatetime(LocalDateTime.now());
            }
        } else {
            expense.setTransactionDatetime(LocalDateTime.now());
        }

        if (dto.amount() != null) expense.setAmount(dto.amount());
        if (dto.currency() != null) {
            expense.setCurrency(dto.currency());
        } else if (user.getBaseCurrency() != null) {
            expense.setCurrency(user.getBaseCurrency());
        }
        if (dto.category() != null) expense.setCategory(dto.category());
        if (dto.notes() != null) expense.setNotes(dto.notes());
        if (dto.storeId() != null) expense.setStoreId(dto.storeId());

        return expense;
    }

    /**
     * Persist expense line items from a typed DTO.
     */
    private void saveExpenseItemsFromDto(ChatExpenseDto expDto, Long expenseId) {
        if (expDto.items() == null || expDto.items().isEmpty()) return;
        List<ExpenseItem> items = new ArrayList<>();
        for (ChatExpenseItemDto itemDto : expDto.items()) {
            ExpenseItem item = new ExpenseItem();
            item.setExpenseId(expenseId);
            item.setItemName(itemDto.itemName() != null ? itemDto.itemName() : "");
            item.setQuantity(itemDto.quantity() != null ? itemDto.quantity() : BigDecimal.ONE);
            item.setUnitPrice(itemDto.unitPrice() != null ? itemDto.unitPrice() : BigDecimal.ZERO);
            item.setAdjustment(itemDto.adjustment() != null ? itemDto.adjustment() : BigDecimal.ZERO);
            item.setDeleted(false);
            items.add(item);
        }
        expenseItemRepository.saveAll(items);
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
                .map(this::resolveExpenseAmountForTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal resolveExpenseAmountForTotal(Expense expense) {
        if (expense.getAmountInBase() != null) {
            return expense.getAmountInBase();
        }
        if (expense.getAmount() != null) {
            return expense.getAmount();
        }
        return BigDecimal.ZERO;
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

    private ChatMessage saveBotMessage(Long userId, String text, List<Long> linkedExpenseIds, List<Long> linkedReportIds) {
        ChatMessage botMsg = new ChatMessage();
        botMsg.setUserId(userId);
        botMsg.setRole("BOT");
        botMsg.setText(text);
        botMsg.setLinkedExpenseIds(linkedExpenseIds);
        botMsg.setLinkedReportIds(linkedReportIds);
        botMsg.setCreatedAt(LocalDateTime.now());
        chatMessageRepository.save(botMsg);
        return botMsg;
    }

    /**
     * Holds the result of LLM response processing.
     */
    private record ProcessedResponse(String botText, List<Long> savedExpenseIds) {}
}

