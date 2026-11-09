package com.eurojobcopilot.modules.job.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Authenticated user's persisted interaction flags and timestamps for a job")
public class JobInteractionResponse {

    @Schema(description = "Target job identifier", example = "1042")
    private Long jobId;

    @JsonProperty("isSaved")
    @Schema(name = "isSaved", description = "Whether the user has saved/bookmarked the job", example = "true")
    private boolean saved;

    @Schema(description = "UTC timestamp when the job was saved")
    private Instant savedAt;

    @JsonProperty("isInterested")
    @Schema(name = "isInterested", description = "Whether the user marked the job as interested", example = "false")
    private boolean interested;

    @Schema(description = "UTC timestamp when the job was marked interested")
    private Instant interestedAt;

    @JsonProperty("isHidden")
    @Schema(name = "isHidden", description = "Whether the user hid the job from default discovery", example = "false")
    private boolean hidden;

    @Schema(description = "UTC timestamp when the job was hidden")
    private Instant hiddenAt;

    @JsonProperty("hasApplied")
    @Schema(name = "hasApplied", description = "Whether the user has confirmed applying for this job", example = "false")
    private boolean hasApplied;

    @Schema(description = "UTC timestamp when application submission was confirmed")
    private Instant appliedAt;

    @Schema(description = "UTC timestamp of the most recent outbound apply redirect click")
    private Instant redirectClickedAt;
}

