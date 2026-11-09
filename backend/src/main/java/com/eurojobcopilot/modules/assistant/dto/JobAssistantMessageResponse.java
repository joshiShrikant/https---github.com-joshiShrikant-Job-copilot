package com.eurojobcopilot.modules.assistant.dto;

import com.eurojobcopilot.modules.assistant.entity.AssistantActionType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Persisted contextual AI Application Assistant response attached to a job for the user")
public class JobAssistantMessageResponse {

    private Long id;
    private String uuid;
    private Long jobId;
    private AssistantActionType actionType;
    private String actionLabel;
    private String userPrompt;
    private String assistantTitle;
    private String assistantContent;
    private String editableContent;
    private List<String> highlights;
    private boolean editedByUser;
    private boolean aiGenerated;
    private String aiProvider;
    private String aiModelUsed;
    private Instant createdAt;
    private Instant updatedAt;
}

