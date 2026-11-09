package com.eurojobcopilot.modules.resume.dto;

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
@Schema(description = "Telemetry summarizing profile changes synced from an approved master resume")
public class MasterResumeSyncSummaryDto {

    @Schema(description = "Sync strategy applied during approval", example = "MERGE")
    private ProfileSyncMode syncMode;

    @Schema(description = "Whether a new user_profiles record was created", example = "false")
    private boolean profileCreated;

    @Schema(description = "Whether user_profiles core fields were updated", example = "true")
    private boolean profileUpdated;

    @Schema(description = "Number of new skills inserted into user_skills", example = "4")
    private int skillsAdded;

    @Schema(description = "Number of existing user_skills updated", example = "2")
    private int skillsUpdated;

    @Schema(description = "Number of new work experiences added", example = "2")
    private int experiencesAdded;

    @Schema(description = "Number of existing work experiences updated", example = "1")
    private int experiencesUpdated;

    @Schema(description = "Number of new education records added", example = "1")
    private int educationsAdded;

    @Schema(description = "Number of existing education records updated", example = "0")
    private int educationsUpdated;

    @Schema(description = "UTC timestamp when profile sync completed")
    private Instant syncedAt;
}

