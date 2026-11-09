package com.eurojobcopilot.modules.user.controller;

import com.eurojobcopilot.core.dto.ApiErrorResponse;
import com.eurojobcopilot.core.dto.ApiResponse;
import com.eurojobcopilot.modules.auth.security.AuthenticatedUserPrincipal;
import com.eurojobcopilot.modules.user.dto.EducationDto;
import com.eurojobcopilot.modules.user.dto.UpsertEducationRequest;
import com.eurojobcopilot.modules.user.dto.UpsertProfileRequest;
import com.eurojobcopilot.modules.user.dto.UpsertSkillRequest;
import com.eurojobcopilot.modules.user.dto.UpsertWorkExperienceRequest;
import com.eurojobcopilot.modules.user.dto.UserProfileResponse;
import com.eurojobcopilot.modules.user.dto.UserSkillDto;
import com.eurojobcopilot.modules.user.dto.WorkExperienceDto;
import com.eurojobcopilot.modules.user.service.UserProfileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/profile")
@RequiredArgsConstructor
@SecurityRequirement(name = "BearerAuth")
@Tag(name = "User Profile", description = "Authenticated candidate profile, skills, education, and work experience CRUD APIs")
public class UserProfileController {

    private final UserProfileService userProfileService;

    @PostMapping
    @Operation(summary = "Create user profile", operationId = "createUserProfile",
            description = "Creates the authenticated user's professional profile and initial job target preferences.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201",
                    description = "User profile created", useReturnTypeSchema = true),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400",
                    description = "Validation error in profile fields, currency code, or profile URLs",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                    description = "Unauthorized",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409",
                    description = "User profile already exists",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<UserProfileResponse> createProfile(
            @AuthenticationPrincipal AuthenticatedUserPrincipal currentUser,
            @Valid @RequestBody UpsertProfileRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(userProfileService.createProfile(currentUser, request));
    }

