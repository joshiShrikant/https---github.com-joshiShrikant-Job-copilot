package com.eurojobcopilot.modules.resume.dto;

import com.eurojobcopilot.modules.user.entity.ProficiencyLevel;
import com.fasterxml.jackson.annotation.JsonAlias;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
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
@Schema(description = "Technical or domain skill extracted from a master resume")
public class ParsedResumeSkillDto {

    @NotBlank(message = "Skill name is required")
    @Size(max = 100, message = "Skill name must not exceed 100 characters")
    @Schema(description = "Technical or domain skill name", example = "Java 21")
    private String skillName;

    @DecimalMin(value = "0.0", message = "Skill years experience must be 0 or greater")
    @DecimalMax(value = "70.0", message = "Skill years experience must not exceed 70")
    @Digits(integer = 3, fraction = 1, message = "Skill years experience must have at most 3 integer digits and 1 decimal place")
    @Schema(description = "Years of experience with the skill if stated or directly inferable", example = "6.0")
    private BigDecimal yearsExperience;

    @Schema(description = "Inferred or verified proficiency level", example = "EXPERT")
    private ProficiencyLevel proficiencyLevel;

    @JsonAlias("isPrimary")
    @Schema(description = "Whether this is a core/primary skill", example = "true")
    private boolean primary;
}

