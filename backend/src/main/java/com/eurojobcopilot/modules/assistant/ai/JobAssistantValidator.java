package com.eurojobcopilot.modules.assistant.ai;

import com.eurojobcopilot.modules.ai.exception.AiResponseValidationException;
import com.eurojobcopilot.modules.assistant.entity.AssistantActionType;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

@Component
public class JobAssistantValidator {

    private static final int MAX_HIGHLIGHTS = 8;

    public ValidatedAssistantPayload validateAndNormalize(
            RawAiAssistantPayload raw,
            AssistantActionType actionType
    ) {
        if (raw == null) {
            throw new AiResponseValidationException("AI assistant payload was empty.");
        }

        String content = raw.assistantContent() != null ? raw.assistantContent().trim() : "";
        if (content.length() < 20) {
            throw new AiResponseValidationException(
                    "AI assistant response must contain substantive guidance (at least 20 characters)."
            );
        }

        String title = raw.assistantTitle() != null && !raw.assistantTitle().isBlank()
                ? raw.assistantTitle().trim()
                : (actionType != null ? actionType.getLabel() : "Application Assistant Guidance");
        if (title.length() > 250) {
            title = title.substring(0, 247) + "...";
        }

        LinkedHashSet<String> highlights = new LinkedHashSet<>();
        if (raw.highlights() != null) {
            for (String item : raw.highlights()) {
                if (item != null && !item.isBlank() && highlights.size() < MAX_HIGHLIGHTS) {
                    highlights.add(item.trim());
                }
            }
        }
        if (raw.warnings() != null) {
            for (String warning : raw.warnings()) {
                if (warning != null && !warning.isBlank() && highlights.size() < MAX_HIGHLIGHTS) {
                    highlights.add("Note: " + warning.trim());
                }
            }
        }

        return new ValidatedAssistantPayload(title, content, new ArrayList<>(highlights));
    }
}

