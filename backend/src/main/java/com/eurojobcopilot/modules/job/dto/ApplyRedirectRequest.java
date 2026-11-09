package com.eurojobcopilot.modules.job.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Optional payload when tracking an external application redirect click")
public class ApplyRedirectRequest {

    @Schema(description = "When true, also records hasApplied=true and appliedAt timestamp alongside redirectClickedAt", example = "false")
    private Boolean markApplied;
}

