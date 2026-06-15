package com.delfino.expensetracker.service;

import com.delfino.expensetracker.model.Expense;
import com.delfino.expensetracker.model.Store;
import com.delfino.expensetracker.repository.AiUsageRepository;
import com.delfino.expensetracker.repository.ExpenseItemRepository;
import com.delfino.expensetracker.repository.ExpenseRepository;
import com.delfino.expensetracker.repository.StoreRepository;
import com.delfino.expensetracker.repository.UserRepository;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

/**
 * Handles permanent deletion of a user account and all associated data.
 *
 * <p>Deletion order (safe):
 * <ol>
 *   <li>Validate current password</li>
 *   <li>Validate typed confirmation phrase</li>
 *   <li>Delete linked files (receipts, attachment directories)</li>
 *   <li>Delete expense items</li>
 *   <li>Delete expenses</li>
 *   <li>Delete user-owned stores</li>
 *   <li>Delete AI usage records</li>
 *   <li>Delete the user row</li>
 *   <li>Invalidate the HTTP session</li>
 * </ol>
 */
@Service
public class UserDeletionService {

    private static final Logger log = LoggerFactory.getLogger(UserDeletionService.class);
    static final String REQUIRED_CONFIRMATION = "DELETE";

    private final UserRepository userRepository;
    private final ExpenseRepository expenseRepository;
    private final ExpenseItemRepository expenseItemRepository;
    private final StoreRepository storeRepository;
    private final AiUsageRepository aiUsageRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.data.dir:data}")
    private String dataDir;

    public UserDeletionService(UserRepository userRepository,
                               ExpenseRepository expenseRepository,
                               ExpenseItemRepository expenseItemRepository,
                               StoreRepository storeRepository,
                               AiUsageRepository aiUsageRepository,
                               PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.expenseRepository = expenseRepository;
        this.expenseItemRepository = expenseItemRepository;
        this.storeRepository = storeRepository;
        this.aiUsageRepository = aiUsageRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Validate the request, delete all user data, and invalidate the session.
     *
     * @throws IllegalArgumentException if password is wrong or confirmation phrase is missing/wrong
     */
    @Transactional
    public void deleteAccount(long userId, String rawPassword, String confirmation, HttpSession session) {
        var user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalStateException("User not found"));

        // 1. Validate password
        if (rawPassword == null || rawPassword.isBlank()
                || !passwordEncoder.matches(rawPassword, user.getPasswordHash())) {
            throw new IllegalArgumentException("Incorrect password");
        }

        // 2. Validate confirmation phrase
        if (!REQUIRED_CONFIRMATION.equals(confirmation)) {
            throw new IllegalArgumentException("Confirmation phrase must be exactly: " + REQUIRED_CONFIRMATION);
        }

        log.info("Account deletion initiated for user {} ({})", user.getUsername(), userId);

        // 3. Delete files first (idempotent — missing files are skipped)
        List<Expense> allExpenses = expenseRepository.findByUserId(userId);
        for (Expense e : allExpenses) {
            deleteExpenseFiles(e);
        }

        // 4. Delete expense items
        for (Expense e : allExpenses) {
            expenseItemRepository.deleteByExpenseId(e.getId());
        }

        // 5. Delete expenses
        expenseRepository.deleteAll(allExpenses);

        // 6. Delete user-owned stores
        List<Store> stores = storeRepository.findByUserId(userId);
        storeRepository.deleteAll(stores);

        // 7. Delete AI usage records
        aiUsageRepository.deleteByUserId(userId);

        // 8. Delete the user row
        userRepository.deleteById(userId);

        log.info("Account permanently deleted: user {} ({}), {} expenses, {} stores removed",
                user.getUsername(), userId, allExpenses.size(), stores.size());

        // 9. Invalidate the session
        try {
            session.invalidate();
        } catch (IllegalStateException ignored) {
            // Session may already be invalidated
        }
    }

    // --- file helpers ---

    private void deleteExpenseFiles(Expense expense) {
        if (expense.getImagePath() != null && !expense.getImagePath().isBlank()) {
            try {
                Files.deleteIfExists(Path.of(expense.getImagePath()));
            } catch (IOException e) {
                log.warn("Could not delete receipt image for expense {}: {}", expense.getUrlId(), e.getMessage());
            }
        }
        if (expense.getUrlId() != null) {
            deleteDirectoryQuietly(Path.of(dataDir, "attachments", expense.getUrlId()));
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
            log.warn("Could not delete directory {}: {}", dir, e.getMessage());
        }
    }
}

