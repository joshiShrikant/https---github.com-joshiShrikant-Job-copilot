package com.eurojobcopilot.modules.assistant.ai;

import java.util.List;

public record ValidatedAssistantPayload(
        String assistantTitle,
        String assistantContent,
        List<String> highlights
) {
}

