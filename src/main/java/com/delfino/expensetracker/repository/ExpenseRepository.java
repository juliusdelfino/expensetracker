package com.delfino.expensetracker.repository;

import com.delfino.expensetracker.model.Expense;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;


@Repository
public interface ExpenseRepository extends JpaRepository<Expense, Long> {

    List<Expense> findByUserIdAndDeletedFalse(Long userId);

    List<Expense> findByUserId(Long userId);

    Optional<Expense> findByUrlId(String urlId);
}
