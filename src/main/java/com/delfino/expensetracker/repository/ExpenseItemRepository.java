package com.delfino.expensetracker.repository;

import com.delfino.expensetracker.model.ExpenseItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;


@Repository
public interface ExpenseItemRepository extends JpaRepository<ExpenseItem, Long> {

    List<ExpenseItem> findByExpenseIdAndDeletedFalse(Long expenseId);

    List<ExpenseItem> findByExpenseId(Long expenseId);

    /** Batch lookup — fetch all non-deleted items for many expenses in a single query. */
    List<ExpenseItem> findByExpenseIdInAndDeletedFalse(Collection<Long> expenseIds);

    @Modifying
    @Query("UPDATE ExpenseItem i SET i.deleted = true WHERE i.expenseId = :expenseId")
    void softDeleteByExpenseId(Long expenseId);

    @Modifying
    @Query("UPDATE ExpenseItem i SET i.deleted = false WHERE i.expenseId = :expenseId")
    void restoreByExpenseId(Long expenseId);
}
