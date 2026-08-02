package com.delfino.expensetracker.service;

import com.delfino.expensetracker.config.AiModelDefinition;
import com.delfino.expensetracker.config.AiProperties;
import com.delfino.expensetracker.exception.AiModelNotAllowedException;
import com.delfino.expensetracker.model.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
public class UserAiSettingsService {

    private static final Logger log = LoggerFactory.getLogger(UserAiSettingsService.class);

    private final AiProperties aiProperties;

    public UserAiSettingsService(AiProperties aiProperties) {
        this.aiProperties = aiProperties;
    }

    public void validateAiModelOverride(String aiModel) {
        if (!StringUtils.hasText(aiModel)) {
            return;
        }
        AiModelDefinition modelDefinition = aiProperties.findModel(aiModel.trim())
                .orElseThrow(() -> new AiModelNotAllowedException(aiModel.trim()));
        if (!supportsAccountOverride(modelDefinition)) {
            throw new IllegalArgumentException("Selected AI model must support both chat and OCR: " + aiModel.trim());
        }
    }

    public List<AiModelDefinition> getAvailableModels() {
        return aiProperties.getModels();
    }

    public List<AiModelDefinition> getAssignableModels() {
        return aiProperties.getModels().stream()
                .filter(this::supportsAccountOverride)
                .toList();
    }

    public String getDefaultChatModelId() {
        return aiProperties.getDefaults().getChatModel();
    }

    public String getDefaultOcrModelId() {
        return aiProperties.getDefaults().getOcrModel();
    }

    public String getModelLabel(String modelId) {
        if (!StringUtils.hasText(modelId)) {
            return "Default";
        }
        return aiProperties.findModel(modelId.trim())
                .map(AiModelDefinition::getLabel)
                .orElse(modelId.trim());
    }

    public AiModelDefinition getEffectiveChatModel(User user) {
        return resolveEffectiveModel(user, true);
    }

    public AiModelDefinition getEffectiveOcrModel(User user) {
        return resolveEffectiveModel(user, false);
    }

    private AiModelDefinition resolveEffectiveModel(User user, boolean chat) {
        String requestedModelId = user.getAiModel();
        if (StringUtils.hasText(requestedModelId)) {
            AiModelDefinition override = aiProperties.findModel(requestedModelId.trim()).orElse(null);
            if (override != null && supportsFeature(override, chat)) {
                return override;
            }
            log.warn("User {} has unavailable AI model override '{}' for {}. Falling back to configured default.",
                    user.getId(),
                    requestedModelId,
                    chat ? "chat" : "ocr");
        }

        return aiProperties.findModel(chat ? aiProperties.getDefaults().getChatModel() : aiProperties.getDefaults().getOcrModel())
                .filter(model -> supportsFeature(model, chat))
                .orElseThrow(() -> new IllegalStateException("No default AI model configured for " + (chat ? "chat" : "ocr")));
    }

    private boolean supportsFeature(AiModelDefinition model, boolean chat) {
        return chat ? model.isSupportsChat() : model.isSupportsOcr();
    }

    private boolean supportsAccountOverride(AiModelDefinition model) {
        return model.isSupportsChat() && model.isSupportsOcr();
    }
}

