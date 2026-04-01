package com.delfino.expensetracker.repository;

import com.delfino.expensetracker.model.Expense;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ExpenseRepository extends JpaRepository<Expense, UUID> {

    List<Expense> findByUserIdAndDeletedFalse(UUID userId);

    List<Expense> findByUserId(UUID userId);
}
