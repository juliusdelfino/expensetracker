package com.delfino.expensetracker.service;

import com.delfino.expensetracker.model.Expense;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ExpenseOcrQueueService {

    private static final Logger log = LoggerFactory.getLogger(ExpenseOcrQueueService.class);

    private final OcrService ocrService;

    public ExpenseOcrQueueService(OcrService ocrService) {
        this.ocrService = ocrService;
    }

    @Async
    public void processOcrQueue(List<Expense> expenses) {
        for (int i = 0; i < expenses.size(); i++) {
            Expense expense = expenses.get(i);
            log.info("Processing OCR queue item {}/{}: expense {}", i + 1, expenses.size(), expense.getId());
            ocrService.processReceiptSync(expense.getId(), expense.getImagePath());
        }
    }
}

