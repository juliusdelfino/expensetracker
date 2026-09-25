package com.delfino.expensetracker.service;

import com.delfino.expensetracker.model.Expense;
import com.delfino.expensetracker.model.ExpenseShare;
import com.delfino.expensetracker.repository.ExpenseRepository;
import com.delfino.expensetracker.repository.ExpenseShareRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class ExpenseShareService {

    private final ExpenseShareRepository expenseShareRepository;
    private final ExpenseRepository expenseRepository;

    @Value("${app.sharing.default-ttl-days:30}")
    private long defaultTtlDays;

    public ExpenseShareService(ExpenseShareRepository expenseShareRepository,
                               ExpenseRepository expenseRepository) {
        this.expenseShareRepository = expenseShareRepository;
        this.expenseRepository = expenseRepository;
    }

    @Transactional
    public ExpenseShare createShare(String expenseUrlId, long userId, Duration ttl) {
        Expense expense = getOwnedExpense(expenseUrlId, userId);
        Optional<ExpenseShare> existing = findActiveShareForExpense(expense.getId());
        if (existing.isPresent()) {
            return existing.get();
        }

        LocalDateTime now = LocalDateTime.now();
        ExpenseShare share = new ExpenseShare();
        share.setExpenseId(expense.getId());
        share.setShareToken(UUID.randomUUID().toString());
        share.setCreatedAt(now);
        share.setExpiresAt(now.plus(resolveTtl(ttl)));
        share.setCreatedBy(userId);
        return expenseShareRepository.save(share);
    }

    @Transactional
    public Optional<ExpenseShare> revokeShare(String expenseUrlId, long userId) {
        Expense expense = getOwnedExpense(expenseUrlId, userId);
        Optional<ExpenseShare> activeShare = findActiveShareForExpense(expense.getId());
        activeShare.ifPresent(share -> {
            share.setRevokedAt(LocalDateTime.now());
            expenseShareRepository.save(share);
        });
        return activeShare;
    }

    public Optional<ExpenseShare> getShareStatus(String expenseUrlId, long userId) {
        Expense expense = getOwnedExpense(expenseUrlId, userId);
        return findActiveShareForExpense(expense.getId());
    }

    public Optional<ExpenseShare> resolveActiveShare(String shareToken) {
        return expenseShareRepository.findByShareTokenAndRevokedAtIsNull(shareToken)
                .filter(share -> share.getExpiresAt() != null)
                .filter(share -> share.getExpiresAt().isAfter(LocalDateTime.now()));
    }

    public ExpenseShare requireActiveShare(String shareToken) {
        return resolveActiveShare(shareToken)
                .orElseThrow(() -> new IllegalStateException("Share link is expired, revoked, or invalid"));
    }

    public Expense resolveSharedExpense(String shareToken) {
        return resolveSharedExpense(requireActiveShare(shareToken));
    }

    public Expense resolveSharedExpense(ExpenseShare share) {
        return expenseRepository.findById(share.getExpenseId())
                .orElseThrow(() -> new IllegalStateException("Expense not found"));
    }

    public Optional<ExpenseShare> findLatestShareForExpense(String expenseUrlId, long userId) {
        Expense expense = getOwnedExpense(expenseUrlId, userId);
        return expenseShareRepository.findByExpenseIdOrderByCreatedAtDesc(expense.getId()).stream()
                .findFirst();
    }

    public Optional<String> resolveSharedReceiptPath(String shareToken, String filename) {
        Expense expense = resolveSharedExpense(shareToken);
        String imagePath = expense.getImagePath();
        if (imagePath != null && imagePath.replace('\\', '/').endsWith("/" + filename)) {
            return Optional.of(imagePath);
        }
        List<String> attachments = expense.getAttachments();
        if (attachments == null) {
            return Optional.empty();
        }
        return attachments.stream()
                .filter(path -> path != null && path.replace('\\', '/').endsWith("/" + filename))
                .findFirst();
    }

    private Optional<ExpenseShare> findActiveShareForExpense(long expenseId) {
        return expenseShareRepository.findByExpenseIdAndRevokedAtIsNullOrderByCreatedAtDesc(expenseId).stream()
                .filter(share -> share.getExpiresAt() != null)
                .filter(share -> share.getExpiresAt().isAfter(LocalDateTime.now()))
                .findFirst();
    }

    private Expense getOwnedExpense(String expenseUrlId, long userId) {
        Expense expense = expenseRepository.findByUrlId(expenseUrlId)
                .orElseThrow(() -> new IllegalStateException("Expense not found"));
        if (expense.getUserId() != userId) {
            throw new AuthorizationDeniedException("Not authorized");
        }
        return expense;
    }

    private Duration resolveTtl(Duration requestedTtl) {
        if (requestedTtl != null && !requestedTtl.isNegative() && !requestedTtl.isZero()) {
            Duration maxTtl = Duration.ofDays(defaultTtlDays);
            return requestedTtl.compareTo(maxTtl) > 0 ? maxTtl : requestedTtl;
        }
        return Duration.ofDays(defaultTtlDays);
    }
}



