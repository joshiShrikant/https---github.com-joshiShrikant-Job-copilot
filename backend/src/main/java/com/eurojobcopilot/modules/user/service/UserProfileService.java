package com.eurojobcopilot.modules.user.service;

import com.eurojobcopilot.modules.auth.security.AuthenticatedUserPrincipal;
import com.eurojobcopilot.modules.user.dto.EducationDto;
import com.eurojobcopilot.modules.user.dto.UpsertEducationRequest;
import com.eurojobcopilot.modules.user.dto.UpsertProfileRequest;
import com.eurojobcopilot.modules.user.dto.UpsertSkillRequest;
import com.eurojobcopilot.modules.user.dto.UpsertWorkExperienceRequest;
import com.eurojobcopilot.modules.user.dto.UserProfileResponse;
import com.eurojobcopilot.modules.user.dto.UserSkillDto;
import com.eurojobcopilot.modules.user.dto.WorkExperienceDto;

import java.util.List;

public interface UserProfileService {

    UserProfileResponse createProfile(AuthenticatedUserPrincipal currentUser, UpsertProfileRequest request);

    UserProfileResponse getProfile(AuthenticatedUserPrincipal currentUser);

    UserProfileResponse upsertProfile(AuthenticatedUserPrincipal currentUser, UpsertProfileRequest request);

    void deleteProfile(AuthenticatedUserPrincipal currentUser);

    List<UserSkillDto> getSkills(AuthenticatedUserPrincipal currentUser);

    UserSkillDto getSkill(AuthenticatedUserPrincipal currentUser, Long skillId);

    UserSkillDto addSkill(AuthenticatedUserPrincipal currentUser, UpsertSkillRequest request);

    UserSkillDto updateSkill(AuthenticatedUserPrincipal currentUser, Long skillId, UpsertSkillRequest request);

    void deleteSkill(AuthenticatedUserPrincipal currentUser, Long skillId);

    List<EducationDto> getEducations(AuthenticatedUserPrincipal currentUser);

    EducationDto getEducation(AuthenticatedUserPrincipal currentUser, Long educationId);

    EducationDto addEducation(AuthenticatedUserPrincipal currentUser, UpsertEducationRequest request);

    EducationDto updateEducation(AuthenticatedUserPrincipal currentUser, Long educationId, UpsertEducationRequest request);

    void deleteEducation(AuthenticatedUserPrincipal currentUser, Long educationId);

    List<WorkExperienceDto> getWorkExperiences(AuthenticatedUserPrincipal currentUser);

    WorkExperienceDto getWorkExperience(AuthenticatedUserPrincipal currentUser, Long experienceId);

    WorkExperienceDto addWorkExperience(AuthenticatedUserPrincipal currentUser, UpsertWorkExperienceRequest request);

    WorkExperienceDto updateWorkExperience(AuthenticatedUserPrincipal currentUser, Long experienceId, UpsertWorkExperienceRequest request);

    void deleteWorkExperience(AuthenticatedUserPrincipal currentUser, Long experienceId);
}
