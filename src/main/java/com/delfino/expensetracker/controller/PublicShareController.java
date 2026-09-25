package com.delfino.expensetracker.controller;

import com.delfino.expensetracker.dto.common.ErrorResponse;
import com.delfino.expensetracker.dto.expense.ExpenseDetailResponse;
import com.delfino.expensetracker.model.Expense;
import com.delfino.expensetracker.model.ExpenseShare;
import com.delfino.expensetracker.repository.ExpenseItemRepository;
import com.delfino.expensetracker.repository.StoreRepository;
import com.delfino.expensetracker.service.ExpenseShareService;
import com.delfino.expensetracker.service.PublicShareRateLimitService;
import com.delfino.expensetracker.service.ShareAccessLogService;
import jakarta.servlet.http.HttpServletRequest;
import com.delfino.expensetracker.util.ReceiptStorageUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.nio.file.Files;
import java.nio.file.Path;

@RestController
public class PublicShareController {

    private final ExpenseShareService expenseShareService;
    private final ExpenseItemRepository expenseItemRepository;
    private final StoreRepository storeRepository;
    private final PublicShareRateLimitService publicShareRateLimitService;
    private final ShareAccessLogService shareAccessLogService;

    @Value("${app.data.dir:data}")
    private String dataDir;

    public PublicShareController(ExpenseShareService expenseShareService,
                                 ExpenseItemRepository expenseItemRepository,
                                 StoreRepository storeRepository,
                                 PublicShareRateLimitService publicShareRateLimitService,
                                 ShareAccessLogService shareAccessLogService) {
        this.expenseShareService = expenseShareService;
        this.expenseItemRepository = expenseItemRepository;
        this.storeRepository = storeRepository;
        this.publicShareRateLimitService = publicShareRateLimitService;
        this.shareAccessLogService = shareAccessLogService;
    }

    @GetMapping("/api/share/{shareToken}/expense")
    public ResponseEntity<?> getSharedExpense(@PathVariable String shareToken, HttpServletRequest request) {
        if (!publicShareRateLimitService.allow(request)) {
            return ResponseEntity.status(429).body(new ErrorResponse("Too many share-link requests. Please try again shortly."));
        }
        try {
            ExpenseShare share = expenseShareService.requireActiveShare(shareToken);
            Expense expense = expenseShareService.resolveSharedExpense(share);
            shareAccessLogService.recordAccess(request, share, "EXPENSE", null, true, 200);
            return ResponseEntity.ok(new ExpenseDetailResponse(
                    expense,
                    expenseItemRepository.findByExpenseId(expense.getId()),
                    expense.getStoreId() != null ? storeRepository.findById(expense.getStoreId()).orElse(null) : null,
                    false
            ));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(404).body(new ErrorResponse(e.getMessage()));
        }
    }

    @GetMapping("/api/share/{shareToken}/receipts/{filename}")
    public ResponseEntity<Resource> getSharedReceipt(@PathVariable String shareToken,
                                                     @PathVariable String filename,
                                                     HttpServletRequest request) {
        if (!publicShareRateLimitService.allow(request)) {
            return ResponseEntity.status(429).build();
        }
        try {
            ExpenseShare share = expenseShareService.requireActiveShare(shareToken);
            return expenseShareService.resolveSharedReceiptPath(shareToken, filename)
                    .map(path -> {
                        ResponseEntity<Resource> response = serveFile(ReceiptStorageUtils.resolveStoredPath(dataDir, path));
                        shareAccessLogService.recordAccess(request, share, "FILE", filename, response.getStatusCode().is2xxSuccessful(), response.getStatusCode().value());
                        return response;
                    })
                    .orElseGet(() -> {
                        shareAccessLogService.recordAccess(request, share, "FILE", filename, false, 404);
                        return ResponseEntity.notFound().build();
                    });
        } catch (IllegalStateException e) {
            return ResponseEntity.notFound().build();
        }
    }

    private ResponseEntity<Resource> serveFile(Path path) {
        if (!Files.exists(path)) return ResponseEntity.notFound().build();
        try {
            String contentType = Files.probeContentType(path);
            if (contentType == null) contentType = "application/octet-stream";
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    .body(new FileSystemResource(path));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }
}
