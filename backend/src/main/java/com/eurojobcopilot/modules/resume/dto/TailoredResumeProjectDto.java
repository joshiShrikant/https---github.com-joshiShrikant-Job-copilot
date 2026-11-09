package com.eurojobcopilot.modules.resume.dto;

import com.eurojobcopilot.core.validation.ValidHttpUrl;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Verified project entry emphasized for relevance to target role")
public class TailoredResumeProjectDto {

    @NotBlank(message = "Project name is required")
    @Size(max = 150, message = "Project name must not exceed 150 characters")
    @Schema(description = "Verified project title", example = "SEPA Instant Settlement Engine")
    private String name;

    @Size(max = 1500, message = "Project description must not exceed 1500 characters")
    @Schema(description = "Role-aligned summary of the verified project")
    private String description;

    @Size(max = 10, message = "Maximum 10 bullets allowed per project")
    @Schema(description = "Highlight bullets grounded in project description/master CV")
    private List<@NotBlank(message = "Project bullet must not be blank")
    @Size(max = 500, message = "Project bullet must not exceed 500 characters") String> tailoredBullets;

    @Size(max = 25, message = "Maximum 25 technologies allowed per project")
    @Schema(description = "Verified project tech stack", example = "[\"Java 21\", \"Spring Boot\", \"PostgreSQL\"]")
    private List<@NotBlank(message = "Technology entry must not be blank")
    @Size(max = 100, message = "Technology entry must not exceed 100 characters") String> technologies;

    @ValidHttpUrl(message = "Project URL must be a valid HTTP or HTTPS URL")
    @Size(max = 255, message = "Project URL must not exceed 255 characters")
    @Schema(description = "Verified repository or demo URL")
    private String url;
}

