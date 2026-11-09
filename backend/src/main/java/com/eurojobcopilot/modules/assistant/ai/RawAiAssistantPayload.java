package com.eurojobcopilot.modules.assistant.ai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record RawAiAssistantPayload(
        String assistantTitle,
        String assistantContent,
        List<String> highlights,
        List<String> groundedFacts,
        List<String> warnings
) {
}

