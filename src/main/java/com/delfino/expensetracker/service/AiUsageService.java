package com.delfino.expensetracker.service;

import com.delfino.expensetracker.dto.ai.AiUsageLineDto;
import com.delfino.expensetracker.dto.ai.AiUsageStatusResponse;
import com.delfino.expensetracker.exception.AiQuotaExceededException;
import com.delfino.expensetracker.model.AiUsage;
import com.delfino.expensetracker.model.AiUsageType;
import com.delfino.expensetracker.model.User;
import com.delfino.expensetracker.repository.AiUsageRepository;
import com.delfino.expensetracker.repository.UserRepository;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

@Service
public class AiUsageService {

    private final AiUsageRepository aiUsageRepository;
    private final UserRepository userRepository;
    private final UserAiSettingsService userAiSettingsService;
    private final MeterRegistry meterRegistry;
    private final AiUsageRowCreator aiUsageRowCreator;

    @Value("${app.ai.quotas.chat-monthly:200}")
    private int chatMonthlyQuota;

    @Value("${app.ai.quotas.ocr-monthly:100}")
    private int ocrMonthlyQuota;

    public AiUsageService(AiUsageRepository aiUsageRepository,
                          UserRepository userRepository,
                          UserAiSettingsService userAiSettingsService,
                          MeterRegistry meterRegistry,
                          AiUsageRowCreator aiUsageRowCreator) {
        this.aiUsageRepository = aiUsageRepository;
        this.userRepository = userRepository;
        this.userAiSettingsService = userAiSettingsService;
        this.meterRegistry = meterRegistry;
        this.aiUsageRowCreator = aiUsageRowCreator;
    }

    @Transactional
    public AiUsageStatusResponse getCurrentStatus(long userId) {
        return getStatus(userId, YearMonth.now());
    }

