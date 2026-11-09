package com.eurojobcopilot.modules.user.repository;

import com.eurojobcopilot.config.JpaAuditingConfig;
import com.eurojobcopilot.core.enums.WorkArrangement;
import com.eurojobcopilot.modules.auth.entity.User;
import com.eurojobcopilot.modules.auth.repository.UserRepository;
import com.eurojobcopilot.modules.user.entity.ProficiencyLevel;
import com.eurojobcopilot.modules.user.entity.RelocationPreference;
import com.eurojobcopilot.modules.user.entity.UserEducation;
import com.eurojobcopilot.modules.user.entity.UserJobPreference;
import com.eurojobcopilot.modules.user.entity.UserProfile;
import com.eurojobcopilot.modules.user.entity.UserSkill;
import com.eurojobcopilot.modules.user.entity.UserWorkExperience;
import com.eurojobcopilot.modules.user.entity.WorkAuthorizationStatus;
import com.eurojobcopilot.support.TestEntityFactory;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(JpaAuditingConfig.class)
class UserProfileRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserProfileRepository userProfileRepository;

    @Autowired
    private UserSkillRepository userSkillRepository;

    @Autowired
    private UserEducationRepository userEducationRepository;

    @Autowired
    private UserWorkExperienceRepository userWorkExperienceRepository;

    @Autowired
    private UserJobPreferenceRepository userJobPreferenceRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void shouldPersistProfileWithOptimisticLockingAndUniqueUserConstraint() {
        User user = userRepository.saveAndFlush(TestEntityFactory.createUser("profile@eurojob.eu"));

        UserProfile profile = UserProfile.builder()
                .user(user)
                .currentDesignation("Senior Java & Angular Engineer")
                .yearsOfExperience(new BigDecimal("6.5"))
                .currentCountry("India")
                .currentCity("Pune")
                .expectedSalaryMin(new BigDecimal("80000.00"))
                .expectedSalaryCurrency("EUR")
                .noticePeriodDays(30)
                .workAuthorizationStatus(WorkAuthorizationStatus.NEEDS_SPONSORSHIP)
                .relocationPreference(RelocationPreference.WILLING_TO_RELOCATE)
                .linkedinUrl("https://linkedin.com/in/example")
                .build();

        UserProfile saved = userProfileRepository.saveAndFlush(profile);
        assertThat(saved.getVersion()).isZero();

        saved.setCurrentCity("Bengaluru");
        UserProfile updated = userProfileRepository.saveAndFlush(saved);
        entityManager.clear();

        UserProfile reloaded = userProfileRepository.findByUserId(user.getId()).orElseThrow();
        assertThat(reloaded.getCurrentCity()).isEqualTo("Bengaluru");
        assertThat(reloaded.getVersion()).isEqualTo(1);
        assertThat(updated.getUpdatedAt()).isNotNull();

        assertThatThrownBy(() -> userProfileRepository.saveAndFlush(
                UserProfile.builder().user(user).currentDesignation("Duplicate").build()
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void shouldPersistSkillsEducationsExperiencesAndJsonJobPreferences() {
        User user = userRepository.saveAndFlush(TestEntityFactory.createUser("qualifications@eurojob.eu"));

        UserSkill javaSkill = UserSkill.builder()
                .user(user)
                .skillName("Java 21")
                .yearsExperience(new BigDecimal("6.0"))
                .proficiencyLevel(ProficiencyLevel.EXPERT)
                .primary(true)
                .build();

        UserSkill angularSkill = UserSkill.builder()
                .user(user)
                .skillName("Angular")
                .yearsExperience(new BigDecimal("4.5"))
                .proficiencyLevel(ProficiencyLevel.ADVANCED)
                .primary(false)
                .build();

        userSkillRepository.saveAll(List.of(angularSkill, javaSkill));

        userEducationRepository.save(UserEducation.builder()
                .user(user)
                .institution("TU Munich")
                .degree("M.Sc.")
                .fieldOfStudy("Computer Science")
                .startDate(LocalDate.of(2018, 10, 1))
                .endDate(LocalDate.of(2020, 9, 30))
                .gradeOrGpa("1.4")
                .country("Germany")
                .build());

        userWorkExperienceRepository.save(UserWorkExperience.builder()
                .user(user)
                .companyName("FinTech Core GmbH")
                .designation("Lead Backend Developer")
                .location("Berlin, Germany")
                .startDate(LocalDate.of(2021, 1, 1))
                .current(true)
                .description("Built distributed Spring Boot microservices and Angular dashboards.")
                .skillsUsed(List.of("Java 21", "Spring Boot", "MySQL", "Docker"))
                .build());

        userJobPreferenceRepository.save(UserJobPreference.builder()
                .user(user)
                .preferredJobTitles(List.of("Senior Java Developer", "Full Stack Engineer"))
                .preferredCountries(List.of("Netherlands", "Germany", "Ireland"))
                .preferredCities(List.of("Amsterdam", "Berlin"))
                .minSalary(new BigDecimal("78000.00"))
                .workArrangement(WorkArrangement.HYBRID)
                .requiresSponsorship(true)
                .autopilotEnabled(true)
                .build());

        entityManager.flush();
        entityManager.clear();

        List<UserSkill> skills = userSkillRepository.findAllByUserIdOrderByPrimaryDescYearsExperienceDesc(user.getId());
        assertThat(skills).hasSize(2);
        assertThat(skills.getFirst().getNormalizedSkill()).isEqualTo("java 21");
        assertThat(skills.getFirst().isPrimary()).isTrue();
        assertThat(userSkillRepository.existsByUserIdAndNormalizedSkill(user.getId(), "angular")).isTrue();

        assertThat(userEducationRepository.findAllByUserIdOrderByStartDateDesc(user.getId()))
                .singleElement()
                .satisfies(edu -> assertThat(edu.getInstitution()).isEqualTo("TU Munich"));

        assertThat(userWorkExperienceRepository.findAllByUserIdOrderByStartDateDesc(user.getId()))
                .singleElement()
                .satisfies(exp -> assertThat(exp.getSkillsUsed()).containsExactly("Java 21", "Spring Boot", "MySQL", "Docker"));

        UserJobPreference pref = userJobPreferenceRepository.findByUserId(user.getId()).orElseThrow();
        assertThat(pref.getPreferredCountries()).containsExactly("Netherlands", "Germany", "Ireland");
        assertThat(pref.getPreferredJobTitles()).containsExactly("Senior Java Developer", "Full Stack Engineer");
        assertThat(pref.isAutopilotEnabled()).isTrue();
    }
}

