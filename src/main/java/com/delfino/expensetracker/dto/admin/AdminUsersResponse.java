package com.delfino.expensetracker.dto.admin;

import java.util.List;

public record AdminUsersResponse(
        String monthYear,
        List<AdminUserSummaryResponse> users) {
}

