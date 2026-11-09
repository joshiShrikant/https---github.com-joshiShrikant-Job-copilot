package com.eurojobcopilot.modules.user.service;

import com.eurojobcopilot.core.enums.WorkArrangement;
import com.eurojobcopilot.modules.auth.entity.AccountStatus;
import com.eurojobcopilot.modules.auth.entity.User;
import com.eurojobcopilot.modules.auth.entity.UserRole;
import com.eurojobcopilot.modules.auth.repository.UserRepository;
import com.eurojobcopilot.modules.auth.security.AuthenticatedUserPrincipal;
import com.eurojobcopilot.modules.matching.service.JobMatchingService;
import com.eurojobcopilot.modules.user.dto.JobPreferencesResponse;
import com.eurojobcopilot.modules.user.dto.UpdateJobPreferencesRequest;
import com.eurojobcopilot.modules.user.entity.UserJobPreference;
import com.eurojobcopilot.modules.user.exception.ProfileConflictException;
import com.eurojobcopilot.modules.user.repository.UserJobPreferenceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JobPreferenceServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserJobPreferenceRepository userJobPreferenceRepository;

    @Mock
    private JobMatchingService jobMatchingService;

    private JobPreferenceServiceImpl service;
    private User owner;
    private AuthenticatedUserPrincipal principal;

    @BeforeEach
    void setUp() {
        service = new JobPreferenceServiceImpl(userRepository, userJobPreferenceRepository, jobMatchingService);
        owner = User.builder()
                .id(15L)
                .uuid("user-uuid-15")
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
    void getPreferences_whenNoneSaved_returnsDefaultConfiguration() {
        when(userRepository.findById(15L)).thenReturn(Optional.of(owner));
        when(userJobPreferenceRepository.findByUserId(15L)).thenReturn(Optional.empty());

        JobPreferencesResponse response = service.getPreferences(principal);

        assertThat(response.getPreferredJobTitles()).isEmpty();
        assertThat(response.getPreferredCountries()).isEmpty();
        assertThat(response.getPreferredCities()).isEmpty();
        assertThat(response.getMinSalary()).isNull();
        assertThat(response.getSalaryCurrency()).isEqualTo("EUR");
        assertThat(response.isRequiresSponsorship()).isTrue();
        assertThat(response.isRequiresRelocation()).isTrue();
        assertThat(response.getWorkArrangement()).isEqualTo(WorkArrangement.ANY);
        assertThat(response.isEnglishSpeakingOnly()).isTrue();
        assertThat(response.getMinMatchScore()).isEqualTo(60);
        assertThat(response.isAutopilotEnabled()).isFalse();
    }

    @Test
    void updatePreferences_deduplicatesListsAndNormalizesCurrency() {
        UpdateJobPreferencesRequest request = UpdateJobPreferencesRequest.builder()
                .preferredJobTitles(List.of("  Senior Java Engineer ", "Senior Java Engineer", "Staff Engineer"))
                .preferredCountries(List.of("Germany", "Netherlands", "Germany"))
                .preferredCities(List.of("Berlin", "Amsterdam", "  "))
                .minSalary(new BigDecimal("92000.00"))
                .salaryCurrency("eur")
                .requiresSponsorship(true)
                .requiresRelocation(false)
                .workArrangement(WorkArrangement.HYBRID)
                .englishSpeakingOnly(true)
                .minMatchScore(75)
                .autopilotEnabled(true)
                .build();

        when(userRepository.findById(15L)).thenReturn(Optional.of(owner));
        when(userJobPreferenceRepository.findByUserId(15L)).thenReturn(Optional.empty());
        when(userJobPreferenceRepository.saveAndFlush(any(UserJobPreference.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        JobPreferencesResponse saved = service.updatePreferences(principal, request);

        assertThat(saved.getPreferredJobTitles()).containsExactly("Senior Java Engineer", "Staff Engineer");
        assertThat(saved.getPreferredCountries()).containsExactly("Germany", "Netherlands");
        assertThat(saved.getPreferredCities()).containsExactly("Berlin", "Amsterdam");
        assertThat(saved.getSalaryCurrency()).isEqualTo("EUR");
        assertThat(saved.isRequiresRelocation()).isFalse();
        assertThat(saved.getWorkArrangement()).isEqualTo(WorkArrangement.HYBRID);
        assertThat(saved.getMinMatchScore()).isEqualTo(75);
        assertThat(saved.isAutopilotEnabled()).isTrue();
    }

    @Test
    void updatePreferences_whenConcurrentIntegrityConflict_throwsProfileConflictException() {
        when(userRepository.findById(15L)).thenReturn(Optional.of(owner));
        when(userJobPreferenceRepository.findByUserId(15L)).thenReturn(Optional.empty());
        when(userJobPreferenceRepository.saveAndFlush(any(UserJobPreference.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate key"));

        assertThatThrownBy(() -> service.updatePreferences(principal, new UpdateJobPreferencesRequest()))
                .isInstanceOf(ProfileConflictException.class);
    }
}

