package com.delfino.expensetracker.service;

import com.delfino.expensetracker.model.Expense;
import com.delfino.expensetracker.repository.ExpenseItemRepository;
import com.delfino.expensetracker.repository.ExpenseRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

/**
 * Handles permanent deletion and restore of soft-deleted (trashed) expenses,
 * including cleanup of associated receipt files and attachment directories.
 */
@Service
public class UserTrashService {

    private static final Logger log = LoggerFactory.getLogger(UserTrashService.class);

    private final ExpenseRepository expenseRepository;
    private final ExpenseItemRepository expenseItemRepository;

    @Value("${app.data.dir:data}")
    private String dataDir;

    public UserTrashService(ExpenseRepository expenseRepository,
                            ExpenseItemRepository expenseItemRepository) {
        this.expenseRepository = expenseRepository;
        this.expenseItemRepository = expenseItemRepository;
    }

    /** Return all soft-deleted expenses for the given user, newest first. */
    public List<Expense> listTrashed(long userId) {
        return expenseRepository.findByUserIdAndDeletedTrue(userId).stream()
                .sorted(Comparator.comparing(
                        e -> e.getUpdatedAt() != null ? e.getUpdatedAt() : e.getCreatedAt(),
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }

    /** Permanently delete one soft-deleted expense and its linked files. */
    @Transactional
    public void purgeOne(String urlId, long userId) {
        Expense expense = expenseRepository.findByUrlId(urlId)
                .orElseThrow(() -> new IllegalStateException("Expense not found: " + urlId));
        if (expense.getUserId() != userId) throw new AuthorizationDeniedException("Not authorized");
        if (!expense.isDeleted()) throw new IllegalStateException("Expense is not in trash: " + urlId);

        deleteFiles(expense);
        expenseItemRepository.deleteByExpenseId(expense.getId());
        expenseRepository.delete(expense);
        log.info("Permanently purged expense {} for user {}", urlId, userId);
    }

    /** Permanently delete a list of trashed expenses (or all trash if ids is empty/null). */
    @Transactional
    public int purgeAll(long userId, List<String> urlIds) {
        List<Expense> toDelete;
        if (urlIds == null || urlIds.isEmpty()) {
            toDelete = expenseRepository.findByUserIdAndDeletedTrue(userId);
        } else {
            toDelete = urlIds.stream()
                    .map(id -> expenseRepository.findByUrlId(id)
                            .orElseThrow(() -> new IllegalStateException("Expense not found: " + id)))
                    .toList();
            for (Expense e : toDelete) {
                if (e.getUserId() != userId) throw new AuthorizationDeniedException("Not authorized");
                if (!e.isDeleted()) throw new IllegalStateException("Expense is not in trash: " + e.getUrlId());
            }
        }
        for (Expense e : toDelete) {
            deleteFiles(e);
            expenseItemRepository.deleteByExpenseId(e.getId());
        }
        expenseRepository.deleteAll(toDelete);
        log.info("Bulk purged {} trashed expenses for user {}", toDelete.size(), userId);
        return toDelete.size();
    }

    /** Restore a soft-deleted expense back to active. */
    @Transactional
    public void restore(String urlId, long userId) {
        Expense expense = expenseRepository.findByUrlId(urlId)
                .orElseThrow(() -> new IllegalStateException("Expense not found: " + urlId));
        if (expense.getUserId() != userId) throw new AuthorizationDeniedException("Not authorized");
        if (!expense.isDeleted()) throw new IllegalStateException("Expense is not in trash: " + urlId);

        expense.setDeleted(false);
        expense.setUpdatedAt(java.time.LocalDateTime.now());
        expenseRepository.save(expense);
        expenseItemRepository.restoreByExpenseId(expense.getId());
        log.info("Restored expense {} for user {}", urlId, userId);
    }

    // --- private helpers ---

    private void deleteFiles(Expense expense) {
        // Delete scanned receipt image
        if (expense.getImagePath() != null && !expense.getImagePath().isBlank()) {
            try {
                Files.deleteIfExists(Path.of(expense.getImagePath()));
            } catch (IOException e) {
                log.warn("Could not delete receipt image for expense {}: {}", expense.getUrlId(), e.getMessage());
            }
        }
        // Delete attachments directory
        if (expense.getUrlId() != null) {
            Path attachDir = Path.of(dataDir, "attachments", expense.getUrlId());
            deleteDirectoryQuietly(attachDir);
        }
    }

    private void deleteDirectoryQuietly(Path dir) {
        if (!Files.exists(dir)) return;
        try {
            Files.walk(dir)
                    .sorted(Comparator.reverseOrder())
                    .forEach(p -> {
                        try { Files.deleteIfExists(p); } catch (IOException e) {
                            log.warn("Could not delete {}: {}", p, e.getMessage());
                        }
                    });
        } catch (IOException e) {
            log.warn("Could not delete attachment dir {}: {}", dir, e.getMessage());
        }
    }
}

