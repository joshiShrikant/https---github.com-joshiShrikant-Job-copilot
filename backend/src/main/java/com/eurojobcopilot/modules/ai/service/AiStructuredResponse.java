package com.eurojobcopilot.modules.ai.service;

public record AiStructuredResponse<T>(
        String provider,
        String model,
        T payload,
        String normalizedJson,
        Integer promptTokens,
        Integer completionTokens
) {
}

