package com.delfino.expensetracker.repository;

import com.delfino.expensetracker.model.ExpenseShare;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ExpenseShareRepository extends JpaRepository<ExpenseShare, Long> {

    Optional<ExpenseShare> findByShareTokenAndRevokedAtIsNull(String shareToken);

    List<ExpenseShare> findByExpenseIdAndRevokedAtIsNullOrderByCreatedAtDesc(long expenseId);

    List<ExpenseShare> findByExpenseIdOrderByCreatedAtDesc(long expenseId);

    List<ExpenseShare> findByRevokedAtBefore(LocalDateTime cutoff);

    List<ExpenseShare> findByExpiresAtBefore(LocalDateTime cutoff);
}

