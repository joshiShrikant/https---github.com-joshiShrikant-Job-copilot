package com.eurojobcopilot.modules.resume.dto;

import io.swagger.v3.oas.annotations.media.Schema;
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
@Schema(description = "Verified candidate header block displayed at the top of the tailored CV")
public class TailoredResumeHeaderDto {

    @Size(max = 200)
    @Schema(description = "Candidate full name", example = "Shrikant Joshi")
    private String fullName;

    @Size(max = 150)
    @Schema(description = "Role-aligned professional headline grounded in verified candidate title", example = "Senior Java Backend Architect")
    private String headline;

    @Size(max = 150)
    @Schema(description = "Candidate email address", example = "shrikant@example.com")
    private String email;

    @Size(max = 150)
    @Schema(description = "Candidate current location label", example = "Berlin, Germany")
    private String location;

    @Size(max = 100)
    @Schema(description = "Candidate current country", example = "Germany")
    private String country;

    @Schema(description = "Total verified years of experience", example = "7.5")
    private BigDecimal yearsOfExperience;

    @Size(max = 120)
    @Schema(description = "Work authorization and relocation readiness note grounded in profile", example = "EU Blue Card eligible · Open to relocation")
    private String workAuthorizationSummary;

    @Size(max = 255)
    @Schema(description = "Verified LinkedIn profile URL")
    private String linkedinUrl;

    @Size(max = 255)
    @Schema(description = "Verified GitHub profile URL")
    private String githubUrl;

    @Size(max = 255)
    @Schema(description = "Verified portfolio or personal site URL")
    private String portfolioUrl;
}

