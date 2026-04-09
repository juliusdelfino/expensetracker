package com.delfino.expensetracker.dto.expense;

import com.delfino.expensetracker.model.Expense;
import com.delfino.expensetracker.model.ExpenseItem;
import com.delfino.expensetracker.model.Store;

import java.util.List;

public record ExpenseDetailResponse(
        Expense expense,
        List<ExpenseItem> items,
        Store store,
        boolean isOwner) {}
