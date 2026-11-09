package com.eurojobcopilot.modules.resume.dto;

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
@Schema(description = "Canonical structured tailored resume content stored in resume_data_json")
public class TailoredResumeDataDto {

    @Valid
    @Schema(description = "Candidate header section derived from verified profile details")
    private TailoredResumeHeaderDto header;

    @Size(max = 2500, message = "Tailored summary must not exceed 2500 characters")
    @Schema(description = "JD-aligned professional executive summary strictly grounded in verified experience")
    private String tailoredSummary;

    @Size(max = 40, message = "Maximum 40 highlighted skills allowed")
    @Schema(description = "Verified candidate skills prioritized for alignment with the target JD")
    private List<@NotBlank(message = "Skill must not be blank")
    @Size(max = 100, message = "Skill must not exceed 100 characters") String> highlightedSkills;

    @Size(max = 40, message = "Maximum 40 additional skills allowed")
    @Schema(description = "Remaining verified candidate skills retained for breadth")
    private List<@NotBlank(message = "Skill must not be blank")
    @Size(max = 100, message = "Skill must not exceed 100 characters") String> additionalSkills;

    @Size(max = 40, message = "Maximum 40 matched keywords allowed")
    @Schema(description = "Target JD keywords demonstrably covered across the tailored resume")
    private List<@NotBlank(message = "Keyword must not be blank")
    @Size(max = 100, message = "Keyword must not exceed 100 characters") String> matchedKeywords;

    @Size(max = 40, message = "Maximum 40 missing keywords allowed")
    @Schema(description = "Target JD keywords absent from verified candidate evidence (explicit gaps, never fabricated)")
    private List<@NotBlank(message = "Keyword must not be blank")
    @Size(max = 100, message = "Keyword must not exceed 100 characters") String> missingKeywords;

    @Size(max = 2000, message = "Tailoring rationale must not exceed 2000 characters")
    @Schema(description = "Explanation of how experience and skills were framed for this target role")
    private String tailoringRationale;

    @Valid
    @Size(max = 25, message = "Maximum 25 work experience entries allowed")
    @Schema(description = "Tailored work experience blocks anchored to verified employers and dates")
    private List<TailoredResumeExperienceDto> optimizedExperiences;

    @Valid
    @Size(max = 15, message = "Maximum 15 projects allowed")
    @Schema(description = "Verified candidate projects emphasized for this role")
    private List<TailoredResumeProjectDto> projects;

    @Valid
    @Size(max = 15, message = "Maximum 15 education entries allowed")
    @Schema(description = "Verified academic education entries")
    private List<TailoredResumeEducationDto> educations;

    @Size(max = 20, message = "Maximum 20 language entries allowed")
    @Schema(description = "Verified spoken languages", example = "[\"English (C2)\", \"German (B1)\"]")
    private List<@NotBlank(message = "Language entry must not be blank")
    @Size(max = 100, message = "Language entry must not exceed 100 characters") String> languages;

    @Size(max = 20, message = "Maximum 20 certifications allowed")
    @Schema(description = "Verified certifications", example = "[\"AWS Certified Solutions Architect\"]")
    private List<@NotBlank(message = "Certification must not be blank")
    @Size(max = 150, message = "Certification must not exceed 150 characters") String> certifications;

    @Size(max = 30, message = "Maximum 30 grounding warnings allowed")
    @Schema(description = "Verification notices when AI claims were filtered or truncated to prevent hallucination")
    private List<@NotBlank(message = "Warning must not be blank")
    @Size(max = 350, message = "Warning must not exceed 350 characters") String> groundingWarnings;
}

