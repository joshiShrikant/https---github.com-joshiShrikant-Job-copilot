package com.eurojobcopilot.modules.ai.client;

public record AiCompletionResult(
        String provider,
        String model,
        String rawContent,
        Integer promptTokens,
        Integer completionTokens
) {
}

