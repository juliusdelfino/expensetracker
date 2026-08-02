package com.delfino.expensetracker.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Component
@ConfigurationProperties(prefix = "app.ai")
public class AiProperties {

    private Defaults defaults = new Defaults();
    private Providers providers = new Providers();
    private List<AiModelDefinition> models = new ArrayList<>();

    public Defaults getDefaults() {
        return defaults;
    }

    public void setDefaults(Defaults defaults) {
        this.defaults = defaults;
    }

    public Providers getProviders() {
        return providers;
    }

    public void setProviders(Providers providers) {
        this.providers = providers;
    }

    public List<AiModelDefinition> getModels() {
        return models;
    }

    public void setModels(List<AiModelDefinition> models) {
        this.models = models;
    }

    public Optional<AiModelDefinition> findModel(String modelId) {
        if (!StringUtils.hasText(modelId)) {
            return Optional.empty();
        }
        return models.stream()
                .filter(model -> modelId.equals(model.getId()))
                .findFirst();
    }

    public static class Defaults {
        private String chatModel;
        private String ocrModel;

        public String getChatModel() {
            return chatModel;
        }

        public void setChatModel(String chatModel) {
            this.chatModel = chatModel;
        }

        public String getOcrModel() {
            return ocrModel;
        }

        public void setOcrModel(String ocrModel) {
            this.ocrModel = ocrModel;
        }
    }

    public static class Providers {
        private OcrProviderSettings ollama = new OcrProviderSettings();
        private OcrProviderSettings openai = new OcrProviderSettings();

        public OcrProviderSettings getOllama() {
            return ollama;
        }

        public void setOllama(OcrProviderSettings ollama) {
            this.ollama = ollama;
        }

        public OcrProviderSettings getOpenai() {
            return openai;
        }

        public void setOpenai(OcrProviderSettings openai) {
            this.openai = openai;
        }
    }

    public static class OcrProviderSettings {
        private String ocrUrl;

        public String getOcrUrl() {
            return ocrUrl;
        }

        public void setOcrUrl(String ocrUrl) {
            this.ocrUrl = ocrUrl;
        }
    }
}

