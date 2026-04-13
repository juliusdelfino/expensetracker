package com.delfino.expensetracker.controller;

import com.delfino.expensetracker.dto.chat.ChatHistoryResponse;
import com.delfino.expensetracker.dto.chat.ChatMessageRequest;
import com.delfino.expensetracker.dto.chat.ChatResponse;
import com.delfino.expensetracker.dto.chat.ExpenseCard;
import com.delfino.expensetracker.dto.common.ErrorResponse;
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

    public ChatController(ChatService chatService, ExpenseRepository expenseRepository) {
        this.chatService = chatService;
        this.expenseRepository = expenseRepository;
    }

    @PostMapping
    public ResponseEntity<?> sendMessage(@RequestBody ChatMessageRequest body, HttpSession session) {
        Long userId = getUserId(session);
        if (userId == null) return ResponseEntity.status(401).body(new ErrorResponse("Not authenticated"));

        String message = body.message();
        if (message == null || message.isBlank()) {
            return ResponseEntity.badRequest().body(new ErrorResponse("Message is required"));
        }

        ChatMessage botReply = chatService.processUserMessage(userId, message);

        List<ExpenseCard> expenseCards = new ArrayList<>();
        if (botReply.getLinkedExpenseIds() != null) {
            for (Long expId : botReply.getLinkedExpenseIds()) {
                expenseRepository.findById(expId).ifPresent(e -> expenseCards.add(new ExpenseCard(
                        e.getId(),
                        e.getUrlId(),
                        e.getAmount(),
                        e.getCurrency(),
                        e.getCategory(),
                        e.getNotes(),
                        e.getTransactionDatetime()
                )));
            }
        }

        return ResponseEntity.ok(new ChatResponse(botReply, expenseCards));
    }

    @GetMapping("/history")
    public ResponseEntity<?> getHistory(
            @RequestParam(defaultValue = "10") int limit,
            @RequestParam(defaultValue = "0") int offset,
            HttpSession session) {
        Long userId = getUserId(session);
        if (userId == null) return ResponseEntity.status(401).body(new ErrorResponse("Not authenticated"));

        List<ChatMessage> page = chatService.getHistoryPage(userId, limit, offset);
        long total = chatService.countHistory(userId);
        boolean hasMore = (offset + limit) < total;

        Set<Long> allExpenseIds = page.stream()
                .filter(m -> m.getLinkedExpenseIds() != null)
                .flatMap(m -> m.getLinkedExpenseIds().stream())
                .collect(Collectors.toSet());

        Map<String, ExpenseCard> expenseMap = new LinkedHashMap<>();
        for (Long expId : allExpenseIds) {
            expenseRepository.findById(expId).ifPresent(e -> expenseMap.put(
                    expId.toString(),
                    new ExpenseCard(
                            e.getId(),
                            e.getUrlId(),
                            e.getAmount(),
                            e.getCurrency(),
                            e.getCategory(),
                            e.getNotes(),
                            e.getTransactionDatetime()
                    )
            ));
        }

        return ResponseEntity.ok(new ChatHistoryResponse(page, expenseMap, hasMore, total));
    }

    private Long getUserId(HttpSession session) {
        return (Long) session.getAttribute("userId");
    }
}