    @Transactional
    public AiUsageStatusResponse getStatus(long userId, YearMonth month) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalStateException("User not found: " + userId));

        AiUsage chatUsage = ensureMonthRow(userId, AiUsageType.CHAT, month);
        AiUsage ocrUsage = ensureMonthRow(userId, AiUsageType.OCR, month);
        return buildStatusResponse(user, month, chatUsage, ocrUsage);
    }

    @Transactional(readOnly = true)
    public AiUsageStatusResponse getStatusSnapshot(long userId, YearMonth month) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalStateException("User not found: " + userId));

        List<AiUsage> usages = aiUsageRepository.findByUserIdAndMonthYear(userId, month.toString());
        AiUsage chatUsage = null;
        AiUsage ocrUsage = null;
        for (AiUsage usage : usages) {
            if (usage.getType() == AiUsageType.CHAT) {
                chatUsage = usage;
            } else if (usage.getType() == AiUsageType.OCR) {
                ocrUsage = usage;
            }
        }

        return buildStatusResponse(user, month, chatUsage, ocrUsage);
    }

    @Transactional
    public QuotaCheckResult checkQuota(long userId, AiUsageType type) {
        return checkQuota(userId, type, 1);
    }

    @Transactional
    public QuotaCheckResult checkQuota(long userId, AiUsageType type, int requestedUnits) {
        AiUsage usage = ensureMonthRow(userId, type, YearMonth.now());
        int remaining = Math.max(0, usage.getQuota() - usage.getUsageCount());
        return new QuotaCheckResult(type, usage.getUsageCount(), usage.getQuota(), remaining, remaining >= requestedUnits, requestedUnits);
    }

    @Transactional
    public AiUsage consume(long userId, AiUsageType type) {
        return consume(userId, type, 1);
    }

    @Transactional
    public AiUsage consume(long userId, AiUsageType type, int units) {
        if (units <= 0) {
            throw new IllegalArgumentException("Usage units must be greater than zero");
        }

        AiUsage usage = ensureMonthRowForUpdate(userId, type, YearMonth.now());
        if (usage.getUsageCount() + units > usage.getQuota()) {
            meterRegistry.counter("app.ai.usage.denied", "type", type.name().toLowerCase()).increment();
            logQuotaEvent("quota_denied", userId, type, usage.getUsageCount(), usage.getQuota(), units, usage.getMonthYear());
            throw new AiQuotaExceededException(type, usage.getUsageCount(), usage.getQuota(), units);
        }
        usage.setUsageCount(usage.getUsageCount() + units);
        AiUsage saved = aiUsageRepository.save(usage);
        meterRegistry.counter("app.ai.usage.consumed", "type", type.name().toLowerCase()).increment(units);
        logQuotaEvent("consumed", userId, type, saved.getUsageCount(), saved.getQuota(), units, saved.getMonthYear());
        return saved;
    }

    private AiUsage ensureMonthRow(long userId, AiUsageType type, YearMonth month) {
        return aiUsageRepository.findByUserIdAndTypeAndMonthYear(userId, type, month.toString())
                .orElseGet(() -> createUsageRow(userId, type, month));
    }

    private AiUsage ensureMonthRowForUpdate(long userId, AiUsageType type, YearMonth month) {
        Optional<AiUsage> existing = aiUsageRepository.findForUpdate(userId, type, month.toString());
        if (existing.isPresent()) {
            return existing.get();
        }
        // Row doesn't exist yet — try to create it in an isolated REQUIRES_NEW transaction.
        try {
            aiUsageRowCreator.tryCreate(userId, type, month, getQuota(type));
        } catch (DataIntegrityViolationException ex) {
            // A concurrent thread created the row first — that's fine, fall through.
        }
        // Always re-fetch with a pessimistic lock so the outer transaction owns the row.
        return aiUsageRepository.findForUpdate(userId, type, month.toString())
                .orElseThrow(() -> new IllegalStateException("Failed to ensure usage row for " + type));
    }

    private AiUsage createUsageRow(long userId, AiUsageType type, YearMonth month) {
        try {
            return aiUsageRowCreator.tryCreate(userId, type, month, getQuota(type));
        } catch (DataIntegrityViolationException ex) {
            return aiUsageRepository.findByUserIdAndTypeAndMonthYear(userId, type, month.toString())
                    .orElseThrow(() -> ex);
        }
    }

    private int getQuota(AiUsageType type) {
        return type == AiUsageType.CHAT ? chatMonthlyQuota : ocrMonthlyQuota;
    }

    private AiUsageStatusResponse buildStatusResponse(User user, YearMonth month, AiUsage chatUsage, AiUsage ocrUsage) {
        AiUsageLineDto chatLine = toLine(AiUsageType.CHAT, chatUsage);
        AiUsageLineDto ocrLine = toLine(AiUsageType.OCR, ocrUsage);

        return new AiUsageStatusResponse(
                month.toString(),
                user.getAiModel(),
                userAiSettingsService.getEffectiveChatModel(user).getId(),
                userAiSettingsService.getEffectiveOcrModel(user).getId(),
                chatLine,
                ocrLine,
                chatLine.allowed(),
                ocrLine.allowed());
    }

    private AiUsageLineDto toLine(AiUsageType type, AiUsage usage) {
        int usageCount = usage != null ? usage.getUsageCount() : 0;
        int quota = usage != null ? usage.getQuota() : getQuota(type);
        int remaining = Math.max(0, quota - usageCount);
        return new AiUsageLineDto(
                type.name(),
                usageCount,
                quota,
                remaining,
                remaining > 0);
    }

    public record QuotaCheckResult(
            AiUsageType type,
            int usageCount,
            int quota,
            int remaining,
            boolean allowed,
            int requestedUnits) {
    }

    private void logQuotaEvent(String event, long userId, AiUsageType type, int usageCount, int quota, int requestedUnits,
                               String monthYear) {
        org.slf4j.LoggerFactory.getLogger(AiUsageService.class)
                .info("AI usage event={} userId={} type={} month={} usageCount={} quota={} requestedUnits={} remaining={}",
                        event, userId, type, monthYear, usageCount, quota, requestedUnits, Math.max(0, quota - usageCount));
    }
}


