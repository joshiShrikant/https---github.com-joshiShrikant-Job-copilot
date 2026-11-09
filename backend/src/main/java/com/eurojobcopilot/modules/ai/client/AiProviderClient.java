package com.eurojobcopilot.modules.ai.client;

public interface AiProviderClient {

    String getProviderName();

    boolean isConfigured();

    String getConfiguredModel();

    AiCompletionResult generateStructuredJson(AiJsonPromptRequest request);
}

