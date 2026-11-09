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
@Schema(description = "Notable project extracted from a master resume")
public class ParsedResumeProjectDto {

    @NotBlank(message = "Project name is required")
    @Size(max = 150, message = "Project name must not exceed 150 characters")
    @Schema(description = "Project title", example = "SEPA Instant Payment Clearing Core")
    private String name;

    @Size(max = 1500, message = "Project description must not exceed 1500 characters")
    @Schema(description = "Project scope and outcomes explicitly stated in the resume")
    private String description;

    @Size(max = 25, message = "Maximum 25 technologies allowed per project")
    @Schema(description = "Technologies used in the project", example = "[\"Java 21\", \"Kafka\", \"PostgreSQL\"]")
    private List<@NotBlank(message = "Technology entry must not be blank")
    @Size(max = 100, message = "Technology name must not exceed 100 characters") String> technologies;

    @Size(max = 255, message = "Project URL must not exceed 255 characters")
    @ValidHttpUrl(message = "Project URL must be a valid HTTP or HTTPS URL")
    @Schema(description = "Project repository or live demo URL if explicitly present", example = "https://github.com/elena-weber/sepa-core")
    private String url;
}

