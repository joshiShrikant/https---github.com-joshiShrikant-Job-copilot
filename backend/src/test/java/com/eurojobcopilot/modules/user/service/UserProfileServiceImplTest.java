package com.eurojobcopilot.modules.user.service;

import com.eurojobcopilot.modules.auth.entity.AccountStatus;
import com.eurojobcopilot.modules.auth.entity.User;
import com.eurojobcopilot.modules.auth.entity.UserRole;
import com.eurojobcopilot.modules.auth.repository.UserRepository;
import com.eurojobcopilot.modules.auth.security.AuthenticatedUserPrincipal;
import com.eurojobcopilot.modules.matching.service.JobMatchingService;
import com.eurojobcopilot.modules.user.dto.EducationDto;
import com.eurojobcopilot.modules.user.dto.UpsertEducationRequest;
import com.eurojobcopilot.modules.user.dto.UpsertProfileRequest;
import com.eurojobcopilot.modules.user.dto.UpsertSkillRequest;
import com.eurojobcopilot.modules.user.dto.UpsertWorkExperienceRequest;
import com.eurojobcopilot.modules.user.dto.UserProfileResponse;
import com.eurojobcopilot.modules.user.dto.UserSkillDto;
import com.eurojobcopilot.modules.user.dto.WorkExperienceDto;
import com.eurojobcopilot.modules.user.entity.ProficiencyLevel;
import com.eurojobcopilot.modules.user.entity.RelocationPreference;
import com.eurojobcopilot.modules.user.entity.UserEducation;
import com.eurojobcopilot.modules.user.entity.UserJobPreference;
import com.eurojobcopilot.modules.user.entity.UserProfile;
import com.eurojobcopilot.modules.user.entity.UserSkill;
import com.eurojobcopilot.modules.user.entity.UserWorkExperience;
import com.eurojobcopilot.modules.user.entity.WorkAuthorizationStatus;
import com.eurojobcopilot.modules.user.exception.InvalidProfileDataException;
import com.eurojobcopilot.modules.user.exception.ProfileConflictException;
import com.eurojobcopilot.modules.user.exception.ProfileResourceNotFoundException;
import com.eurojobcopilot.modules.user.repository.UserEducationRepository;
import com.eurojobcopilot.modules.user.repository.UserJobPreferenceRepository;
import com.eurojobcopilot.modules.user.repository.UserProfileRepository;
import com.eurojobcopilot.modules.user.repository.UserSkillRepository;
import com.eurojobcopilot.modules.user.repository.UserWorkExperienceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserProfileServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserProfileRepository userProfileRepository;

    @Mock
    private UserSkillRepository userSkillRepository;

    @Mock
    private UserEducationRepository userEducationRepository;

    @Mock
    private UserWorkExperienceRepository userWorkExperienceRepository;

    @Mock
    private UserJobPreferenceRepository userJobPreferenceRepository;

    @Mock
    private JobMatchingService jobMatchingService;

    private UserProfileServiceImpl service;
    private User owner;
    private AuthenticatedUserPrincipal principal;

    @BeforeEach
    void setUp() {
        service = new UserProfileServiceImpl(
                userRepository,
                userProfileRepository,
                userSkillRepository,
                userEducationRepository,
                userWorkExperienceRepository,
                userJobPreferenceRepository,
                jobMatchingService
        );
        owner = User.builder()
                .id(10L)
                .uuid("user-uuid-10")
                .email("alex@eurojob.eu")
                .passwordHash("$2a$10$hash")
                .firstName("Alex")
                .lastName("Mueller")
                .role(UserRole.ROLE_USER)
                .accountStatus(AccountStatus.ACTIVE)
                .build();
        principal = AuthenticatedUserPrincipal.from(owner);
    }

    @Test
    void createProfile_validRequest_persistsProfilePreferencesAndUpdatesName() {
        UpsertProfileRequest request = UpsertProfileRequest.builder()
                .firstName(" Alexander ")
                .lastName(" Mueller ")
                .currentDesignation(" Staff Java Engineer ")
                .yearsOfExperience(new BigDecimal("7.5"))
                .currentCountry("India")
                .currentCity("Bengaluru")
                .expectedSalaryMin(new BigDecimal("90000.00"))
                .expectedSalaryCurrency("eur")
                .noticePeriodDays(45)
                .workAuthorizationStatus(WorkAuthorizationStatus.NEEDS_SPONSORSHIP)
                .relocationPreference(RelocationPreference.WILLING_TO_RELOCATE)
                .linkedinUrl("https://www.linkedin.com/in/alex-mueller")
                .githubUrl("https://github.com/alex-mueller")
                .preferredJobTitles(List.of("Staff Java Engineer", "Staff Java Engineer", "Backend Architect"))
                .preferredCountries(List.of("Germany", "Netherlands"))
                .build();

        when(userRepository.findById(10L)).thenReturn(Optional.of(owner));
        when(userProfileRepository.existsByUserId(10L)).thenReturn(false);
        when(userProfileRepository.saveAndFlush(any(UserProfile.class))).thenAnswer(inv -> {
            UserProfile p = inv.getArgument(0);
            p.setId(55L);
            p.setCreatedAt(Instant.now());
            p.setUpdatedAt(Instant.now());
            return p;
        });
        when(userJobPreferenceRepository.findByUserId(10L)).thenReturn(Optional.empty());
        when(userJobPreferenceRepository.saveAndFlush(any(UserJobPreference.class))).thenAnswer(inv -> inv.getArgument(0));
        when(userSkillRepository.findAllByUserIdOrderByPrimaryDescYearsExperienceDesc(10L)).thenReturn(List.of());
        when(userEducationRepository.findAllByUserIdOrderByStartDateDesc(10L)).thenReturn(List.of());
        when(userWorkExperienceRepository.findAllByUserIdOrderByStartDateDesc(10L)).thenReturn(List.of());

        UserProfileResponse response = service.createProfile(principal, request);

        assertThat(response.getUuid()).isEqualTo("user-uuid-10");
        assertThat(response.getFirstName()).isEqualTo("Alexander");
        assertThat(response.getFullName()).isEqualTo("Alexander Mueller");
        assertThat(response.getEmail()).isEqualTo("alex@eurojob.eu");
        assertThat(response.getExpectedSalaryCurrency()).isEqualTo("EUR");
        assertThat(response.getPreferredJobTitles()).containsExactly("Staff Java Engineer", "Backend Architect");
        assertThat(response.getPreferredCountries()).containsExactly("Germany", "Netherlands");
        verify(userRepository).save(owner);
    }

    @Test
    void createProfile_alreadyExists_throwsConflict() {
        when(userRepository.findById(10L)).thenReturn(Optional.of(owner));
        when(userProfileRepository.existsByUserId(10L)).thenReturn(true);

        assertThatThrownBy(() -> service.createProfile(principal, new UpsertProfileRequest()))
                .isInstanceOf(ProfileConflictException.class);
        verify(userProfileRepository, never()).saveAndFlush(any());
    }

    @Test
    void getProfile_notCreated_throwsNotFound() {
        when(userRepository.findById(10L)).thenReturn(Optional.of(owner));
        when(userProfileRepository.findByUserId(10L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getProfile(principal))
                .isInstanceOf(ProfileResourceNotFoundException.class);
    }

    @Test
    void addSkill_duplicateNormalizedSkill_throwsConflict() {
        when(userRepository.findById(10L)).thenReturn(Optional.of(owner));
        when(userSkillRepository.existsByUserIdAndNormalizedSkill(10L, "spring boot")).thenReturn(true);

        UpsertSkillRequest request = UpsertSkillRequest.builder()
                .skillName("  Spring Boot ")
                .yearsExperience(new BigDecimal("4.0"))
                .proficiencyLevel(ProficiencyLevel.EXPERT)
                .primary(true)
                .build();

        assertThatThrownBy(() -> service.addSkill(principal, request))
                .isInstanceOf(ProfileConflictException.class);
        verify(userSkillRepository, never()).saveAndFlush(any());
    }

    @Test
    void updateSkill_notOwnedByCurrentUser_throwsNotFound() {
        when(userRepository.findById(10L)).thenReturn(Optional.of(owner));
        when(userSkillRepository.findByIdAndUserId(999L, 10L)).thenReturn(Optional.empty());

        UpsertSkillRequest request = UpsertSkillRequest.builder()
                .skillName("Kubernetes")
                .yearsExperience(new BigDecimal("3.0"))
                .proficiencyLevel(ProficiencyLevel.ADVANCED)
                .primary(false)
                .build();

        assertThatThrownBy(() -> service.updateSkill(principal, 999L, request))
                .isInstanceOf(ProfileResourceNotFoundException.class);
    }

    @Test
    void updateSkill_renamingSkill_updatesNormalizedNameAndSaves() {
        UserSkill existing = UserSkill.builder()
                .id(7L)
                .user(owner)
                .skillName("Java")
                .normalizedSkill("java")
                .yearsExperience(new BigDecimal("5.0"))
                .proficiencyLevel(ProficiencyLevel.ADVANCED)
                .primary(false)
                .build();

        when(userRepository.findById(10L)).thenReturn(Optional.of(owner));
        when(userSkillRepository.findByIdAndUserId(7L, 10L)).thenReturn(Optional.of(existing));
        when(userSkillRepository.existsByUserIdAndNormalizedSkill(10L, "java 21")).thenReturn(false);
        when(userSkillRepository.saveAndFlush(any(UserSkill.class))).thenAnswer(inv -> inv.getArgument(0));

        UserSkillDto updated = service.updateSkill(principal, 7L, UpsertSkillRequest.builder()
                .skillName(" Java 21 ")
                .yearsExperience(new BigDecimal("6.0"))
                .proficiencyLevel(ProficiencyLevel.EXPERT)
                .primary(true)
                .build());

        assertThat(updated.getSkillName()).isEqualTo("Java 21");
        assertThat(updated.getNormalizedSkill()).isEqualTo("java 21");
        assertThat(updated.getProficiencyLevel()).isEqualTo(ProficiencyLevel.EXPERT);
        assertThat(updated.isPrimary()).isTrue();
    }

    @Test
    void addEducation_endDateBeforeStartDate_throwsInvalidProfileDataException() {
        when(userRepository.findById(10L)).thenReturn(Optional.of(owner));

        UpsertEducationRequest request = UpsertEducationRequest.builder()
                .institution("TU Berlin")
                .degree("B.Sc.")
                .fieldOfStudy("Computer Science")
                .startDate(LocalDate.of(2022, 10, 1))
                .endDate(LocalDate.of(2020, 9, 1))
                .country("Germany")
                .build();

        assertThatThrownBy(() -> service.addEducation(principal, request))
                .isInstanceOf(InvalidProfileDataException.class)
                .hasMessageContaining("end date");
    }

    @Test
    void addWorkExperience_currentRole_clearsEndDateAndDeduplicatesSkills() {
        when(userRepository.findById(10L)).thenReturn(Optional.of(owner));
        when(userWorkExperienceRepository.saveAndFlush(any(UserWorkExperience.class))).thenAnswer(inv -> {
            UserWorkExperience entity = inv.getArgument(0);
            entity.setId(21L);
            return entity;
        });

        UpsertWorkExperienceRequest request = UpsertWorkExperienceRequest.builder()
                .companyName("Adyen")
                .designation("Staff Engineer")
                .location("Amsterdam")
                .startDate(LocalDate.of(2023, 1, 1))
                .endDate(LocalDate.of(2025, 1, 1))
                .current(true)
                .skillsUsed(List.of("Java", "Java", " Kafka "))
                .build();

        WorkExperienceDto result = service.addWorkExperience(principal, request);

        assertThat(result.getId()).isEqualTo(21L);
        assertThat(result.isCurrent()).isTrue();
        assertThat(result.getEndDate()).isNull();
        assertThat(result.getSkillsUsed()).containsExactly("Java", "Kafka");
    }

    @Test
    void deleteEducation_notOwnedByUser_throwsNotFound() {
        when(userRepository.findById(10L)).thenReturn(Optional.of(owner));
        when(userEducationRepository.findByIdAndUserId(44L, 10L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deleteEducation(principal, 44L))
                .isInstanceOf(ProfileResourceNotFoundException.class);
        verify(userEducationRepository, never()).delete(any(UserEducation.class));
    }

    @Test
    void getSkill_notOwnedByUser_throwsNotFound() {
        when(userRepository.findById(10L)).thenReturn(Optional.of(owner));
        when(userSkillRepository.findByIdAndUserId(77L, 10L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getSkill(principal, 77L))
                .isInstanceOf(ProfileResourceNotFoundException.class);
    }

    @Test
    void upsertProfile_paddedShortFirstName_throwsInvalidProfileDataException() {
        when(userRepository.findById(10L)).thenReturn(Optional.of(owner));
        when(userProfileRepository.findByUserId(10L)).thenReturn(Optional.empty());

        UpsertProfileRequest invalidNameRequest = UpsertProfileRequest.builder()
                .firstName(" A ")
                .build();

        assertThatThrownBy(() -> service.upsertProfile(principal, invalidNameRequest))
                .isInstanceOf(InvalidProfileDataException.class)
                .hasMessageContaining("First name");
    }
}
