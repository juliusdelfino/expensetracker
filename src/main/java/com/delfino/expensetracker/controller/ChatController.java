package com.delfino.expensetracker.controller;

import com.delfino.expensetracker.dto.chat.ChatHistoryResponse;
import com.delfino.expensetracker.dto.chat.ChatMessageRequest;
import com.delfino.expensetracker.dto.chat.ChatResponse;
import com.delfino.expensetracker.dto.chat.ExpenseCard;
import com.delfino.expensetracker.dto.chat.ReportCard;
import com.delfino.expensetracker.dto.common.ErrorResponse;
import com.delfino.expensetracker.model.ChatMessage;
import com.delfino.expensetracker.repository.ExpenseRepository;
import com.delfino.expensetracker.repository.ReportRepository;
import com.delfino.expensetracker.service.ChatService;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/chat")
@Validated
public class ChatController {

    private final ChatService chatService;
    private final ExpenseRepository expenseRepository;
    private final ReportRepository reportRepository;

    public ChatController(ChatService chatService, ExpenseRepository expenseRepository, ReportRepository reportRepository) {
        this.chatService = chatService;
        this.expenseRepository = expenseRepository;
        this.reportRepository = reportRepository;
    }

    @PostMapping
    public ResponseEntity<Object> sendMessage(@RequestBody @Valid ChatMessageRequest body, HttpSession session) {
        Long userId = getUserId(session);
        if (userId == null) return ResponseEntity.status(401).body(new ErrorResponse("Not authenticated"));

        String message = body.message();
        if (message == null || message.isBlank()) {
            return ResponseEntity.badRequest().body(new ErrorResponse("Message is required"));
        }

        ChatMessage botReply = chatService.processUserMessage(userId, message);

        List<ExpenseCard> expenseCards = mapExpenseCards(botReply.getLinkedExpenseIds());
        List<ReportCard> reportCards = mapReportCards(userId, botReply.getLinkedReportIds());

        return ResponseEntity.ok(new ChatResponse(botReply, expenseCards, reportCards));
    }

    @GetMapping("/history")
    public ResponseEntity<Object> getHistory(
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
        Set<Long> allReportIds = page.stream()
                .filter(m -> m.getLinkedReportIds() != null)
                .flatMap(m -> m.getLinkedReportIds().stream())
                .collect(Collectors.toSet());

        Map<String, ExpenseCard> expenseMap = new LinkedHashMap<>();
        for (Long expId : allExpenseIds) {
            expenseRepository.findById(expId).ifPresent(e -> expenseMap.put(expId.toString(), toExpenseCard(e)));
        }

        Map<String, ReportCard> reportMap = new LinkedHashMap<>();
        for (Long reportId : allReportIds) {
            reportRepository.findByIdAndUserId(reportId, userId)
                    .ifPresent(report -> reportMap.put(reportId.toString(), toReportCard(report)));
        }

        return ResponseEntity.ok(new ChatHistoryResponse(page, expenseMap, reportMap, hasMore, total));
    }

    private List<ExpenseCard> mapExpenseCards(List<Long> linkedExpenseIds) {
        List<ExpenseCard> expenseCards = new ArrayList<>();
        if (linkedExpenseIds == null) {
            return expenseCards;
        }
        for (Long expId : linkedExpenseIds) {
            expenseRepository.findById(expId).ifPresent(e -> expenseCards.add(toExpenseCard(e)));
        }
        return expenseCards;
    }

    private List<ReportCard> mapReportCards(Long userId, List<Long> linkedReportIds) {
        List<ReportCard> reportCards = new ArrayList<>();
        if (linkedReportIds == null) {
            return reportCards;
        }
        for (Long reportId : linkedReportIds) {
            reportRepository.findByIdAndUserId(reportId, userId)
                    .ifPresent(report -> reportCards.add(toReportCard(report)));
        }
        return reportCards;
    }

    private ExpenseCard toExpenseCard(com.delfino.expensetracker.model.Expense expense) {
        return new ExpenseCard(
                expense.getId(),
                expense.getUrlId(),
                expense.getAmount(),
                expense.getCurrency(),
                expense.getCategory(),
                expense.getNotes(),
                expense.getTransactionDatetime()
        );
    }

    private ReportCard toReportCard(com.delfino.expensetracker.model.Report report) {
        return new ReportCard(
                report.getId(),
                report.getTitle(),
                report.getDescription(),
                report.getGroupBy(),
                report.getExpenseIds() != null ? report.getExpenseIds().size() : 0,
                report.getCreatedAt()
        );
    }

    private Long getUserId(HttpSession session) {
        return (Long) session.getAttribute("userId");
    }
}
