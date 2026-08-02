package com.delfino.expensetracker.dto.user;

import java.util.List;

public record BulkPurgeRequest(
        List<String> expenseIds  // null or empty = purge all trashed expenses
) {}

