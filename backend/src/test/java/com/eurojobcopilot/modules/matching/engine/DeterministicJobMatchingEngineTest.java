package com.eurojobcopilot.modules.matching.engine;

import com.eurojobcopilot.core.enums.WorkArrangement;
import com.eurojobcopilot.modules.job.entity.ExperienceLevel;
import com.eurojobcopilot.modules.job.entity.Job;
import com.eurojobcopilot.modules.job.entity.JobSponsorStatus;
import com.eurojobcopilot.modules.job.entity.SalaryPeriod;
import com.eurojobcopilot.modules.job.normalization.CanonicalHashGenerator;
import com.eurojobcopilot.modules.job.normalization.JobNormalizer;
import com.eurojobcopilot.modules.matching.config.MatchingProperties;
import com.eurojobcopilot.modules.sponsorship.entity.Company;
import com.eurojobcopilot.modules.user.entity.ProficiencyLevel;
import com.eurojobcopilot.modules.user.entity.RelocationPreference;
import com.eurojobcopilot.modules.user.entity.UserJobPreference;
import com.eurojobcopilot.modules.user.entity.UserProfile;
import com.eurojobcopilot.modules.user.entity.UserSkill;
import com.eurojobcopilot.modules.user.entity.UserWorkExperience;
import com.eurojobcopilot.modules.user.entity.WorkAuthorizationStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DeterministicJobMatchingEngineTest {

    private CanonicalHashGenerator canonicalHashGenerator;
    private JobNormalizer jobNormalizer;
    private MatchingProperties matchingProperties;
    private DeterministicJobMatchingEngine engine;

    @BeforeEach
    void setUp() {
        canonicalHashGenerator = new CanonicalHashGenerator();
        jobNormalizer = new JobNormalizer(canonicalHashGenerator);
        matchingProperties = new MatchingProperties();
        engine = new DeterministicJobMatchingEngine(canonicalHashGenerator, jobNormalizer, matchingProperties);
    }

    @Test
    void evaluate_fullMatchAcrossAllDimensions_scores100WithAllFieldsPopulated() {
        UserProfile profile = UserProfile.builder()
                .currentDesignation("Senior Java Backend Engineer")
                .yearsOfExperience(new BigDecimal("6.0"))
                .currentCountry("India")
                .expectedSalaryMin(new BigDecimal("85000.00"))
                .expectedSalaryCurrency("EUR")
                .workAuthorizationStatus(WorkAuthorizationStatus.NEEDS_SPONSORSHIP)
                .relocationPreference(RelocationPreference.WILLING_TO_RELOCATE)
                .build();

        List<UserSkill> skills = List.of(
                UserSkill.builder().skillName("Java").normalizedSkill("java").proficiencyLevel(ProficiencyLevel.EXPERT).build(),
                UserSkill.builder().skillName("Spring Boot").normalizedSkill("spring boot").build(),
                UserSkill.builder().skillName("PostgreSQL").normalizedSkill("postgresql").build(),
                UserSkill.builder().skillName("Docker").normalizedSkill("docker").build()
        );

        UserJobPreference preference = UserJobPreference.builder()
                .preferredJobTitles(List.of("Senior Java Backend Engineer"))
                .preferredCountries(List.of("Netherlands", "Germany"))
                .preferredCities(List.of("Amsterdam"))
                .minSalary(new BigDecimal("85000.00"))
                .salaryCurrency("EUR")
                .requiresSponsorship(true)
                .workArrangement(WorkArrangement.HYBRID)
                .build();

        Company adyen = Company.builder().name("Adyen N.V.").verifiedSponsor(true).build();
        Job job = Job.builder()
                .title("Senior Java Backend Engineer")
                .companyName("Adyen N.V.")
                .country("Netherlands")
                .city("Amsterdam")
                .workArrangement(WorkArrangement.HYBRID)
                .experienceLevel(ExperienceLevel.SENIOR)
                .minExperienceYears(new BigDecimal("5.0"))
                .salaryMin(new BigDecimal("90000.00"))
                .salaryMax(new BigDecimal("115000.00"))
                .salaryCurrency("EUR")
                .salaryPeriod(SalaryPeriod.YEARLY)
                .skills(List.of("Java 21", "Spring Boot", "Postgres", "Docker"))
                .sponsorStatus(JobSponsorStatus.VERIFIED_SPONSOR)
                .description("Join our Amsterdam platform engineering team.")
                .build();

        DeterministicMatchResult result = engine.evaluate(
                new CandidateMatchContext(profile, skills, List.of(), preference),
                job,
                adyen
        );

        assertThat(result.deterministicScore()).isEqualByComparingTo("100.00");
        assertThat(result.finalScore()).isEqualByComparingTo("100.00");
        assertThat(result.matchPercentage()).isEqualTo(100);
        assertThat(result.skillScore()).isEqualByComparingTo("100.00");
        assertThat(result.experienceScore()).isEqualByComparingTo("100.00");
        assertThat(result.titleScore()).isEqualByComparingTo("100.00");
        assertThat(result.locationScore()).isEqualByComparingTo("100.00");
        assertThat(result.sponsorshipScore()).isEqualByComparingTo("100.00");
        assertThat(result.salaryScore()).isEqualByComparingTo("100.00");
        assertThat(result.matchingSkills()).containsExactly("Java 21", "Spring Boot", "Postgres", "Docker");
        assertThat(result.missingSkills()).isEmpty();
        assertThat(result.experienceCompatibility()).contains("Strong Fit").contains("6 yrs").contains("Senior");
        assertThat(result.recommendationReasons()).isNotEmpty();
    }

    @Test
    void evaluate_configurableWeights_appliesCustomWeightsProportionally() {
        UserProfile profile = UserProfile.builder()
                .yearsOfExperience(new BigDecimal("5.0"))
                .workAuthorizationStatus(WorkAuthorizationStatus.NEEDS_SPONSORSHIP)
                .relocationPreference(RelocationPreference.WILLING_TO_RELOCATE)
                .build();

        List<UserSkill> skills = List.of(
                UserSkill.builder().skillName("Java").normalizedSkill("java").build()
        );

        UserJobPreference preference = UserJobPreference.builder()
                .preferredJobTitles(List.of("Java Backend Engineer"))
                .preferredCountries(List.of("Germany"))
                .preferredCities(List.of())
                .minSalary(new BigDecimal("100000.00"))
                .salaryCurrency("EUR")
                .requiresSponsorship(true)
                .workArrangement(WorkArrangement.ONSITE)
                .build();

        // Expected sub-scores:
        // Skills: 1 of 2 -> 50.00
        // Experience: 5.0 yrs vs 5.0+ -> 100.00
        // Title: exact match -> 100.00
        // Location: country & arrangement match -> 100.00
        // Sponsorship: NO_SPONSORSHIP when needs sponsorship -> 0.00
        // Salary: 60k max vs 100k target (ratio 0.60 < 0.75) -> 0.00
        Job job = Job.builder()
                .title("Java Backend Engineer")
                .country("Germany")
                .city("Berlin")
                .workArrangement(WorkArrangement.ONSITE)
                .experienceLevel(ExperienceLevel.SENIOR)
                .minExperienceYears(new BigDecimal("5.0"))
                .salaryMin(new BigDecimal("50000.00"))
                .salaryMax(new BigDecimal("60000.00"))
                .salaryCurrency("EUR")
                .salaryPeriod(SalaryPeriod.YEARLY)
                .skills(List.of("Java", "Kubernetes"))
                .sponsorStatus(JobSponsorStatus.NO_SPONSORSHIP)
                .description("Role in Berlin.")
                .build();

        // Default weights: 40/20/15/10/10/5 = 0.4*50 + 0.2*100 + 0.15*100 + 0.10*100 + 0 + 0 = 20 + 20 + 15 + 10 = 65.00
        DeterministicMatchResult defaultResult = engine.evaluate(
                new CandidateMatchContext(profile, skills, List.of(), preference),
                job,
                null
        );
        assertThat(defaultResult.deterministicScore()).isEqualByComparingTo("65.00");
        assertThat(defaultResult.matchPercentage()).isEqualTo(65);
        assertThat(defaultResult.matchingSkills()).containsExactly("Java");
        assertThat(defaultResult.missingSkills()).containsExactly("Kubernetes");

        // Custom weights emphasizing skills=80, sponsorship=20, others=0 -> (50*80 + 0*20)/100 = 40.00
        MatchingProperties customProps = new MatchingProperties();
        customProps.getWeights().setTechnicalSkills(80.0);
        customProps.getWeights().setExperience(0.0);
        customProps.getWeights().setJobTitle(0.0);
        customProps.getWeights().setLocation(0.0);
        customProps.getWeights().setSponsorship(20.0);
        customProps.getWeights().setSalary(0.0);

        DeterministicJobMatchingEngine customEngine =
                new DeterministicJobMatchingEngine(canonicalHashGenerator, jobNormalizer, customProps);
        DeterministicMatchResult customResult = customEngine.evaluate(
                new CandidateMatchContext(profile, skills, List.of(), preference),
                job,
                null
        );
        assertThat(customResult.deterministicScore()).isEqualByComparingTo("40.00");
        assertThat(customResult.matchPercentage()).isEqualTo(40);
    }

    @Test
    void evaluate_invalidZeroTotalWeights_throwsIllegalStateException() {
        MatchingProperties invalidProps = new MatchingProperties();
        invalidProps.getWeights().setTechnicalSkills(0);
        invalidProps.getWeights().setExperience(0);
        invalidProps.getWeights().setJobTitle(0);
        invalidProps.getWeights().setLocation(0);
        invalidProps.getWeights().setSponsorship(0);
        invalidProps.getWeights().setSalary(0);

        DeterministicJobMatchingEngine brokenEngine =
                new DeterministicJobMatchingEngine(canonicalHashGenerator, jobNormalizer, invalidProps);

        Job job = Job.builder().title("Java Engineer").country("Germany").description("Code").build();
        assertThatThrownBy(() -> brokenEngine.evaluate(CandidateMatchContext.empty(), job, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("positive");
    }

    @Test
    void evaluate_missingCandidateAndJobData_neverAssumesMissingDataIsMatch() {
        Job entryJobMissingExtras = Job.builder()
                .title("Graduate Software Engineer")
                .country("Germany")
                .workArrangement(WorkArrangement.ANY)
                .experienceLevel(ExperienceLevel.ENTRY)
                .minExperienceYears(BigDecimal.ZERO)
                .skills(List.of())
                .sponsorStatus(JobSponsorStatus.UNCONFIRMED)
                .description("General software engineering role for fresh graduates.")
                .build();

        DeterministicMatchResult emptyCandidateResult = engine.evaluate(
                CandidateMatchContext.empty(),
                entryJobMissingExtras,
                null
        );

        assertThat(emptyCandidateResult.deterministicScore()).isEqualByComparingTo("0.00");
        assertThat(emptyCandidateResult.finalScore()).isEqualByComparingTo("0.00");
        assertThat(emptyCandidateResult.matchPercentage()).isZero();
        assertThat(emptyCandidateResult.skillScore()).isEqualByComparingTo("0.00");
        assertThat(emptyCandidateResult.experienceScore()).isEqualByComparingTo("0.00");
        assertThat(emptyCandidateResult.titleScore()).isEqualByComparingTo("0.00");
        assertThat(emptyCandidateResult.locationScore()).isEqualByComparingTo("0.00");
        assertThat(emptyCandidateResult.sponsorshipScore()).isEqualByComparingTo("0.00");
        assertThat(emptyCandidateResult.salaryScore()).isEqualByComparingTo("0.00");
        assertThat(emptyCandidateResult.experienceCompatibility()).contains("Missing Data");
        assertThat(emptyCandidateResult.recommendationReasons()).hasSizeGreaterThanOrEqualTo(5);

        // Even with a candidate who needs sponsorship and lacks salary expectations, unconfirmed sponsorship & missing salary stay 0%
        UserProfile cand = UserProfile.builder()
                .yearsOfExperience(new BigDecimal("1.0"))
                .workAuthorizationStatus(WorkAuthorizationStatus.NEEDS_SPONSORSHIP)
                .build();
        UserJobPreference pref = UserJobPreference.builder()
                .requiresSponsorship(true)
                .preferredCountries(List.of())
                .preferredJobTitles(List.of())
                .workArrangement(WorkArrangement.ANY)
                .build();

        Job unconfirmedJob = Job.builder()
                .title("Backend Developer")
                .country("Netherlands")
                .experienceLevel(null)
                .minExperienceYears(null)
                .skills(List.of("Go", "Docker"))
                .sponsorStatus(JobSponsorStatus.UNCONFIRMED)
                .salaryMin(null)
                .salaryMax(null)
                .description("Backend role in Amsterdam.")
                .build();

        DeterministicMatchResult result = engine.evaluate(
                new CandidateMatchContext(cand, List.of(), List.of(), pref),
                unconfirmedJob,
                null
        );

        assertThat(result.skillScore()).isEqualByComparingTo("0.00");
        assertThat(result.missingSkills()).containsExactly("Go", "Docker");
        assertThat(result.experienceScore()).isEqualByComparingTo("0.00");
        assertThat(result.titleScore()).isEqualByComparingTo("0.00");
        assertThat(result.locationScore()).isEqualByComparingTo("0.00");
        assertThat(result.sponsorshipScore()).isEqualByComparingTo("0.00");
        assertThat(result.salaryScore()).isEqualByComparingTo("0.00");
        assertThat(result.deterministicScore()).isEqualByComparingTo("0.00");
    }

    @Test
    void evaluate_skillAliasesParentSkillsAndWorkHistoryExperience_handledAccurately() {
        UserProfile profile = UserProfile.builder()
                .currentDesignation("Backend Java Engineer")
                .yearsOfExperience(BigDecimal.ZERO)
                .workAuthorizationStatus(WorkAuthorizationStatus.EU_CITIZEN)
                .build();

        List<UserSkill> skills = List.of(
                UserSkill.builder().skillName("PostgreSQL").build(),
                UserSkill.builder().skillName("k8s").build(),
                UserSkill.builder().skillName("ReactJS").build(),
                UserSkill.builder().skillName("Spring Boot").build()
        );

        // Two overlapping/adjacent experiences totaling 5.0 years fallback when profile.yearsOfExperience is 0
        List<UserWorkExperience> experiences = List.of(
                UserWorkExperience.builder()
                        .companyName("FinTech Co")
                        .designation("Software Engineer")
                        .startDate(LocalDate.now().minusYears(5))
                        .endDate(LocalDate.now().minusYears(2))
                        .skillsUsed(List.of("Java 17"))
                        .build(),
                UserWorkExperience.builder()
                        .companyName("Cloud Co")
                        .designation("Senior Java Developer")
                        .startDate(LocalDate.now().minusYears(2))
                        .endDate(null)
                        .skillsUsed(List.of("Docker"))
                        .build()
        );

        Job job = Job.builder()
                .title("Senior Java Developer (m/w/d)")
                .country("DE")
                .city("München")
                .workArrangement(WorkArrangement.REMOTE)
                .experienceLevel(ExperienceLevel.SENIOR)
                .minExperienceYears(new BigDecimal("5.0"))
                .skills(List.of("Java 21", "SQL", "Kubernetes", "React.js", "Spring", "Docker", "Terraform"))
                .sponsorStatus(JobSponsorStatus.NO_SPONSORSHIP)
                .description("Cloud native backend systems.")
                .build();

        UserJobPreference preference = UserJobPreference.builder()
                .preferredJobTitles(List.of("Senior Java Engineer"))
                .preferredCountries(List.of("Germany"))
                .preferredCities(List.of("Munich"))
                .minSalary(new BigDecimal("100000.00"))
                .salaryCurrency("EUR")
                .requiresSponsorship(false)
                .workArrangement(WorkArrangement.REMOTE)
                .build();

        DeterministicMatchResult result = engine.evaluate(
                new CandidateMatchContext(profile, skills, experiences, preference),
                job,
                null
        );

        // 6 of 7 skills matched (missing only Terraform): 85.71%
        assertThat(result.matchingSkills()).containsExactly("Java 21", "SQL", "Kubernetes", "React.js", "Spring", "Docker");
        assertThat(result.missingSkills()).containsExactly("Terraform");
        assertThat(result.skillScore()).isEqualByComparingTo("85.71");

        // Derived ~5.0 yrs meets 5.0+ Senior -> 100.00
        assertThat(result.experienceScore()).isEqualByComparingTo("100.00");
        assertThat(result.experienceCompatibility()).contains("Strong Fit");

        // Title matches after (m/w/d) normalization & Developer->Engineer equivalence
        assertThat(result.titleScore()).isGreaterThanOrEqualTo(new BigDecimal("95.00"));

        // Country "DE" -> Germany & "München" -> Munich match cleanly
        assertThat(result.locationScore()).isEqualByComparingTo("100.00");

        // EU_CITIZEN holds work authorization even when posting says NO_SPONSORSHIP
        assertThat(result.sponsorshipScore()).isEqualByComparingTo("100.00");
    }

    @Test
    void evaluate_experienceCompatibilityBracketsAndTitleOpposition_scoreProportionally() {
        UserProfile underqualified = UserProfile.builder()
                .yearsOfExperience(new BigDecimal("4.0"))
                .workAuthorizationStatus(WorkAuthorizationStatus.NEEDS_SPONSORSHIP)
                .build();
        UserJobPreference pref = UserJobPreference.builder()
                .preferredJobTitles(List.of("Backend Engineer"))
                .preferredCountries(List.of("Netherlands"))
                .workArrangement(WorkArrangement.HYBRID)
                .requiresSponsorship(true)
                .build();

        Job leadFrontendJob = Job.builder()
                .title("Frontend Engineer")
                .country("Netherlands")
                .workArrangement(WorkArrangement.HYBRID)
                .experienceLevel(ExperienceLevel.SENIOR)
                .minExperienceYears(new BigDecimal("5.0"))
                .skills(List.of("Angular"))
                .sponsorStatus(JobSponsorStatus.RELOCATION_OFFERED)
                .description("Frontend architecture.")
                .build();

        DeterministicMatchResult closeFit = engine.evaluate(
                new CandidateMatchContext(underqualified, List.of(), List.of(), pref),
                leadFrontendJob,
                null
        );

        assertThat(closeFit.experienceScore()).isEqualByComparingTo("80.00");
        assertThat(closeFit.experienceCompatibility()).startsWith("Close Fit");
        // Opposing Backend vs Frontend role capped low
        assertThat(closeFit.titleScore()).isLessThanOrEqualTo(new BigDecimal("25.00"));
        assertThat(closeFit.sponsorshipScore()).isEqualByComparingTo("55.00");

        // Overqualified check: 14 years on Junior (1-4 yr) role
        UserProfile veteran = UserProfile.builder()
                .yearsOfExperience(new BigDecimal("14.0"))
                .workAuthorizationStatus(WorkAuthorizationStatus.PERMANENT_RESIDENT)
                .build();
        Job juniorJob = Job.builder()
                .title("Junior Java Engineer")
                .country("Netherlands")
                .experienceLevel(ExperienceLevel.JUNIOR)
                .minExperienceYears(new BigDecimal("1.0"))
                .skills(List.of("Java"))
                .sponsorStatus(JobSponsorStatus.UNCONFIRMED)
                .description("Junior Java developer.")
                .build();

        DeterministicMatchResult overqualifiedResult = engine.evaluate(
                new CandidateMatchContext(veteran, List.of(), List.of(), pref),
                juniorJob,
                null
        );
        assertThat(overqualifiedResult.experienceCompatibility()).startsWith("Overqualified");
        assertThat(overqualifiedResult.experienceScore()).isBetween(new BigDecimal("65.00"), new BigDecimal("80.00"));
    }

    @Test
    void evaluate_monthlySalaryConversionFxAndRelocationCap_appliedCorrectly() {
        UserProfile profile = UserProfile.builder()
                .yearsOfExperience(new BigDecimal("5.0"))
                .currentCountry("Netherlands")
                .relocationPreference(RelocationPreference.NOT_INTERESTED)
                .workAuthorizationStatus(WorkAuthorizationStatus.EU_CITIZEN)
                .build();

        UserJobPreference preference = UserJobPreference.builder()
                .preferredJobTitles(List.of("Java Engineer"))
                .preferredCountries(List.of("Switzerland"))
                .minSalary(new BigDecimal("84000.00"))
                .salaryCurrency("EUR")
                .workArrangement(WorkArrangement.ONSITE)
                .requiresSponsorship(false)
                .build();

        // 7,500 CHF / month * 12 * 1.05 FX = 94,500 EUR/yr >= 84,000 EUR target -> 100.00 salary
        // Onsite in Switzerland when user lives in Netherlands & relocation NOT_INTERESTED -> capped <= 20.00 location
        Job zurichOnsite = Job.builder()
                .title("Java Engineer")
                .country("Switzerland")
                .city("Zurich")
                .workArrangement(WorkArrangement.ONSITE)
                .experienceLevel(ExperienceLevel.SENIOR)
                .minExperienceYears(new BigDecimal("5.0"))
                .salaryMin(new BigDecimal("7500.00"))
                .salaryMax(new BigDecimal("8000.00"))
                .salaryCurrency("CHF")
                .salaryPeriod(SalaryPeriod.MONTHLY)
                .skills(List.of("Java"))
                .sponsorStatus(JobSponsorStatus.VERIFIED_SPONSOR)
                .description("Zurich role.")
                .build();

        DeterministicMatchResult res = engine.evaluate(
                new CandidateMatchContext(profile, List.of(), List.of(), preference),
                zurichOnsite,
                null,
                new BigDecimal("90.00")
        );

        assertThat(res.salaryScore()).isEqualByComparingTo("100.00");
        assertThat(res.locationScore()).isLessThanOrEqualTo(new BigDecimal("20.00"));
        assertThat(res.recommendationReasons()).anyMatch(r -> r.contains("CHF"));
        assertThat(res.recommendationReasons()).anyMatch(r -> r.contains("relocation"));
        // Blended finalScore averages deterministicScore with existing aiScore (90.00)
        BigDecimal expectedBlended = res.deterministicScore().add(new BigDecimal("90.00"))
                .divide(BigDecimal.valueOf(2), 2, java.math.RoundingMode.HALF_UP);
        assertThat(res.finalScore()).isEqualByComparingTo(expectedBlended);
    }

    @Test
    void evaluate_compositeHeadlineAndExpandedFrontendSkills_matchPreferredAndDescriptionExtractedSkills() {
        UserProfile profile = UserProfile.builder()
                .currentDesignation("Angular Lead | Frontend Lead | Senior Angular Developer")
                .yearsOfExperience(new BigDecimal("9.5"))
                .currentCountry("Germany")
                .workAuthorizationStatus(WorkAuthorizationStatus.NEEDS_SPONSORSHIP)
                .build();

        List<UserSkill> skills = List.of(
                UserSkill.builder().skillName("Angular").normalizedSkill("angular").build(),
                UserSkill.builder().skillName("RxJS").normalizedSkill("rxjs").build(),
                UserSkill.builder().skillName("NgRx").normalizedSkill("ngrx").build(),
                UserSkill.builder().skillName("HTML5").normalizedSkill("html5").build(),
                UserSkill.builder().skillName("CSS3").normalizedSkill("css3").build(),
                UserSkill.builder().skillName("Micro Front-End Architecture").normalizedSkill("micro front-end architecture").build()
        );

        UserJobPreference preference = UserJobPreference.builder()
                .preferredJobTitles(List.of("Engineering Manager"))
                .preferredCountries(List.of("Germany"))
                .workArrangement(WorkArrangement.HYBRID)
                .requiresSponsorship(true)
                .build();

        Job angularJob = Job.builder()
                .title("Senior Angular Developer")
                .country("Germany")
                .city("Berlin")
                .workArrangement(WorkArrangement.HYBRID)
                .experienceLevel(ExperienceLevel.SENIOR)
                .skills(List.of())
                .sponsorStatus(JobSponsorStatus.UNCONFIRMED)
                .description("Build enterprise portals using Angular, RxJS, NgRx, HTML5, SCSS, and micro-frontends.")
                .build();

        DeterministicMatchResult angularResult = engine.evaluate(
                new CandidateMatchContext(profile, skills, List.of(), preference),
                angularJob,
                null
        );

        assertThat(angularResult.titleScore()).isEqualByComparingTo("100.00");
        assertThat(angularResult.skillScore()).isEqualByComparingTo("100.00");
        assertThat(angularResult.matchingSkills())
                .containsExactly("Angular", "RxJS", "NgRx", "HTML", "CSS", "Micro-Frontends");
        assertThat(angularResult.missingSkills()).isEmpty();
        assertThat(angularResult.experienceScore()).isEqualByComparingTo("100.00");
        assertThat(angularResult.recommendationReasons())
                .noneMatch(reason -> reason.contains("No preferred job titles"));

        Job managerJob = Job.builder()
                .title("Engineering Manager")
                .country("Germany")
                .workArrangement(WorkArrangement.HYBRID)
                .skills(List.of("Angular"))
                .sponsorStatus(JobSponsorStatus.UNCONFIRMED)
                .description("Lead frontend teams.")
                .build();

        DeterministicMatchResult managerResult = engine.evaluate(
                new CandidateMatchContext(profile, skills, List.of(), preference),
                managerJob,
                null
        );
        assertThat(managerResult.titleScore()).isEqualByComparingTo("100.00");
    }
}

