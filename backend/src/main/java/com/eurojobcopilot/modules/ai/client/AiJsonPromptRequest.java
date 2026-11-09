package com.eurojobcopilot.modules.ai.client;

public record AiJsonPromptRequest(
        String taskName,
        String systemPrompt,
        String userPrompt,
        Double temperature,
        Integer maxOutputTokens
) {
}

