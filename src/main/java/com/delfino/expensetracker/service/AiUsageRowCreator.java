package com.delfino.expensetracker.service;

import com.delfino.expensetracker.model.AiUsage;
import com.delfino.expensetracker.model.AiUsageType;
import com.delfino.expensetracker.repository.AiUsageRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.YearMonth;

/**
 * Isolated component for creating AiUsage rows in a REQUIRES_NEW transaction.
 * If two threads race to insert the same row, the loser's REQUIRES_NEW
 * transaction rolls back cleanly and DataIntegrityViolationException propagates
 * to the caller, which can then re-fetch the row that the winner committed.
 */
@Component
public class AiUsageRowCreator {

    private final AiUsageRepository aiUsageRepository;

    public AiUsageRowCreator(AiUsageRepository aiUsageRepository) {
        this.aiUsageRepository = aiUsageRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public AiUsage tryCreate(long userId, AiUsageType type, YearMonth month, int quota) {
        AiUsage usage = new AiUsage();
        usage.setUserId(userId);
        usage.setType(type);
        usage.setMonthYear(month.toString());
        usage.setUsageCount(0);
        usage.setQuota(quota);
        return aiUsageRepository.saveAndFlush(usage);
    }
}
