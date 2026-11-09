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
@Schema(description = "Validated outbound application redirect URL and updated user-job interaction state")
public class ApplyRedirectResponse {

    @Schema(description = "Target job identifier", example = "1042")
    private Long jobId;

    @Schema(description = "Validated HTTP/HTTPS external application destination URL", example = "https://careers.adyen.com/jobs/991/apply")
    private String targetUrl;

    @Schema(description = "UTC timestamp recorded for the redirect click")
    private Instant redirectClickedAt;

    @JsonProperty("hasApplied")
    @Schema(name = "hasApplied", description = "Whether the job is currently marked as applied", example = "false")
    private boolean hasApplied;

    @Schema(description = "UTC timestamp when application confirmation was recorded, if set")
    private Instant appliedAt;

    @Schema(description = "Full updated interaction state snapshot for this job")
    private JobInteractionResponse interaction;
}

