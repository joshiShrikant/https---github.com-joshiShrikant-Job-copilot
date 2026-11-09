package com.eurojobcopilot.modules.matching.controller;

import com.eurojobcopilot.core.enums.WorkArrangement;
import com.eurojobcopilot.modules.auth.dto.RegisterRequest;
import com.eurojobcopilot.modules.auth.entity.User;
import com.eurojobcopilot.modules.auth.repository.RefreshTokenRepository;
import com.eurojobcopilot.modules.auth.repository.UserRepository;
import com.eurojobcopilot.modules.job.entity.EmploymentType;
import com.eurojobcopilot.modules.job.entity.ExperienceLevel;
import com.eurojobcopilot.modules.job.entity.Job;
import com.eurojobcopilot.modules.job.entity.JobSponsorStatus;
import com.eurojobcopilot.modules.job.entity.SalaryPeriod;
import com.eurojobcopilot.modules.job.repository.JobRepository;
import com.eurojobcopilot.modules.matching.entity.JobMatchScore;
import com.eurojobcopilot.modules.matching.repository.JobMatchScoreRepository;
import com.eurojobcopilot.modules.sponsorship.entity.Company;
import com.eurojobcopilot.modules.sponsorship.entity.SponsorConfidenceLevel;
import com.eurojobcopilot.modules.sponsorship.repository.CompanyRepository;
import com.eurojobcopilot.modules.user.entity.ProficiencyLevel;
import com.eurojobcopilot.modules.user.entity.RelocationPreference;
import com.eurojobcopilot.modules.user.entity.UserJobPreference;
import com.eurojobcopilot.modules.user.entity.UserProfile;
import com.eurojobcopilot.modules.user.entity.UserSkill;
import com.eurojobcopilot.modules.user.entity.WorkAuthorizationStatus;
import com.eurojobcopilot.modules.user.repository.UserJobPreferenceRepository;
import com.eurojobcopilot.modules.user.repository.UserProfileRepository;
import com.eurojobcopilot.modules.user.repository.UserSkillRepository;
import com.eurojobcopilot.modules.user.repository.UserWorkExperienceRepository;
import com.eurojobcopilot.support.TestEntityFactory;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class JobMatchingControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JobMatchScoreRepository jobMatchScoreRepository;

    @Autowired
    private UserSkillRepository userSkillRepository;

    @Autowired
    private UserWorkExperienceRepository userWorkExperienceRepository;

    @Autowired
    private UserJobPreferenceRepository userJobPreferenceRepository;

    @Autowired
    private UserProfileRepository userProfileRepository;

    @Autowired
    private JobRepository jobRepository;

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private UserRepository userRepository;

    private String tokenCompleteUser;
    private String tokenEmptyUser;
    private User completeUser;
    private User emptyUser;

    @BeforeEach
    void setUp() throws Exception {
        cleanDatabase();
        tokenCompleteUser = registerUser("match.complete@eurojob.eu", "Lukas", "Meyer");
        tokenEmptyUser = registerUser("match.empty@eurojob.eu", "Sofia", "Klein");
        completeUser = userRepository.findByEmailIgnoreCase("match.complete@eurojob.eu").orElseThrow();
        emptyUser = userRepository.findByEmailIgnoreCase("match.empty@eurojob.eu").orElseThrow();

        userProfileRepository.saveAndFlush(UserProfile.builder()
                .user(completeUser)
                .currentDesignation("Senior Java & Spring Boot Engineer")
                .yearsOfExperience(new BigDecimal("6.0"))
                .currentCountry("India")
                .expectedSalaryMin(new BigDecimal("80000.00"))
                .expectedSalaryCurrency("EUR")
                .workAuthorizationStatus(WorkAuthorizationStatus.NEEDS_SPONSORSHIP)
                .relocationPreference(RelocationPreference.WILLING_TO_RELOCATE)
                .build());

        userSkillRepository.saveAllAndFlush(List.of(
                UserSkill.builder()
                        .user(completeUser)
                        .skillName("Java")
                        .normalizedSkill("java")
                        .yearsExperience(new BigDecimal("6.0"))
                        .proficiencyLevel(ProficiencyLevel.EXPERT)
                        .primary(true)
                        .build(),
                UserSkill.builder()
                        .user(completeUser)
                        .skillName("Spring Boot")
                        .normalizedSkill("spring boot")
                        .yearsExperience(new BigDecimal("5.0"))
                        .proficiencyLevel(ProficiencyLevel.EXPERT)
                        .primary(true)
                        .build(),
                UserSkill.builder()
                        .user(completeUser)
                        .skillName("PostgreSQL")
                        .normalizedSkill("postgresql")
                        .yearsExperience(new BigDecimal("4.0"))
                        .proficiencyLevel(ProficiencyLevel.ADVANCED)
                        .build()
        ));

        userJobPreferenceRepository.saveAndFlush(UserJobPreference.builder()
                .user(completeUser)
                .preferredJobTitles(List.of("Senior Java Engineer"))
                .preferredCountries(List.of("Netherlands", "Germany"))
                .preferredCities(List.of("Amsterdam"))
                .minSalary(new BigDecimal("85000.00"))
                .salaryCurrency("EUR")
                .requiresSponsorship(true)
                .workArrangement(WorkArrangement.HYBRID)
                .minMatchScore(70)
                .build());
    }

    @AfterEach
    void cleanDatabase() {
        jobMatchScoreRepository.deleteAllInBatch();
        userSkillRepository.deleteAllInBatch();
        userWorkExperienceRepository.deleteAllInBatch();
        userJobPreferenceRepository.deleteAllInBatch();
        userProfileRepository.deleteAllInBatch();
        jobRepository.deleteAllInBatch();
        companyRepository.deleteAllInBatch();
        refreshTokenRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
    }

    @Test
    void evaluateJobMatchScore_enforcesAuthValidationIsolatesUsersAndPersistsIdempotently() throws Exception {
        Company adyen = companyRepository.saveAndFlush(Company.builder()
                .name("Adyen N.V.")
                .normalizedName("adyen")
                .headquartersCountry("Netherlands")
                .verifiedSponsor(true)
                .sponsorConfidenceLevel(SponsorConfidenceLevel.VERIFIED)
                .build());

        Job adyenJob = jobRepository.saveAndFlush(Job.builder()
                .canonicalHash(TestEntityFactory.sha256Hex("matching-adyen-java"))
                .company(adyen)
                .companyName("Adyen N.V.")
                .title("Senior Java Engineer")
                .normalizedTitle("senior java engineer")
                .location("Amsterdam, Netherlands")
                .city("Amsterdam")
                .country("Netherlands")
                .workArrangement(WorkArrangement.HYBRID)
                .employmentType(EmploymentType.FULL_TIME)
                .experienceLevel(ExperienceLevel.SENIOR)
                .minExperienceYears(new BigDecimal("5.0"))
                .salaryMin(new BigDecimal("90000.00"))
                .salaryMax(new BigDecimal("115000.00"))
                .salaryCurrency("EUR")
                .salaryPeriod(SalaryPeriod.YEARLY)
                .skills(List.of("Java 21", "Spring Boot", "Postgres", "Kafka"))
                .description("Distributed payment APIs with IND sponsorship.")
                .sourceProvider("ARBEITNOW")
                .originalUrl("https://careers.adyen.com/1")
                .sponsorStatus(JobSponsorStatus.VERIFIED_SPONSOR)
                .postedAt(Instant.now().minus(1, ChronoUnit.HOURS))
                .active(true)
                .build());

        mockMvc.perform(get("/api/v1/jobs/" + adyenJob.getId() + "/intelligence/match-score"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));

        mockMvc.perform(get("/api/v1/jobs/0/intelligence/match-score")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenCompleteUser))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        mockMvc.perform(get("/api/v1/jobs/999999/intelligence/match-score")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenCompleteUser))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));

        // Complete user scores high: 3/4 skills (75%) * 0.4 + 100*0.6 = 30 + 60 = 90.00
        mockMvc.perform(get("/api/v1/jobs/" + adyenJob.getId() + "/intelligence/match-score")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenCompleteUser))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deterministicScore").value(90.0))
                .andExpect(jsonPath("$.finalScore").value(90.0))
                .andExpect(jsonPath("$.matchPercentage").value(90))
                .andExpect(jsonPath("$.skillScore").value(75.0))
                .andExpect(jsonPath("$.experienceScore").value(100.0))
                .andExpect(jsonPath("$.titleScore").value(100.0))
                .andExpect(jsonPath("$.locationScore").value(100.0))
                .andExpect(jsonPath("$.sponsorshipScore").value(100.0))
                .andExpect(jsonPath("$.salaryScore").value(100.0))
                .andExpect(jsonPath("$.matchingSkills.length()").value(3))
                .andExpect(jsonPath("$.missingSkills.length()").value(1))
                .andExpect(jsonPath("$.missingSkills[0]").value("Kafka"))
                .andExpect(jsonPath("$.experienceCompatibility").value("Strong Fit · 6 yrs meets 5+ yrs (Senior)"))
                .andExpect(jsonPath("$.recommendationReasons").isArray());

        // Empty user scores strictly 0% with zero false assumptions
        mockMvc.perform(get("/api/v1/jobs/" + adyenJob.getId() + "/intelligence/match-score")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenEmptyUser))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deterministicScore").value(0.0))
                .andExpect(jsonPath("$.matchPercentage").value(0))
                .andExpect(jsonPath("$.matchingSkills.length()").value(0))
                .andExpect(jsonPath("$.missingSkills.length()").value(4));

        // Add Kafka to completeUser and recalculate idempotently -> upgrades to 100% without duplicate rows
        userSkillRepository.saveAndFlush(UserSkill.builder()
                .user(completeUser)
                .skillName("Kafka")
                .normalizedSkill("kafka")
                .yearsExperience(new BigDecimal("2.0"))
                .proficiencyLevel(ProficiencyLevel.INTERMEDIATE)
                .build());

        mockMvc.perform(get("/api/v1/jobs/" + adyenJob.getId() + "/intelligence/match-score")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenCompleteUser))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.matchPercentage").value(100))
                .andExpect(jsonPath("$.missingSkills.length()").value(0));

        assertThat(jobMatchScoreRepository.count()).isEqualTo(2L);

        // GET /api/v1/jobs/{id} now returns persisted experienceCompatibility & recommendationReasons
        mockMvc.perform(get("/api/v1/jobs/" + adyenJob.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenCompleteUser))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.matchPercentage").value(100))
                .andExpect(jsonPath("$.matchBreakdown.experienceCompatibility").value("Strong Fit · 6 yrs meets 5+ yrs (Senior)"))
                .andExpect(jsonPath("$.matchBreakdown.recommendationReasons").isArray());
    }

    @Test
    void recalculateActiveJobMatches_scoresOnlyActiveJobsAndIntegratesWithJobSearchAndDashboard() throws Exception {
        Job strongJavaJob = jobRepository.saveAndFlush(Job.builder()
                .canonicalHash(TestEntityFactory.sha256Hex("batch-strong-java"))
                .companyName("Booking.com")
                .title("Senior Java Engineer")
                .normalizedTitle("senior java engineer")
                .country("Netherlands")
                .city("Amsterdam")
                .workArrangement(WorkArrangement.HYBRID)
                .experienceLevel(ExperienceLevel.SENIOR)
                .minExperienceYears(new BigDecimal("5.0"))
                .salaryMin(new BigDecimal("90000.00"))
                .salaryMax(new BigDecimal("110000.00"))
                .salaryCurrency("EUR")
                .salaryPeriod(SalaryPeriod.YEARLY)
                .skills(List.of("Java", "Spring Boot", "PostgreSQL"))
                .sponsorStatus(JobSponsorStatus.VERIFIED_SPONSOR)
                .description("High match Java role.")
                .sourceProvider("ARBEITNOW")
                .originalUrl("https://careers.booking.com/1")
                .postedAt(Instant.now().minus(2, ChronoUnit.HOURS))
                .active(true)
                .build());

        Job weakPhpJob = jobRepository.saveAndFlush(Job.builder()
                .canonicalHash(TestEntityFactory.sha256Hex("batch-weak-php"))
                .companyName("Legacy Agency")
                .title("Senior PHP Developer")
                .normalizedTitle("senior php developer")
                .country("Spain")
                .city("Madrid")
                .workArrangement(WorkArrangement.ONSITE)
                .experienceLevel(ExperienceLevel.SENIOR)
                .minExperienceYears(new BigDecimal("5.0"))
                .skills(List.of("PHP", "Laravel", "Symfony"))
                .sponsorStatus(JobSponsorStatus.NO_SPONSORSHIP)
                .description("PHP legacy system.")
                .sourceProvider("ADZUNA")
                .originalUrl("https://jobs.example.eu/php")
                .postedAt(Instant.now().minus(1, ChronoUnit.HOURS))
                .active(true)
                .build());

        // Inactive archived job must be excluded from batch scoring
        jobRepository.saveAndFlush(Job.builder()
                .canonicalHash(TestEntityFactory.sha256Hex("batch-inactive"))
                .companyName("Closed Corp")
                .title("Java Engineer")
                .country("Netherlands")
                .description("Closed.")
                .sourceProvider("ADZUNA")
                .originalUrl("https://jobs.example.eu/closed")
                .postedAt(Instant.now().minus(40, ChronoUnit.DAYS))
                .active(false)
                .build());

        mockMvc.perform(post("/api/v1/matching/recalculate"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/v1/matching/recalculate")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenCompleteUser))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.evaluatedJobsCount").value(2))
                .andExpect(jsonPath("$.highMatchJobsCount").value(1))
                .andExpect(jsonPath("$.minMatchThreshold").value(70))
                .andExpect(jsonPath("$.weightsUsed.technicalSkills").value(40.0))
                .andExpect(jsonPath("$.topMatches.length()").value(2))
                .andExpect(jsonPath("$.topMatches[0].jobId").value(strongJavaJob.getId()))
                .andExpect(jsonPath("$.topMatches[0].matchPercentage").value(100))
                .andExpect(jsonPath("$.topMatches[1].jobId").value(weakPhpJob.getId()));

        // Verify job search sorting by matchScore,desc and minMatchScore filtering work seamlessly
        mockMvc.perform(get("/api/v1/jobs")
                        .param("sort", "matchScore,desc")
                        .param("minMatchScore", "70")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenCompleteUser))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(strongJavaJob.getId()))
                .andExpect(jsonPath("$.content[0].matchPercentage").value(100));

        // Verify dashboard telemetry picks up recalculated highMatchJobsCount
        mockMvc.perform(get("/api/v1/dashboard/summary")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenCompleteUser))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.highMatchJobsCount").value(1));
    }

    @Test
    void profileSkillWorkExpAndPreferenceMutations_recalculateScoresAutomaticallyAndPreserveAiScore() throws Exception {
        String token = registerUser("auto.mutate@eurojob.eu", "Shrikant", "Joshi");
        User user = userRepository.findByEmailIgnoreCase("auto.mutate@eurojob.eu").orElseThrow();

        Job angularJob = jobRepository.saveAndFlush(Job.builder()
                .canonicalHash(TestEntityFactory.sha256Hex("auto-mutate-angular"))
                .companyName("Berlin Frontend GmbH")
                .title("Senior Angular Developer")
                .normalizedTitle("senior angular developer")
                .country("Germany")
                .city("Berlin")
                .workArrangement(WorkArrangement.HYBRID)
                .experienceLevel(ExperienceLevel.SENIOR)
                .minExperienceYears(new BigDecimal("5.0"))
                .salaryMin(new BigDecimal("90000.00"))
                .salaryMax(new BigDecimal("110000.00"))
                .salaryCurrency("EUR")
                .salaryPeriod(SalaryPeriod.YEARLY)
                .skills(List.of())
                .sponsorStatus(JobSponsorStatus.VERIFIED_SPONSOR)
                .description("Join our team working with Angular 17, RxJS, NgRx, HTML5, SCSS, and micro-frontends.")
                .sourceProvider("ADZUNA")
                .originalUrl("https://jobs.example.eu/angular-lead")
                .postedAt(Instant.now().minus(1, ChronoUnit.HOURS))
                .active(true)
                .build());

        // Pre-seed a stale 0% score with existing AI evaluation that must be preserved across mutations
        jobMatchScoreRepository.saveAndFlush(JobMatchScore.builder()
                .user(user)
                .job(angularJob)
                .deterministicScore(BigDecimal.ZERO)
                .aiScore(new BigDecimal("88.00"))
                .finalScore(new BigDecimal("44.00"))
                .aiRationale("Preserved AI evaluation.")
                .calculatedAt(Instant.now().minus(2, ChronoUnit.HOURS))
                .build());

        // 1. Update profile via PUT /api/v1/profile -> auto-recalculates experience, headline title, location, salary, and keeps AI score
        String profilePayload = """
                {
                  "firstName": "Shrikant",
                  "lastName": "Joshi",
                  "currentDesignation": "Angular Lead | Frontend Lead | Senior Angular Developer",
                  "yearsOfExperience": 9.5,
                  "currentCountry": "India",
                  "expectedSalaryMin": 80000.00,
                  "expectedSalaryCurrency": "EUR",
                  "workAuthorizationStatus": "NEEDS_SPONSORSHIP",
                  "relocationPreference": "WILLING_TO_RELOCATE",
                  "preferredJobTitles": ["Engineering Manager"],
                  "preferredCountries": ["Germany"]
                }
                """;
        mockMvc.perform(put("/api/v1/profile")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(profilePayload))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/jobs/" + angularJob.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.matchBreakdown.titleScore").value(100.0))
                .andExpect(jsonPath("$.matchBreakdown.experienceScore").value(100.0))
                .andExpect(jsonPath("$.matchBreakdown.salaryScore").value(100.0))
                .andExpect(jsonPath("$.matchBreakdown.aiScore").value(88.0))
                .andExpect(jsonPath("$.matchBreakdown.aiRationale").value("Preserved AI evaluation."));

        // 2. Add matching frontend skills via POST /api/v1/profile/skills -> skillScore jumps to 100% automatically
        Long angularSkillId = null;
        for (String skillName : List.of("Angular", "RxJS", "NgRx", "HTML5", "CSS3", "Micro Front-End Architecture")) {
            MvcResult res = mockMvc.perform(post("/api/v1/profile/skills")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"skillName":"%s","yearsExperience":5.0,"proficiencyLevel":"EXPERT","primary":true}
                                    """.formatted(skillName)))
                    .andExpect(status().isCreated())
                    .andReturn();
            if ("Angular".equals(skillName)) {
                angularSkillId = objectMapper.readTree(res.getResponse().getContentAsString()).get("id").asLong();
            }
        }

        mockMvc.perform(get("/api/v1/jobs/" + angularJob.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.matchBreakdown.skillScore").value(100.0));

        // 3. Delete one skill -> skillScore drops automatically
        mockMvc.perform(delete("/api/v1/profile/skills/" + angularSkillId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/jobs/" + angularJob.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.matchBreakdown.skillScore").value(83.33));

        // 4. Add work experience & set profile yearsOfExperience=0 -> derived experience recalculates on experience mutation
        mockMvc.perform(put("/api/v1/profile")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"yearsOfExperience\":0}"))
                .andExpect(status().isOk());

        MvcResult expRes = mockMvc.perform(post("/api/v1/profile/experience")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "companyName": "Ascendion",
                                  "designation": "Angular Lead",
                                  "startDate": "2018-01-01",
                                  "current": true
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        long expId = objectMapper.readTree(expRes.getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(get("/api/v1/jobs/" + angularJob.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.matchBreakdown.experienceScore").value(100.0));

        mockMvc.perform(delete("/api/v1/profile/experience/" + expId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/jobs/" + angularJob.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.matchBreakdown.experienceScore").value(15.0));

        // 5. Update preferences -> location and salary scores recalculate immediately
        mockMvc.perform(put("/api/v1/preferences")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "preferredJobTitles": ["Senior Angular Developer"],
                                  "preferredCountries": ["Germany"],
                                  "preferredCities": ["Berlin"],
                                  "minSalary": 100000.00,
                                  "salaryCurrency": "EUR",
                                  "requiresSponsorship": true,
                                  "workArrangement": "HYBRID"
                                }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/jobs/" + angularJob.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.matchBreakdown.locationScore").value(100.0))
                .andExpect(jsonPath("$.matchBreakdown.salaryScore").value(87.5));
    }

    @Test
    void readEndpoints_selfHealMissingAndStaleScoresLazilyWithoutManualRecalculation() throws Exception {
        String token = registerUser("lazy.heal@eurojob.eu", "Elena", "Weber");
        User user = userRepository.findByEmailIgnoreCase("lazy.heal@eurojob.eu").orElseThrow();

        userProfileRepository.saveAndFlush(UserProfile.builder()
                .user(user)
                .currentDesignation("Senior Java Engineer")
                .yearsOfExperience(new BigDecimal("6.0"))
                .currentCountry("India")
                .expectedSalaryMin(new BigDecimal("85000.00"))
                .expectedSalaryCurrency("EUR")
                .workAuthorizationStatus(WorkAuthorizationStatus.NEEDS_SPONSORSHIP)
                .relocationPreference(RelocationPreference.WILLING_TO_RELOCATE)
                .build());

        userSkillRepository.saveAllAndFlush(List.of(
                UserSkill.builder()
                        .user(user)
                        .skillName("Java")
                        .normalizedSkill("java")
                        .yearsExperience(new BigDecimal("6.0"))
                        .proficiencyLevel(ProficiencyLevel.EXPERT)
                        .primary(true)
                        .build(),
                UserSkill.builder()
                        .user(user)
                        .skillName("Spring Boot")
                        .normalizedSkill("spring boot")
                        .yearsExperience(new BigDecimal("5.0"))
                        .proficiencyLevel(ProficiencyLevel.EXPERT)
                        .primary(true)
                        .build()
        ));

        userJobPreferenceRepository.saveAndFlush(UserJobPreference.builder()
                .user(user)
                .preferredJobTitles(List.of("Senior Java Engineer"))
                .preferredCountries(List.of("Netherlands"))
                .preferredCities(List.of("Amsterdam"))
                .minSalary(new BigDecimal("85000.00"))
                .salaryCurrency("EUR")
                .requiresSponsorship(true)
                .workArrangement(WorkArrangement.HYBRID)
                .minMatchScore(70)
                .build());

        Job adyenJob = jobRepository.saveAndFlush(Job.builder()
                .canonicalHash(TestEntityFactory.sha256Hex("lazy-heal-adyen"))
                .companyName("Adyen N.V.")
                .title("Senior Java Engineer")
                .normalizedTitle("senior java engineer")
                .country("Netherlands")
                .city("Amsterdam")
                .workArrangement(WorkArrangement.HYBRID)
                .experienceLevel(ExperienceLevel.SENIOR)
                .minExperienceYears(new BigDecimal("5.0"))
                .salaryMin(new BigDecimal("95000.00"))
                .salaryMax(new BigDecimal("115000.00"))
                .salaryCurrency("EUR")
                .salaryPeriod(SalaryPeriod.YEARLY)
                .skills(List.of("Java", "Spring Boot"))
                .sponsorStatus(JobSponsorStatus.VERIFIED_SPONSOR)
                .description("Adyen platform payments team.")
                .sourceProvider("ARBEITNOW")
                .originalUrl("https://careers.adyen.com/lazy")
                .postedAt(Instant.now().minus(1, ChronoUnit.HOURS))
                .active(true)
                .build());

        assertThat(jobMatchScoreRepository.findByUserIdAndJobId(user.getId(), adyenJob.getId())).isEmpty();

        // Missing scores self-heal on GET /api/v1/jobs
        mockMvc.perform(get("/api/v1/jobs")
                        .param("sort", "matchScore,desc")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].matchPercentage").value(100));

        // Simulate a newly ingested active job added after initial calculation -> self-heals on GET /api/v1/jobs/{id}
        Job newlyIngestedJob = jobRepository.saveAndFlush(Job.builder()
                .canonicalHash(TestEntityFactory.sha256Hex("lazy-heal-new-job"))
                .companyName("Booking.com")
                .title("Senior Java Engineer")
                .normalizedTitle("senior java engineer")
                .country("Netherlands")
                .city("Amsterdam")
                .workArrangement(WorkArrangement.HYBRID)
                .experienceLevel(ExperienceLevel.SENIOR)
                .skills(List.of("Java", "Spring Boot"))
                .sponsorStatus(JobSponsorStatus.VERIFIED_SPONSOR)
                .description("Payments backend.")
                .sourceProvider("ADZUNA")
                .originalUrl("https://careers.booking.com/lazy")
                .postedAt(Instant.now())
                .active(true)
                .build());

        mockMvc.perform(get("/api/v1/jobs/" + newlyIngestedJob.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.matchPercentage").value(95))
                .andExpect(jsonPath("$.matchBreakdown.skillScore").value(100.0));

        // Simulate stale score rows (calculated before profile/job update) -> self-heals on dashboard & autopilot reads
        JobMatchScore staleScore = jobMatchScoreRepository.findByUserIdAndJobId(user.getId(), adyenJob.getId()).orElseThrow();
        staleScore.setFinalScore(BigDecimal.ZERO);
        staleScore.setDeterministicScore(BigDecimal.ZERO);
        staleScore.setCalculatedAt(Instant.now().minus(3, ChronoUnit.HOURS));
        jobMatchScoreRepository.saveAndFlush(staleScore);

        mockMvc.perform(get("/api/v1/dashboard/summary")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.highMatchJobsCount").value(2));

        mockMvc.perform(get("/api/v1/autopilot/recommended-jobs")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    private String registerUser(String email, String firstName, String lastName) throws Exception {
        RegisterRequest register = RegisterRequest.builder()
                .email(email)
                .password("SecureP@ssw0rd2026!")
                .firstName(firstName)
                .lastName(lastName)
                .build();

        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(register)))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        return json.get("accessToken").asText();
    }
}

