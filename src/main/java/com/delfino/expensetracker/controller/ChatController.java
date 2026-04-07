package com.delfino.expensetracker.controller;

import com.delfino.expensetracker.config.UserContext;
import com.delfino.expensetracker.model.ChatMessage;
import com.delfino.expensetracker.repository.ExpenseRepository;
import com.delfino.expensetracker.service.ChatService;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final ChatService chatService;
    private final ExpenseRepository expenseRepository;
    private final UserContext userContext;

    public ChatController(ChatService chatService, ExpenseRepository expenseRepository, UserContext userContext) {
        this.chatService = chatService;
        this.expenseRepository = expenseRepository;
        this.userContext = userContext;
    }

    @PostMapping
    public ResponseEntity<?> sendMessage(@RequestBody Map<String, String> body, HttpSession session) {
        Long userId = getUserId(session);
        if (userId == null) return ResponseEntity.status(401).body(Map.of("error", "Not authenticated"));

        // Set the user context so @Tool methods can access the authenticated userId
        userContext.setUserId(userId);

        String message = body.get("message");
        if (message == null || message.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Message is required"));
        }

        ChatMessage botReply = chatService.processUserMessage(userId, message);

        // Enrich response with linked expense details
        List<Map<String, Object>> expenseCards = new ArrayList<>();
        if (botReply.getLinkedExpenseIds() != null) {
            for (Long expId : botReply.getLinkedExpenseIds()) {
                expenseRepository.findById(expId).ifPresent(e -> {
                    Map<String, Object> card = new LinkedHashMap<>();
                    card.put("id", e.getId());
                    card.put("urlId", e.getUrlId());
                    card.put("amount", e.getAmount());
                    card.put("currency", e.getCurrency());
                    card.put("category", e.getCategory());
                    card.put("notes", e.getNotes());
                    card.put("transactionDatetime", e.getTransactionDatetime());
                    expenseCards.add(card);
                });
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("message", botReply);
        result.put("expenseCards", expenseCards);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/history")
    public ResponseEntity<?> getHistory(
            @RequestParam(defaultValue = "10") int limit,
            @RequestParam(defaultValue = "0") int offset,
            HttpSession session) {
        Long userId = getUserId(session);
        if (userId == null) return ResponseEntity.status(401).body(Map.of("error", "Not authenticated"));

        // Fetch newest-first with offset, then reverse to get chronological order
        List<ChatMessage> page = chatService.getHistoryPage(userId, limit, offset);

        long total = chatService.countHistory(userId);
        boolean hasMore = (offset + limit) < total;

        // Collect all linked expense IDs and fetch them for card rendering
        Set<Long> allExpenseIds = page.stream()
                .filter(m -> m.getLinkedExpenseIds() != null)
                .flatMap(m -> m.getLinkedExpenseIds().stream())
                .collect(Collectors.toSet());

        Map<String, Map<String, Object>> expenseMap = new LinkedHashMap<>();
        for (Long expId : allExpenseIds) {
            expenseRepository.findById(expId).ifPresent(e -> {
                Map<String, Object> card = new LinkedHashMap<>();
                card.put("id", e.getId());
                card.put("amount", e.getAmount());
                card.put("currency", e.getCurrency());
                card.put("category", e.getCategory());
                card.put("notes", e.getNotes());
                card.put("urlId", e.getUrlId());
                card.put("transactionDatetime", e.getTransactionDatetime());
                expenseMap.put(expId.toString(), card);
            });
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("messages", page);
        result.put("expenses", expenseMap);
        result.put("hasMore", hasMore);
        result.put("total", total);
        return ResponseEntity.ok(result);
    }

    private Long getUserId(HttpSession session) {
        return (Long) session.getAttribute("userId");
    }
}


