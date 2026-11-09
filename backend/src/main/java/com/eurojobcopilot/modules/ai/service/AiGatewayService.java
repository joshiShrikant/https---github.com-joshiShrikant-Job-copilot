package com.eurojobcopilot.modules.ai.service;

import com.eurojobcopilot.modules.ai.client.AiJsonPromptRequest;

public interface AiGatewayService {

    <T> AiStructuredResponse<T> executeStructuredJsonPrompt(AiJsonPromptRequest request, Class<T> targetType);
}