    @GetMapping
    @Operation(summary = "Get complete user profile", operationId = "getUserProfile",
            description = "Retrieves the authenticated user's complete profile including skills, education, and work experience.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                    description = "Complete candidate profile", useReturnTypeSchema = true),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                    description = "Unauthorized",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404",
                    description = "User profile has not been created yet",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<UserProfileResponse> getProfile(
            @AuthenticationPrincipal AuthenticatedUserPrincipal currentUser
    ) {
        return ResponseEntity.ok(userProfileService.getProfile(currentUser));
    }

    @PutMapping
    @Operation(summary = "Update user profile", operationId = "updateUserProfile",
            description = "Creates or updates the authenticated user's professional profile.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                    description = "Profile saved successfully", useReturnTypeSchema = true),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400",
                    description = "Invalid profile field values",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                    description = "Unauthorized",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409",
                    description = "Concurrent profile update conflict",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<UserProfileResponse> updateProfile(
            @AuthenticationPrincipal AuthenticatedUserPrincipal currentUser,
            @Valid @RequestBody UpsertProfileRequest request
    ) {
        return ResponseEntity.ok(userProfileService.upsertProfile(currentUser, request));
    }

    @DeleteMapping
    @Operation(summary = "Delete user profile", operationId = "deleteUserProfile",
            description = "Deletes the authenticated user's professional profile metadata.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                    description = "Profile deleted successfully", useReturnTypeSchema = true),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                    description = "Unauthorized",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404",
                    description = "User profile does not exist",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<ApiResponse<Void>> deleteProfile(
            @AuthenticationPrincipal AuthenticatedUserPrincipal currentUser
    ) {
        userProfileService.deleteProfile(currentUser);
        return ResponseEntity.ok(ApiResponse.ok("Profile deleted successfully"));
    }

    @GetMapping("/skills")
    @Operation(summary = "List technical skills", operationId = "listSkills",
            description = "Lists all technical skills belonging to the authenticated user.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                    description = "Ordered list of candidate technical skills", useReturnTypeSchema = true),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                    description = "Unauthorized",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<List<UserSkillDto>> getSkills(
            @AuthenticationPrincipal AuthenticatedUserPrincipal currentUser
    ) {
        return ResponseEntity.ok(userProfileService.getSkills(currentUser));
    }

    @GetMapping("/skills/{id}")
    @Operation(summary = "Get technical skill", operationId = "getSkill",
            description = "Retrieves a specific technical skill owned by the authenticated user.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                    description = "Skill entry", useReturnTypeSchema = true),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400",
                    description = "Invalid numeric skill ID",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                    description = "Unauthorized",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404",
                    description = "Skill not found or owned by another user",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<UserSkillDto> getSkill(
            @AuthenticationPrincipal AuthenticatedUserPrincipal currentUser,
            @Parameter(description = "Unique numeric identifier of the skill", example = "12")
            @PathVariable("id") Long skillId
    ) {
        return ResponseEntity.ok(userProfileService.getSkill(currentUser, skillId));
    }

    @PostMapping("/skills")
    @Operation(summary = "Add technical skill", operationId = "addSkill",
            description = "Adds a new technical skill to the authenticated user's profile.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201",
                    description = "Skill created", useReturnTypeSchema = true),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400",
                    description = "Invalid skill payload",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                    description = "Unauthorized",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409",
                    description = "Skill with the same normalized name already exists",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<UserSkillDto> addSkill(
            @AuthenticationPrincipal AuthenticatedUserPrincipal currentUser,
            @Valid @RequestBody UpsertSkillRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(userProfileService.addSkill(currentUser, request));
    }

    @PutMapping("/skills/{id}")
    @Operation(summary = "Update technical skill", operationId = "updateSkill",
            description = "Updates an existing technical skill owned by the authenticated user.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                    description = "Skill updated", useReturnTypeSchema = true),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400",
                    description = "Invalid skill ID or payload",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                    description = "Unauthorized",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404",
                    description = "Skill not found",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409",
                    description = "Renamed skill conflicts with an existing skill",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<UserSkillDto> updateSkill(
            @AuthenticationPrincipal AuthenticatedUserPrincipal currentUser,
            @Parameter(description = "Unique numeric identifier of the skill", example = "12")
            @PathVariable("id") Long skillId,
            @Valid @RequestBody UpsertSkillRequest request
    ) {
        return ResponseEntity.ok(userProfileService.updateSkill(currentUser, skillId, request));
    }

    @DeleteMapping("/skills/{id}")
    @Operation(summary = "Delete technical skill", operationId = "deleteSkill",
            description = "Deletes a technical skill owned by the authenticated user.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                    description = "Skill removed successfully", useReturnTypeSchema = true),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400",
                    description = "Invalid numeric skill ID",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                    description = "Unauthorized",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404",
                    description = "Skill not found",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<ApiResponse<Void>> deleteSkill(
            @AuthenticationPrincipal AuthenticatedUserPrincipal currentUser,
            @Parameter(description = "Unique numeric identifier of the skill", example = "12")
            @PathVariable("id") Long skillId
    ) {
        userProfileService.deleteSkill(currentUser, skillId);
        return ResponseEntity.ok(ApiResponse.ok("Skill removed successfully"));
    }

    @GetMapping("/education")
    @Operation(summary = "List education entries", operationId = "listEducations",
            description = "Lists all education entries belonging to the authenticated user.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                    description = "List of education entries", useReturnTypeSchema = true),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                    description = "Unauthorized",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<List<EducationDto>> getEducations(
            @AuthenticationPrincipal AuthenticatedUserPrincipal currentUser
    ) {
        return ResponseEntity.ok(userProfileService.getEducations(currentUser));
    }

    @GetMapping("/education/{id}")
    @Operation(summary = "Get education entry", operationId = "getEducation",
            description = "Retrieves a specific education entry owned by the authenticated user.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                    description = "Education entry", useReturnTypeSchema = true),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400",
                    description = "Invalid numeric education ID",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                    description = "Unauthorized",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404",
                    description = "Education entry not found",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<EducationDto> getEducation(
            @AuthenticationPrincipal AuthenticatedUserPrincipal currentUser,
            @Parameter(description = "Unique numeric identifier of the education entry", example = "3")
            @PathVariable("id") Long educationId
    ) {
        return ResponseEntity.ok(userProfileService.getEducation(currentUser, educationId));
    }

    @PostMapping("/education")
    @Operation(summary = "Add education entry", operationId = "addEducation",
            description = "Adds an education entry to the authenticated user's profile.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201",
                    description = "Education entry created", useReturnTypeSchema = true),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400",
                    description = "Invalid education fields or end date before start date",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                    description = "Unauthorized",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<EducationDto> addEducation(
            @AuthenticationPrincipal AuthenticatedUserPrincipal currentUser,
            @Valid @RequestBody UpsertEducationRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(userProfileService.addEducation(currentUser, request));
    }

    @PutMapping("/education/{id}")
    @Operation(summary = "Update education entry", operationId = "updateEducation",
            description = "Updates an education entry owned by the authenticated user.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                    description = "Education entry updated", useReturnTypeSchema = true),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400",
                    description = "Invalid education ID, fields, or date range",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                    description = "Unauthorized",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404",
                    description = "Education entry not found",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<EducationDto> updateEducation(
            @AuthenticationPrincipal AuthenticatedUserPrincipal currentUser,
            @Parameter(description = "Unique numeric identifier of the education entry", example = "3")
            @PathVariable("id") Long educationId,
            @Valid @RequestBody UpsertEducationRequest request
    ) {
        return ResponseEntity.ok(userProfileService.updateEducation(currentUser, educationId, request));
    }

    @DeleteMapping("/education/{id}")
    @Operation(summary = "Delete education entry", operationId = "deleteEducation",
            description = "Deletes an education entry owned by the authenticated user.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                    description = "Education entry removed successfully", useReturnTypeSchema = true),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400",
                    description = "Invalid numeric education ID",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                    description = "Unauthorized",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404",
                    description = "Education entry not found",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<ApiResponse<Void>> deleteEducation(
            @AuthenticationPrincipal AuthenticatedUserPrincipal currentUser,
            @Parameter(description = "Unique numeric identifier of the education entry", example = "3")
            @PathVariable("id") Long educationId
    ) {
        userProfileService.deleteEducation(currentUser, educationId);
        return ResponseEntity.ok(ApiResponse.ok("Education entry removed successfully"));
    }

    @GetMapping("/experience")
    @Operation(summary = "List work experience entries", operationId = "listWorkExperiences",
            description = "Lists all work experience entries belonging to the authenticated user.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                    description = "List of work experience entries", useReturnTypeSchema = true),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                    description = "Unauthorized",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<List<WorkExperienceDto>> getWorkExperiences(
            @AuthenticationPrincipal AuthenticatedUserPrincipal currentUser
    ) {
        return ResponseEntity.ok(userProfileService.getWorkExperiences(currentUser));
    }

    @GetMapping("/experience/{id}")
    @Operation(summary = "Get work experience entry", operationId = "getWorkExperience",
            description = "Retrieves a specific work experience entry owned by the authenticated user.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                    description = "Work experience entry", useReturnTypeSchema = true),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400",
                    description = "Invalid numeric work experience ID",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                    description = "Unauthorized",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404",
                    description = "Work experience entry not found",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<WorkExperienceDto> getWorkExperience(
            @AuthenticationPrincipal AuthenticatedUserPrincipal currentUser,
            @Parameter(description = "Unique numeric identifier of the work experience entry", example = "8")
            @PathVariable("id") Long experienceId
    ) {
        return ResponseEntity.ok(userProfileService.getWorkExperience(currentUser, experienceId));
    }

    @PostMapping("/experience")
    @Operation(summary = "Add work experience entry", operationId = "addWorkExperience",
            description = "Adds a work experience entry to the authenticated user's profile.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201",
                    description = "Work experience entry created", useReturnTypeSchema = true),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400",
                    description = "Invalid work experience fields or date range",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                    description = "Unauthorized",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<WorkExperienceDto> addWorkExperience(
            @AuthenticationPrincipal AuthenticatedUserPrincipal currentUser,
            @Valid @RequestBody UpsertWorkExperienceRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(userProfileService.addWorkExperience(currentUser, request));
    }

    @PutMapping("/experience/{id}")
    @Operation(summary = "Update work experience entry", operationId = "updateWorkExperience",
            description = "Updates a work experience entry owned by the authenticated user.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                    description = "Work experience entry updated", useReturnTypeSchema = true),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400",
                    description = "Invalid work experience ID, fields, or date range",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                    description = "Unauthorized",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404",
                    description = "Work experience entry not found",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<WorkExperienceDto> updateWorkExperience(
            @AuthenticationPrincipal AuthenticatedUserPrincipal currentUser,
            @Parameter(description = "Unique numeric identifier of the work experience entry", example = "8")
            @PathVariable("id") Long experienceId,
            @Valid @RequestBody UpsertWorkExperienceRequest request
    ) {
        return ResponseEntity.ok(userProfileService.updateWorkExperience(currentUser, experienceId, request));
    }

    @DeleteMapping("/experience/{id}")
    @Operation(summary = "Delete work experience entry", operationId = "deleteWorkExperience",
            description = "Deletes a work experience entry owned by the authenticated user.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                    description = "Work experience entry removed successfully", useReturnTypeSchema = true),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400",
                    description = "Invalid numeric work experience ID",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                    description = "Unauthorized",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404",
                    description = "Work experience entry not found",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<ApiResponse<Void>> deleteWorkExperience(
            @AuthenticationPrincipal AuthenticatedUserPrincipal currentUser,
            @Parameter(description = "Unique numeric identifier of the work experience entry", example = "8")
            @PathVariable("id") Long experienceId
    ) {
        userProfileService.deleteWorkExperience(currentUser, experienceId);
        return ResponseEntity.ok(ApiResponse.ok("Work experience entry removed successfully"));
    }
}
