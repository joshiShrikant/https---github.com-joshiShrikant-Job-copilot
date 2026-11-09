package com.eurojobcopilot.modules.user.dto;

import com.eurojobcopilot.modules.user.entity.ProficiencyLevel;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "User technical skill entry")
public class UserSkillDto {

    @Schema(description = "Skill ID", example = "12")
    private Long id;

    @Schema(description = "Display skill name", example = "Java")
    private String skillName;

    @Schema(description = "Normalized lowercase skill name", example = "java")
    private String normalizedSkill;

    @Schema(description = "Years of experience with this skill", example = "6.0")
    private BigDecimal yearsExperience;

    @Schema(description = "Proficiency level", example = "EXPERT")
    private ProficiencyLevel proficiencyLevel;

    @Schema(description = "Whether this is a primary core skill", example = "true")
    private boolean primary;
}
