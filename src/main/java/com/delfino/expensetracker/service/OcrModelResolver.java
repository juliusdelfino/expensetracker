package com.delfino.expensetracker.service;

import com.delfino.expensetracker.config.AiModelDefinition;
import com.delfino.expensetracker.config.AiProperties;
import com.delfino.expensetracker.config.AiProviderType;
import com.delfino.expensetracker.model.User;
import com.delfino.expensetracker.service.ocr.OcrProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class OcrModelResolver {

	private final UserAiSettingsService userAiSettingsService;
	private final AiProperties aiProperties;
	private final OcrProvider ollamaOcrProvider;
	private final OcrProvider openAiOcrProvider;

	@Value("${ocr.api.api-key:}")
	private String ocrApiKey;

	@Value("${ocr.api.disable-thinking:false}")
	private boolean ocrDisableThinking;

	@Value("${ocr.api.use-tools:true}")
	private boolean ocrUseTools;

	@Value("${ocr.api.prompt:Parse this receipt image and return a JSON object. Return ONLY valid JSON.}")
	private String ocrPrompt;

	public OcrModelResolver(UserAiSettingsService userAiSettingsService,
							AiProperties aiProperties,
							@Qualifier("ollamaOcrProvider") OcrProvider ollamaOcrProvider,
							@Qualifier("openAiOcrProvider") OcrProvider openAiOcrProvider) {
		this.userAiSettingsService = userAiSettingsService;
		this.aiProperties = aiProperties;
		this.ollamaOcrProvider = ollamaOcrProvider;
		this.openAiOcrProvider = openAiOcrProvider;
	}

	public ResolvedOcrModel resolveForUser(User user) {
		AiModelDefinition modelDefinition = userAiSettingsService.getEffectiveOcrModel(user);
		return switch (modelDefinition.getProvider()) {
			case OLLAMA -> new ResolvedOcrModel(
					modelDefinition,
					ollamaOcrProvider,
					aiProperties.getProviders().getOllama().getOcrUrl(),
					ocrApiKey,
					ocrDisableThinking,
					ocrUseTools,
					ocrPrompt
			);
			case OPENAI -> new ResolvedOcrModel(
					modelDefinition,
					openAiOcrProvider,
					aiProperties.getProviders().getOpenai().getOcrUrl(),
					ocrApiKey,
					ocrDisableThinking,
					ocrUseTools,
					ocrPrompt
			);
		};
	}

	public record ResolvedOcrModel(
			AiModelDefinition modelDefinition,
			OcrProvider ocrProvider,
			String apiUrl,
			String apiKey,
			boolean disableThinking,
			boolean useTools,
			String prompt) {

		public AiProviderType provider() {
			return modelDefinition.getProvider();
		}

		public String modelId() {
			return modelDefinition.getId();
		}
	}
}

