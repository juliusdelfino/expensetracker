package com.delfino.expensetracker.exception;

import com.delfino.expensetracker.model.AiUsageType;

public class AiQuotaExceededException extends RuntimeException {

    private final AiUsageType type;
    private final int usageCount;
    private final int quota;
    private final int requestedUnits;

    public AiQuotaExceededException(AiUsageType type, int usageCount, int quota, int requestedUnits) {
        super("Monthly " + type.name() + " quota exceeded");
        this.type = type;
        this.usageCount = usageCount;
        this.quota = quota;
        this.requestedUnits = requestedUnits;
    }

    public AiUsageType getType() {
        return type;
    }

    public int getUsageCount() {
        return usageCount;
    }

    public int getQuota() {
        return quota;
    }

    public int getRequestedUnits() {
        return requestedUnits;
    }

    public int getRemaining() {
        return Math.max(0, quota - usageCount);
    }

    public String getCode() {
        return type == AiUsageType.CHAT ? "AI_CHAT_QUOTA_EXCEEDED" : "AI_OCR_QUOTA_EXCEEDED";
    }
}

