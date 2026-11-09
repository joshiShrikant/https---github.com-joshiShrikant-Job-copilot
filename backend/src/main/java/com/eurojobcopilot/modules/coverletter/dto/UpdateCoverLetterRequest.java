package com.eurojobcopilot.modules.coverletter.dto;

import com.eurojobcopilot.modules.coverletter.entity.CoverLetterTone;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request payload to save human edits to an existing cover letter draft")
public class UpdateCoverLetterRequest {

    @Size(max = 200, message = "Title must not exceed 200 characters")
    @Schema(description = "Updated display title for the cover letter draft", example = "Cover Letter · Staff Java Architect · Adyen N.V. (v1)")
    private String title;

    @Schema(description = "Selected tone label for organizational categorization", example = "CONFIDENT")
    private CoverLetterTone tone;

    @Size(max = 15000, message = "Cover letter content must not exceed 15000 characters")
    @Schema(description = "Edited full cover letter body text")
    private String content;

    @Size(max = 1000, message = "Personal notes must not exceed 1000 characters")
    @Schema(description = "Optional updated personal motivation or relocation notes")
    private String personalNotes;
}

