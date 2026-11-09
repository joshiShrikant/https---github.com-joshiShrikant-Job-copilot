package com.eurojobcopilot.modules.user.dto;

import com.eurojobcopilot.modules.user.entity.ProficiencyLevel;
import com.fasterxml.jackson.annotation.JsonAlias;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Create or update request for a technical skill")
public class UpsertSkillRequest {

    @NotBlank(message = "Skill name is required")
    @Size(max = 100, message = "Skill name must not exceed 100 characters")
    @Schema(description = "Technical skill name", example = "Spring Boot")
    private String skillName;

    @NotNull(message = "Years of experience is required")
    @DecimalMin(value = "0.0", message = "Years of experience must be 0 or greater")
    @DecimalMax(value = "70.0", message = "Years of experience must not exceed 70")
    @Digits(integer = 3, fraction = 1, message = "Years of experience must have at most 3 integer digits and 1 decimal place")
    @Schema(description = "Years of experience with the skill", example = "5.5")
    private BigDecimal yearsExperience;

    @NotNull(message = "Proficiency level is required")
    @Schema(description = "Skill proficiency level", example = "EXPERT")
    private ProficiencyLevel proficiencyLevel;

    @JsonAlias("isPrimary")
    @Schema(description = "Marks whether this is a core/primary skill", example = "true")
    private boolean primary;
}
