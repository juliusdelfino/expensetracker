package com.delfino.expensetracker.dto.share;

import java.time.LocalDateTime;

public record ExpenseShareStatusResponse(
        boolean active,
        String shareToken,
        String shareUrl,
        LocalDateTime expiresAt,
        LocalDateTime revokedAt) {
}
