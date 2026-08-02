package com.delfino.expensetracker.service;

import com.delfino.expensetracker.config.AiModelDefinition;
import com.delfino.expensetracker.config.AiProperties;
import com.delfino.expensetracker.config.AiProviderType;
import com.delfino.expensetracker.exception.AiModelNotAllowedException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserAiSettingsServiceTest {

    @Test
    void getAssignableModels_onlyReturnsModelsThatSupportBothChatAndOcr() {
        UserAiSettingsService service = new UserAiSettingsService(buildProperties());

        assertThat(service.getAssignableModels())
                .extracting(AiModelDefinition::getId)
                .containsExactly("full-model");
    }

    @Test
    void validateAiModelOverride_rejectsUnknownModel() {
        UserAiSettingsService service = new UserAiSettingsService(buildProperties());

        assertThatThrownBy(() -> service.validateAiModelOverride("missing-model"))
                .isInstanceOf(AiModelNotAllowedException.class)
                .hasMessage("Unsupported AI model: missing-model");
    }

    @Test
    void validateAiModelOverride_rejectsModelsThatDoNotSupportBothFeatures() {
        UserAiSettingsService service = new UserAiSettingsService(buildProperties());

        assertThatThrownBy(() -> service.validateAiModelOverride("chat-only"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Selected AI model must support both chat and OCR: chat-only");
    }

    private AiProperties buildProperties() {
        AiProperties properties = new AiProperties();
        properties.getDefaults().setChatModel("full-model");
        properties.getDefaults().setOcrModel("full-model");
        properties.setModels(List.of(
                model("full-model", true, true),
                model("chat-only", true, false),
                model("ocr-only", false, true)
        ));
        return properties;
    }

    private AiModelDefinition model(String id, boolean supportsChat, boolean supportsOcr) {
        AiModelDefinition model = new AiModelDefinition();
        model.setId(id);
        model.setLabel(id);
        model.setProvider(AiProviderType.OLLAMA);
        model.setSupportsChat(supportsChat);
        model.setSupportsOcr(supportsOcr);
        return model;
    }
}

