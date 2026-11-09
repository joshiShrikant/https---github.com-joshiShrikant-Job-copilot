package com.eurojobcopilot.modules.resume.dto;

import com.eurojobcopilot.modules.resume.entity.ResumeTemplate;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
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
@Schema(description = "Request payload to save human edits to an existing tailored resume draft and recalculate ATS keyword coverage")
public class UpdateTailoredResumeRequest {

    @Size(max = 200, message = "Title must not exceed 200 characters")
    @Schema(description = "Optional updated title for this tailored CV draft", example = "Staff Java Architect · Adyen N.V. (v1)")
    private String title;

    @Schema(description = "Selected visual layout template", example = "MINIMAL_ATS")
    private ResumeTemplate templateName;

    @Size(max = 2500, message = "Tailored summary must not exceed 2500 characters")
    @Schema(description = "Edited executive summary")
    private String tailoredSummary;

    @Size(max = 40, message = "Maximum 40 highlighted skills allowed")
    @Schema(description = "Edited highlighted skills list")
    private List<@NotBlank(message = "Skill must not be blank")
    @Size(max = 100, message = "Skill must not exceed 100 characters") String> highlightedSkills;

    @Size(max = 40, message = "Maximum 40 additional skills allowed")
    @Schema(description = "Edited additional skills list")
    private List<@NotBlank(message = "Skill must not be blank")
    @Size(max = 100, message = "Skill must not exceed 100 characters") String> additionalSkills;

    @Valid
    @Size(max = 25, message = "Maximum 25 work experience entries allowed")
    @Schema(description = "Edited tailored work experience entries")
    private List<TailoredResumeExperienceDto> optimizedExperiences;

    @Valid
    @Size(max = 15, message = "Maximum 15 projects allowed")
    @Schema(description = "Edited tailored project entries")
    private List<TailoredResumeProjectDto> projects;

    @Valid
    @Size(max = 15, message = "Maximum 15 education entries allowed")
    @Schema(description = "Edited education entries")
    private List<TailoredResumeEducationDto> educations;

    @Size(max = 20, message = "Maximum 20 language entries allowed")
    @Schema(description = "Edited spoken languages")
    private List<@NotBlank(message = "Language entry must not be blank")
    @Size(max = 100, message = "Language entry must not exceed 100 characters") String> languages;

    @Size(max = 20, message = "Maximum 20 certifications allowed")
    @Schema(description = "Edited certifications")
    private List<@NotBlank(message = "Certification must not be blank")
    @Size(max = 150, message = "Certification must not exceed 150 characters") String> certifications;
}

