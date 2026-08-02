package com.delfino.expensetracker.controller;

import com.delfino.expensetracker.dto.auth.UserToken;
import com.delfino.expensetracker.dto.ai.AiModelOptionResponse;
import com.delfino.expensetracker.dto.ai.AiModelsResponse;
import com.delfino.expensetracker.dto.ai.AiUsageStatusResponse;
import com.delfino.expensetracker.dto.auth.UpdateProfileRequest;
import com.delfino.expensetracker.dto.common.ErrorResponse;
import com.delfino.expensetracker.dto.common.MessageResponse;
import com.delfino.expensetracker.dto.user.AccountSummaryResponse;
import com.delfino.expensetracker.exception.AiModelNotAllowedException;
import com.delfino.expensetracker.model.User;
import com.delfino.expensetracker.repository.ExpenseRepository;
import com.delfino.expensetracker.repository.UserRepository;
import com.delfino.expensetracker.service.AiUsageService;
import com.delfino.expensetracker.service.SupportedCurrencyService;
import com.delfino.expensetracker.service.UserAiSettingsService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/user")
public class UserController {

    private final UserRepository userRepository;
    private final ExpenseRepository expenseRepository;
    private final PasswordEncoder passwordEncoder;
    private final SupportedCurrencyService supportedCurrencyService;
    private final UserAiSettingsService userAiSettingsService;
    private final AiUsageService aiUsageService;

    public UserController(UserRepository userRepository, ExpenseRepository expenseRepository,
                          PasswordEncoder passwordEncoder,
                          SupportedCurrencyService supportedCurrencyService,
                          UserAiSettingsService userAiSettingsService,
                          AiUsageService aiUsageService) {
        this.userRepository = userRepository;
        this.expenseRepository = expenseRepository;
        this.passwordEncoder = passwordEncoder;
        this.supportedCurrencyService = supportedCurrencyService;
        this.userAiSettingsService = userAiSettingsService;
        this.aiUsageService = aiUsageService;
    }

    @GetMapping("/account/summary")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<AccountSummaryResponse> getAccountSummary(UserToken userToken) {
        long trashedCount = expenseRepository.countByUserIdAndDeletedTrue(userToken.getUserId());
        return ResponseEntity.ok(new AccountSummaryResponse(trashedCount));
    }

    @GetMapping("/ai/models")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<AiModelsResponse> getAiModels() {
        List<AiModelOptionResponse> models = userAiSettingsService.getAssignableModels().stream()
                .map(model -> new AiModelOptionResponse(
                        model.getId(),
                        model.getLabel(),
                        model.getProvider().name(),
                        model.isSupportsChat(),
                        model.isSupportsOcr()))
                .toList();

        return ResponseEntity.ok(new AiModelsResponse(
                userAiSettingsService.getDefaultChatModelId(),
                userAiSettingsService.getDefaultOcrModelId(),
                models));
    }

    @GetMapping({"/ai/status", "/ai/usage/current"})
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<AiUsageStatusResponse> getAiStatus(UserToken userToken) {
        return ResponseEntity.ok(aiUsageService.getCurrentStatus(userToken.getUserId()));
    }

    @PutMapping("/profile")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Object> updateProfile(@RequestBody UpdateProfileRequest body, UserToken userToken) {
        long userId = userToken.getUserId();

        User user = userRepository.findById(userId).orElse(null);
        if (user == null) return ResponseEntity.notFound().build();

        if (body.email() != null) user.setEmail(body.email());
        if (body.phoneNumber() != null) user.setPhoneNumber(body.phoneNumber());
        if (body.baseCurrency() != null) {
            String bc = body.baseCurrency();
            if (!supportedCurrencyService.isSupported(bc)) {
                return ResponseEntity.badRequest().body(new ErrorResponse("Unsupported base currency: " + bc));
            }
            user.setBaseCurrency(bc);
        }
        if (body.baseCity() != null) user.setBaseCity(body.baseCity());
        if (body.baseCountry() != null) user.setBaseCountry(body.baseCountry());
        if (body.aiModel() != null) {
            try {
                userAiSettingsService.validateAiModelOverride(body.aiModel());
            } catch (AiModelNotAllowedException e) {
                return ResponseEntity.badRequest().body(new ErrorResponse(e.getMessage(), "AI_MODEL_NOT_ALLOWED"));
            } catch (IllegalArgumentException e) {
                return ResponseEntity.badRequest().body(new ErrorResponse(e.getMessage(), "INVALID_REQUEST"));
            }
            user.setAiModel(body.aiModel());
        }
        if (body.password() != null && !body.password().isBlank()) {
            user.setPasswordHash(passwordEncoder.encode(body.password()));
        }
        user.setUpdatedAt(LocalDateTime.now());
        userRepository.save(user);

        return ResponseEntity.ok(new MessageResponse("Profile updated"));
    }
}
