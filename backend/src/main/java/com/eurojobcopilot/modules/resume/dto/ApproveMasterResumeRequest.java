package com.eurojobcopilot.modules.resume.dto;

import com.eurojobcopilot.core.validation.ValidUuid;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Human-confirmed master resume approval and profile synchronization payload")
public class ApproveMasterResumeRequest {

    @NotBlank(message = "Resume UUID is required")
    @ValidUuid(message = "Resume UUID must be a valid canonical UUID")
    @Schema(description = "Canonical UUID of the uploaded master resume being approved", example = "550e8400-e29b-41d4-a716-446655440000")
    private String resumeUuid;

    @NotNull(message = "Approved parsed resume data is required")
    @Valid
    @Schema(description = "Human-reviewed and confirmed structured resume payload to save and sync to user profile")
    private ParsedResumeJsonDto approvedParsedData;

    @Builder.Default
    @Schema(description = "Sync strategy for profile collections (MERGE preserves manual entries; REPLACE replaces collections with approved resume entries)", example = "MERGE")
    private ProfileSyncMode syncMode = ProfileSyncMode.MERGE;
}

