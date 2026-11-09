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
@Schema(description = "Structured master resume JSON schema extracted for human review and approval")
public class ParsedResumeJsonDto {

    @Valid
    @Schema(description = "Extracted candidate profile metadata")
    private ParsedResumeProfileDto profile;

    @Size(max = 2000, message = "Summary must not exceed 2000 characters")
    @Schema(description = "Concise factual executive summary of the candidate's CV")
    private String summary;

    @Valid
    @Size(max = 60, message = "Maximum 60 skills allowed in parsed resume")
    @Schema(description = "Extracted technical and domain skills")
    private List<ParsedResumeSkillDto> skills;

    @Valid
    @Size(max = 25, message = "Maximum 25 work experiences allowed in parsed resume")
    @Schema(description = "Extracted chronological work experience entries")
    private List<ParsedResumeExperienceDto> experiences;

    @Valid
    @Size(max = 15, message = "Maximum 15 education entries allowed in parsed resume")
    @Schema(description = "Extracted academic education entries")
    private List<ParsedResumeEducationDto> educations;

    @Valid
    @Size(max = 15, message = "Maximum 15 projects allowed in parsed resume")
    @Schema(description = "Notable projects extracted from resume")
    private List<ParsedResumeProjectDto> projects;

    @Size(max = 20, message = "Maximum 20 language entries allowed")
    @Schema(description = "Spoken languages and proficiency levels if listed", example = "[\"English (C2)\", \"German (B1)\"]")
    private List<@NotBlank(message = "Language entry must not be blank")
    @Size(max = 100, message = "Language entry must not exceed 100 characters") String> languages;

    @Size(max = 20, message = "Maximum 20 certifications allowed")
    @Schema(description = "Professional certifications explicitly stated in the resume", example = "[\"AWS Certified Solutions Architect\"]")
    private List<@NotBlank(message = "Certification entry must not be blank")
    @Size(max = 150, message = "Certification entry must not exceed 150 characters") String> certifications;

    @Size(max = 30, message = "Maximum 30 warnings allowed")
    @Schema(description = "Verification alerts highlighting missing dates, ambiguous countries, or partial extractions requiring human confirmation")
    private List<@NotBlank(message = "Warning entry must not be blank")
    @Size(max = 300, message = "Warning entry must not exceed 300 characters") String> warnings;
}

