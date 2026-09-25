package com.delfino.expensetracker.controller;

import com.delfino.expensetracker.dto.auth.UserToken;
import com.delfino.expensetracker.repository.ExpenseRepository;
import com.delfino.expensetracker.util.ReceiptStorageUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.web.bind.annotation.*;

import java.nio.file.Files;
import java.nio.file.Path;

@RestController
@RequestMapping("/api/attachments")
public class AttachmentController {

    private final ExpenseRepository expenseRepository;

    @Value("${app.data.dir:data}")
    private String dataDir;

    public AttachmentController(ExpenseRepository expenseRepository) {
        this.expenseRepository = expenseRepository;
    }

    @GetMapping("/receipts/{userId}/{yearMonth}/{filename}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Resource> getReceipt(@PathVariable long userId,
                                               @PathVariable String yearMonth,
                                               @PathVariable String filename,
                                               UserToken userToken) {
        if (userToken == null || userToken.getUserId() != userId) {
            throw new AuthorizationDeniedException("Not authorized");
        }

        String storedPath = ReceiptStorageUtils.normalizeSeparators(
                Path.of("receipts", String.valueOf(userId), yearMonth, filename).toString());
        if (expenseRepository.findByUserIdAndImagePath(userId, storedPath).isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        return serveFile(ReceiptStorageUtils.resolveStoredPath(dataDir, storedPath));
    }

    @GetMapping("/{expenseId}/{filename}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Resource> getAttachment(@PathVariable String expenseId,
                                                  @PathVariable String filename,
                                                  UserToken userToken) {
        return expenseRepository.findByUrlId(expenseId)
                .map(expense -> {
                    if (userToken == null || expense.getUserId() != userToken.getUserId()) {
                        throw new AuthorizationDeniedException("Not authorized");
                    }
                    if (expense.getAttachments() == null) {
                        return ResponseEntity.notFound().<Resource>build();
                    }
                    return expense.getAttachments().stream()
                            .filter(path -> path != null && path.replace('\\', '/').endsWith("/" + filename))
                            .findFirst()
                            .map(path -> serveFile(ReceiptStorageUtils.resolveStoredPath(dataDir, path)))
                            .orElse(ResponseEntity.notFound().<Resource>build());
                })
                .orElse(ResponseEntity.notFound().<Resource>build());
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
