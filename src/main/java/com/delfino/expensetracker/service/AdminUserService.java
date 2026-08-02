package com.delfino.expensetracker.service;

import com.delfino.expensetracker.config.AiModelDefinition;
import com.delfino.expensetracker.dto.admin.AdminAiUsageOverviewResponse;
import com.delfino.expensetracker.dto.admin.AdminUserSummaryResponse;
import com.delfino.expensetracker.dto.admin.AdminUsageBreakdownResponse;
import com.delfino.expensetracker.dto.admin.AdminUsersResponse;
import com.delfino.expensetracker.dto.ai.AiUsageStatusResponse;
import com.delfino.expensetracker.exception.ResourceNotFoundException;
import com.delfino.expensetracker.model.User;
import com.delfino.expensetracker.model.UserRole;
import com.delfino.expensetracker.repository.UserRepository;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class AdminUserService {

    private static final Logger log = LoggerFactory.getLogger(AdminUserService.class);
    private static final double NEAR_QUOTA_THRESHOLD = 0.8d;

    private final UserRepository userRepository;
    private final UserAiSettingsService userAiSettingsService;
    private final AiUsageService aiUsageService;
    private final MeterRegistry meterRegistry;

    public AdminUserService(UserRepository userRepository,
                            UserAiSettingsService userAiSettingsService,
                            AiUsageService aiUsageService,
                            MeterRegistry meterRegistry) {
        this.userRepository = userRepository;
        this.userAiSettingsService = userAiSettingsService;
        this.aiUsageService = aiUsageService;
        this.meterRegistry = meterRegistry;
    }

    @Transactional(readOnly = true)
    public AdminUsersResponse getUsers(YearMonth month) {
        List<AdminUserSummaryResponse> users = userRepository.findAll(Sort.by(Sort.Direction.ASC, "username")).stream()
                .map(user -> toSummary(user, month))
                .toList();
        return new AdminUsersResponse(month.toString(), users);
    }

    @Transactional(readOnly = true)
    public AdminUserSummaryResponse getUser(long userId, YearMonth month) {
        return toSummary(requireUser(userId), month);
    }

    @Transactional(readOnly = true)
    public AdminAiUsageOverviewResponse getUsageOverview(YearMonth month) {
        List<AdminUserSummaryResponse> users = getUsers(month).users();
        int totalUsers = users.size();
        int adminUsers = (int) users.stream().filter(user -> UserRole.ADMIN.name().equals(user.role())).count();
        int totalChatUsage = users.stream().mapToInt(user -> user.chat().usageCount()).sum();
        int totalChatQuota = users.stream().mapToInt(user -> user.chat().quota()).sum();
        int totalOcrUsage = users.stream().mapToInt(user -> user.ocr().usageCount()).sum();
        int totalOcrQuota = users.stream().mapToInt(user -> user.ocr().quota()).sum();
        long chatNearQuotaUsers = users.stream().filter(user -> isNearQuota(user.chat().usageCount(), user.chat().quota(), user.chatAllowed())).count();
        long ocrNearQuotaUsers = users.stream().filter(user -> isNearQuota(user.ocr().usageCount(), user.ocr().quota(), user.ocrAllowed())).count();
        long chatExceededUsers = users.stream().filter(user -> !user.chatAllowed()).count();
        long ocrExceededUsers = users.stream().filter(user -> !user.ocrAllowed()).count();
        List<AdminUsageBreakdownResponse> chatByModel = buildBreakdown(users, true);
        List<AdminUsageBreakdownResponse> ocrByModel = buildBreakdown(users, false);

        return new AdminAiUsageOverviewResponse(
                month.toString(),
                totalUsers,
                adminUsers,
                totalChatUsage,
                totalChatQuota,
                totalOcrUsage,
                totalOcrQuota,
                chatNearQuotaUsers,
                ocrNearQuotaUsers,
                chatExceededUsers,
                ocrExceededUsers,
                chatByModel,
                ocrByModel);
    }

    @Transactional
    public AdminUserSummaryResponse updateUserRole(long userId, String roleValue, long actingUserId) {
        if (roleValue == null || roleValue.isBlank()) {
            throw new IllegalArgumentException("Role is required");
        }

        User targetUser = requireUser(userId);
        UserRole targetRole;
        try {
            targetRole = UserRole.valueOf(roleValue.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Unsupported role: " + roleValue.trim());
        }

        if (targetUser.getRole() == UserRole.ADMIN && targetRole != UserRole.ADMIN
                && userRepository.countByRole(UserRole.ADMIN) <= 1) {
            throw new IllegalArgumentException("You cannot remove the last admin account");
        }

        UserRole previousRole = targetUser.getRole();
        targetUser.setRole(targetRole);
        targetUser.setUpdatedAt(LocalDateTime.now());
        User saved = userRepository.save(targetUser);

        log.info("Admin {} updated user {} role from {} to {}",
                actingUserId, saved.getId(), previousRole, saved.getRole());
        meterRegistry.counter("app.ai.admin.actions", "action", "update_role", "outcome", "success").increment();

        return toSummary(saved, YearMonth.now());
    }

    @Transactional
    public AdminUserSummaryResponse updateUserAiModel(long userId, String aiModel, long actingUserId) {
        User targetUser = requireUser(userId);
        String previousModel = targetUser.getAiModel();
        userAiSettingsService.validateAiModelOverride(aiModel);
        targetUser.setAiModel(aiModel);
        targetUser.setUpdatedAt(LocalDateTime.now());
        User saved = userRepository.save(targetUser);

        log.info("Admin {} updated user {} aiModel from {} to {}",
                actingUserId, saved.getId(), previousModel, saved.getAiModel());
        meterRegistry.counter("app.ai.admin.actions", "action", "update_ai_model", "outcome", "success").increment();

        return toSummary(saved, YearMonth.now());
    }

    private List<AdminUsageBreakdownResponse> buildBreakdown(List<AdminUserSummaryResponse> users, boolean chat) {
        Map<String, BreakdownAccumulator> grouped = new LinkedHashMap<>();

        for (AdminUserSummaryResponse user : users) {
            String provider = chat ? user.effectiveChatProvider() : user.effectiveOcrProvider();
            String modelId = chat ? user.effectiveChatModel() : user.effectiveOcrModel();
            String key = provider + "::" + modelId;
            BreakdownAccumulator accumulator = grouped.computeIfAbsent(key,
                    ignored -> new BreakdownAccumulator(provider, modelId, userAiSettingsService.getModelLabel(modelId)));
            accumulator.userCount++;
            accumulator.usageCount += chat ? user.chat().usageCount() : user.ocr().usageCount();
            accumulator.quota += chat ? user.chat().quota() : user.ocr().quota();
            boolean allowed = chat ? user.chatAllowed() : user.ocrAllowed();
            int usageCount = chat ? user.chat().usageCount() : user.ocr().usageCount();
            int quota = chat ? user.chat().quota() : user.ocr().quota();
            if (!allowed) {
                accumulator.exceededUsers++;
            } else if (isNearQuota(usageCount, quota, true)) {
                accumulator.nearQuotaUsers++;
            }
        }

        List<AdminUsageBreakdownResponse> breakdown = new ArrayList<>();
        grouped.values().forEach(value -> breakdown.add(value.toResponse()));
        return breakdown;
    }

    private boolean isNearQuota(int usageCount, int quota, boolean allowed) {
        return allowed && quota > 0 && ((double) usageCount / (double) quota) >= NEAR_QUOTA_THRESHOLD;
    }

    private static final class BreakdownAccumulator {
        private final String provider;
        private final String modelId;
        private final String modelLabel;
        private int userCount;
        private int usageCount;
        private int quota;
        private long exceededUsers;
        private long nearQuotaUsers;

        private BreakdownAccumulator(String provider, String modelId, String modelLabel) {
            this.provider = provider;
            this.modelId = modelId;
            this.modelLabel = modelLabel;
        }

        private AdminUsageBreakdownResponse toResponse() {
            return new AdminUsageBreakdownResponse(provider, modelId, modelLabel, userCount, usageCount, quota, exceededUsers, nearQuotaUsers);
        }
    }

    private User requireUser(long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));
    }

    private AdminUserSummaryResponse toSummary(User user, YearMonth month) {
        AiUsageStatusResponse status = aiUsageService.getStatusSnapshot(user.getId(), month);
        AiModelDefinition effectiveChatModel = userAiSettingsService.getEffectiveChatModel(user);
        AiModelDefinition effectiveOcrModel = userAiSettingsService.getEffectiveOcrModel(user);

        return new AdminUserSummaryResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getRole().name(),
                user.getAiModel(),
                effectiveChatModel.getId(),
                effectiveChatModel.getProvider().name(),
                effectiveOcrModel.getId(),
                effectiveOcrModel.getProvider().name(),
                status.chat(),
                status.ocr(),
                status.chatAllowed(),
                status.ocrAllowed());
    }
}



