package com.delfino.expensetracker.service;

import com.delfino.expensetracker.config.AiModelDefinition;
import com.delfino.expensetracker.config.AiProviderType;
import com.delfino.expensetracker.model.User;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

@Service
public class ChatModelResolver {

    private final UserAiSettingsService userAiSettingsService;
    private final OllamaChatModel ollamaChatModel;
    private final OpenAiChatModel openAiChatModel;

    public ChatModelResolver(UserAiSettingsService userAiSettingsService,
                             ObjectProvider<OllamaChatModel> ollamaChatModelProvider,
                             ObjectProvider<OpenAiChatModel> openAiChatModelProvider) {
        this.userAiSettingsService = userAiSettingsService;
        this.ollamaChatModel = ollamaChatModelProvider.getIfAvailable();
        this.openAiChatModel = openAiChatModelProvider.getIfAvailable();
    }

    public ResolvedChatModel resolveForUser(User user) {
        AiModelDefinition modelDefinition = userAiSettingsService.getEffectiveChatModel(user);
        ChatModel chatModel = switch (modelDefinition.getProvider()) {
            case OLLAMA -> requireChatModel(ollamaChatModel, AiProviderType.OLLAMA);
            case OPENAI -> requireChatModel(openAiChatModel, AiProviderType.OPENAI);
        };

        ChatOptions chatOptions = switch (modelDefinition.getProvider()) {
            case OLLAMA -> OllamaChatOptions.builder().model(modelDefinition.getId()).build();
            case OPENAI -> OpenAiChatOptions.builder().model(modelDefinition.getId()).build();
        };

        return new ResolvedChatModel(modelDefinition, chatModel, chatOptions);
    }

    private ChatModel requireChatModel(ChatModel chatModel, AiProviderType providerType) {
        if (chatModel == null) {
            throw new IllegalStateException("Chat provider is not configured: " + providerType);
        }
        return chatModel;
    }

    public record ResolvedChatModel(AiModelDefinition modelDefinition, ChatModel chatModel, ChatOptions chatOptions) {}
}



