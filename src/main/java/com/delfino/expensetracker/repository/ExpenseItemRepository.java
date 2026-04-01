package com.delfino.expensetracker.repository;

import com.delfino.expensetracker.model.ExpenseItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ExpenseItemRepository extends JpaRepository<ExpenseItem, UUID> {

    List<ExpenseItem> findByExpenseIdAndDeletedFalse(UUID expenseId);

    List<ExpenseItem> findByExpenseId(UUID expenseId);

    @Modifying
    @Query("UPDATE ExpenseItem i SET i.deleted = true WHERE i.expenseId = :expenseId")
    void softDeleteByExpenseId(UUID expenseId);

    @Modifying
    @Query("UPDATE ExpenseItem i SET i.deleted = false WHERE i.expenseId = :expenseId")
    void restoreByExpenseId(UUID expenseId);
}
