package com.eurojobcopilot.modules.matching.ai.controller;

import com.eurojobcopilot.core.enums.WorkArrangement;
import com.eurojobcopilot.modules.ai.client.AiJsonPromptRequest;
import com.eurojobcopilot.modules.ai.service.AiGatewayService;
import com.eurojobcopilot.modules.ai.service.AiStructuredResponse;
import com.eurojobcopilot.modules.auth.dto.RegisterRequest;
import com.eurojobcopilot.modules.auth.entity.User;
import com.eurojobcopilot.modules.auth.repository.RefreshTokenRepository;
import com.eurojobcopilot.modules.auth.repository.UserRepository;
import com.eurojobcopilot.modules.job.entity.EmploymentType;
import com.eurojobcopilot.modules.job.entity.ExperienceLevel;
import com.eurojobcopilot.modules.job.entity.Job;
import com.eurojobcopilot.modules.job.entity.JobAiAnalysis;
import com.eurojobcopilot.modules.job.entity.JobSponsorStatus;
import com.eurojobcopilot.modules.job.entity.SalaryPeriod;
import com.eurojobcopilot.modules.job.repository.JobAiAnalysisRepository;
import com.eurojobcopilot.modules.job.repository.JobRepository;
import com.eurojobcopilot.modules.matching.ai.model.RawAiSemanticMatchPayload;
import com.eurojobcopilot.modules.matching.entity.JobMatchScore;
import com.eurojobcopilot.modules.matching.repository.JobMatchScoreRepository;
import com.eurojobcopilot.modules.resume.entity.Resume;
import com.eurojobcopilot.modules.resume.entity.ResumeType;
import com.eurojobcopilot.modules.resume.entity.StorageProvider;
import com.eurojobcopilot.modules.resume.repository.ResumeRepository;
import com.eurojobcopilot.modules.sponsorship.entity.Company;
import com.eurojobcopilot.modules.sponsorship.entity.SponsorConfidenceLevel;
import com.eurojobcopilot.modules.sponsorship.repository.CompanyRepository;
import com.eurojobcopilot.modules.user.entity.ProficiencyLevel;
import com.eurojobcopilot.modules.user.entity.RelocationPreference;
import com.eurojobcopilot.modules.user.entity.UserEducation;
import com.eurojobcopilot.modules.user.entity.UserJobPreference;
import com.eurojobcopilot.modules.user.entity.UserProfile;
import com.eurojobcopilot.modules.user.entity.UserSkill;
import com.eurojobcopilot.modules.user.entity.UserWorkExperience;
import com.eurojobcopilot.modules.user.entity.WorkAuthorizationStatus;
import com.eurojobcopilot.modules.user.repository.UserEducationRepository;
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
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SemanticJobMatchingControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private JobMatchScoreRepository jobMatchScoreRepository;
    @Autowired
    private ResumeRepository resumeRepository;
    @Autowired
    private UserEducationRepository userEducationRepository;
    @Autowired
    private UserWorkExperienceRepository userWorkExperienceRepository;
    @Autowired
    private UserSkillRepository userSkillRepository;
    @Autowired
    private UserJobPreferenceRepository userJobPreferenceRepository;
    @Autowired
    private UserProfileRepository userProfileRepository;
    @Autowired
    private JobAiAnalysisRepository jobAiAnalysisRepository;
    @Autowired
    private JobRepository jobRepository;
    @Autowired
    private CompanyRepository companyRepository;
    @Autowired
    private RefreshTokenRepository refreshTokenRepository;
    @Autowired
    private UserRepository userRepository;

    @MockitoBean
    private AiGatewayService aiGatewayService;

    private String tokenCompleteUser;
    private String tokenEmptyUser;
    private User completeUser;
    private Job adyenJob;

    @BeforeEach
    void setUp() throws Exception {
        cleanDatabase();
        tokenCompleteUser = registerUser("semantic.match@eurojob.eu", "Lukas", "Weber");
        tokenEmptyUser = registerUser("semantic.empty@eurojob.eu", "Marta", "Novak");
        completeUser = userRepository.findByEmailIgnoreCase("semantic.match@eurojob.eu").orElseThrow();

        userProfileRepository.saveAndFlush(UserProfile.builder()
                .user(completeUser)
                .currentDesignation("Senior Java & Spring Boot Engineer")
                .yearsOfExperience(new BigDecimal("6.0"))
                .currentCountry("India")
                .currentCity("Bengaluru")
                .expectedSalaryMin(new BigDecimal("85000.00"))
                .expectedSalaryCurrency("EUR")
                .workAuthorizationStatus(WorkAuthorizationStatus.NEEDS_SPONSORSHIP)
                .relocationPreference(RelocationPreference.WILLING_TO_RELOCATE)
                .githubUrl("https://github.com/lukas-weber")
                .bio("7 years building distributed European FinTech settlement engines.")
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

        userWorkExperienceRepository.saveAndFlush(UserWorkExperience.builder()
                .user(completeUser)
                .companyName("ClearPay Systems")
                .designation("Senior Java Engineer")
                .location("Remote")
                .startDate(LocalDate.of(2021, 3, 1))
                .current(true)
                .description("Built SEPA Instant ledger project processing 5,000 TPS on Spring Boot.")
                .skillsUsed(List.of("Java 21", "Spring Boot", "PostgreSQL"))
                .build());

        userEducationRepository.saveAndFlush(UserEducation.builder()
                .user(completeUser)
                .institution("IIT Bombay")
                .degree("B.Tech")
                .fieldOfStudy("Computer Science")
                .startDate(LocalDate.of(2014, 7, 1))
                .endDate(LocalDate.of(2018, 5, 31))
                .country("India")
                .build());

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

        resumeRepository.saveAndFlush(Resume.builder()
                .user(completeUser)
                .resumeType(ResumeType.MASTER)
                .title("Master Backend Java Resume 2026")
                .fileName("master-backend-2026.pdf")
                .filePath("users/" + completeUser.getUuid() + "/master-backend-2026.pdf")
                .fileSizeBytes(24500L)
                .mimeType("application/pdf")
                .storageProvider(StorageProvider.LOCAL)
                .versionNumber(2)
                .masterApproved(true)
                .parsedContentJson(Map.of(
                        "summary", "Senior FinTech Java engineer",
                        "projects", List.of("SEPA Instant Payment Clearing Core", "Zero-downtime PostgreSQL sharding")
                ))
                .build());

        Company adyen = companyRepository.saveAndFlush(Company.builder()
                .name("Adyen N.V.")
                .normalizedName("adyen")
                .headquartersCountry("Netherlands")
                .verifiedSponsor(true)
                .sponsorConfidenceLevel(SponsorConfidenceLevel.VERIFIED)
                .industry("FinTech")
                .build());

        adyenJob = jobRepository.saveAndFlush(Job.builder()
                .canonicalHash(TestEntityFactory.sha256Hex("semantic-adyen-java"))
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
                .skills(List.of("Java 21", "Spring Boot", "PostgreSQL", "Kafka"))
                .description("Design resilient multi-currency payment clearing APIs in Amsterdam with IND sponsorship.")
                .sourceProvider("ARBEITNOW")
                .originalUrl("https://careers.adyen.com/semantic-1")
                .sponsorStatus(JobSponsorStatus.VERIFIED_SPONSOR)
                .postedAt(Instant.now().minus(1, ChronoUnit.HOURS))
                .active(true)
                .build());

        jobAiAnalysisRepository.saveAndFlush(JobAiAnalysis.builder()
                .job(adyenJob)
                .analysisVersion(1)
                .requiredSkills(List.of("Java 21", "Spring Boot", "PostgreSQL"))
                .niceToHaveSkills(List.of("Kafka"))
                .responsibilities(List.of("Architect high-throughput settlement workflows"))
                .languageRequirements(List.of("English (Fluent)"))
                .summary("Senior Java role on Adyen clearing core.")
                .aiProvider("GEMINI")
                .aiModelUsed("gemini-2.5-flash")
                .build());
    }

    @AfterEach
    void cleanDatabase() {
        jobMatchScoreRepository.deleteAllInBatch();
        jobAiAnalysisRepository.deleteAllInBatch();
        resumeRepository.deleteAllInBatch();
        userEducationRepository.deleteAllInBatch();
        userWorkExperienceRepository.deleteAllInBatch();
        userSkillRepository.deleteAllInBatch();
        userJobPreferenceRepository.deleteAllInBatch();
        userProfileRepository.deleteAllInBatch();
        jobRepository.deleteAllInBatch();
        companyRepository.deleteAllInBatch();
        refreshTokenRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
    }

    @Test
    void evaluateSemanticJobMatch_separatesDeterministicAndAiReasoningValidatesAndPreservesAcrossRecalculation()
            throws Exception {
        mockMvc.perform(post("/api/v1/jobs/" + adyenJob.getId() + "/intelligence/ai-match"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/v1/jobs/0/intelligence/ai-match")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenCompleteUser))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/v1/jobs/999999/intelligence/ai-match")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenCompleteUser))
                .andExpect(status().isNotFound());

        // Candidate with zero profile/skills/experience/resume is rejected with 400 rather than guessing
        mockMvc.perform(post("/api/v1/jobs/" + adyenJob.getId() + "/intelligence/ai-match")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenEmptyUser))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        RawAiSemanticMatchPayload validPayload = RawAiSemanticMatchPayload.builder()
                .aiScore(new BigDecimal("96.00"))
                .strongMatchingAreas(List.of(
                        "Direct alignment on SEPA Instant payment settlement architecture (ClearPay Systems & Master Resume project)",
                        "6.0 yrs core Java 21, Spring Boot & PostgreSQL depth exceeds 5+ yr requirement"
                ))
                .missingSkills(List.of("Apache Kafka event streaming"))
                .experienceGaps(List.of("No documented production event-driven Kafka broker topology in resume/profile"))
                .potentialConcerns(List.of("Requires IND Highly Skilled Migrant sponsorship relocation to Amsterdam"))
                .improvementSuggestions(List.of(
                        "Highlight ClearPay 5,000 TPS ledger uptime and PostgreSQL sharding metrics",
                        "Demonstrate Kafka message-guarantee patterns honestly before technical interviews"
                ))
                .rationale("Strong semantic fit grounded in verified Java FinTech settlement projects; only Kafka depth requires attention.")
                .build();

        when(aiGatewayService.executeStructuredJsonPrompt(
                any(AiJsonPromptRequest.class),
                eq(RawAiSemanticMatchPayload.class)))
                .thenReturn(new AiStructuredResponse<>(
                        "GEMINI",
                        "gemini-2.5-flash",
                        validPayload,
                        objectMapper.writeValueAsString(validPayload),
                        640,
                        280
                ));

        // 1. Trigger AI Semantic Match: initializes deterministic score (90.00) separately and blends finalScore -> (90+96)/2 = 93.00
        mockMvc.perform(post("/api/v1/jobs/" + adyenJob.getId() + "/intelligence/ai-match")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenCompleteUser))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deterministicScore").value(90.0))
                .andExpect(jsonPath("$.aiScore").value(96.0))
                .andExpect(jsonPath("$.finalScore").value(93.0))
                .andExpect(jsonPath("$.matchPercentage").value(93))
                .andExpect(jsonPath("$.missingSkills.length()").value(1))
                .andExpect(jsonPath("$.missingSkills[0]").value("Kafka"))
                .andExpect(jsonPath("$.aiStrongMatches.length()").value(2))
                .andExpect(jsonPath("$.aiMissingSkills.length()").value(1))
                .andExpect(jsonPath("$.aiMissingSkills[0]").value("Apache Kafka event streaming"))
                .andExpect(jsonPath("$.aiExperienceGaps.length()").value(1))
                .andExpect(jsonPath("$.aiPotentialConcerns.length()").value(1))
                .andExpect(jsonPath("$.aiImprovementSuggestions.length()").value(2))
                .andExpect(jsonPath("$.aiRationale").value(validPayload.getRationale()))
                .andExpect(jsonPath("$.aiProvider").value("GEMINI"))
                .andExpect(jsonPath("$.aiModelUsed").value("gemini-2.5-flash"))
                .andExpect(jsonPath("$.aiEvaluatedAt").isNotEmpty());

        ArgumentCaptor<AiJsonPromptRequest> promptCaptor = ArgumentCaptor.forClass(AiJsonPromptRequest.class);
        verify(aiGatewayService).executeStructuredJsonPrompt(promptCaptor.capture(), eq(RawAiSemanticMatchPayload.class));
        AiJsonPromptRequest capturedPrompt = promptCaptor.getValue();
        assertThat(capturedPrompt.systemPrompt()).contains("NEVER invent, fabricate, infer, or assume unstated skills");
        assertThat(capturedPrompt.userPrompt()).contains("Master Backend Java Resume 2026");
        assertThat(capturedPrompt.userPrompt()).contains("SEPA Instant Payment Clearing Core");
        assertThat(capturedPrompt.userPrompt()).contains("ClearPay Systems");
        assertThat(capturedPrompt.userPrompt()).contains("Architect high-throughput settlement workflows");

        // 2. GET /api/v1/jobs/{id} surfaces full separated semantic AI comparison
        mockMvc.perform(get("/api/v1/jobs/" + adyenJob.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenCompleteUser))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.matchPercentage").value(93))
                .andExpect(jsonPath("$.matchBreakdown.deterministicScore").value(90.0))
                .andExpect(jsonPath("$.matchBreakdown.aiScore").value(96.0))
                .andExpect(jsonPath("$.matchBreakdown.aiStrongMatches.length()").value(2))
                .andExpect(jsonPath("$.matchBreakdown.aiMissingSkills[0]").value("Apache Kafka event streaming"))
                .andExpect(jsonPath("$.matchBreakdown.aiExperienceGaps.length()").value(1))
                .andExpect(jsonPath("$.matchBreakdown.aiPotentialConcerns.length()").value(1))
                .andExpect(jsonPath("$.matchBreakdown.aiImprovementSuggestions.length()").value(2));

        // 3. Deterministic recalculation after adding Kafka upgrades deterministicScore to 100, blends finalScore -> (100+96)/2 = 98,
        // and preserves existing AI semantic reasoning completely intact
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
                .andExpect(jsonPath("$.deterministicScore").value(100.0))
                .andExpect(jsonPath("$.aiScore").value(96.0))
                .andExpect(jsonPath("$.finalScore").value(98.0))
                .andExpect(jsonPath("$.matchPercentage").value(98))
                .andExpect(jsonPath("$.missingSkills.length()").value(0))
                .andExpect(jsonPath("$.aiMissingSkills.length()").value(1))
                .andExpect(jsonPath("$.aiStrongMatches.length()").value(2))
                .andExpect(jsonPath("$.aiProvider").value("GEMINI"));

        // 4. forceRefresh=false returns persisted semantic match without invoking AI gateway again
        mockMvc.perform(post("/api/v1/jobs/" + adyenJob.getId() + "/intelligence/ai-match")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenCompleteUser)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"forceRefresh\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.finalScore").value(98.0))
                .andExpect(jsonPath("$.aiScore").value(96.0));
        verify(aiGatewayService, times(1)).executeStructuredJsonPrompt(any(), eq(RawAiSemanticMatchPayload.class));

        // 5. Invalid AI output (aiScore = 150) returns 502 and leaves existing persisted row untouched
        RawAiSemanticMatchPayload invalidPayload = RawAiSemanticMatchPayload.builder()
                .aiScore(new BigDecimal("150.0"))
                .strongMatchingAreas(List.of("Over-scored"))
                .rationale("Out of range score")
                .build();
        when(aiGatewayService.executeStructuredJsonPrompt(any(), eq(RawAiSemanticMatchPayload.class)))
                .thenReturn(new AiStructuredResponse<>(
                        "GEMINI", "gemini-2.5-flash", invalidPayload, "{}", 100, 40
                ));

        mockMvc.perform(post("/api/v1/jobs/" + adyenJob.getId() + "/intelligence/ai-match")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenCompleteUser))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.status").value(502));

        JobMatchScore persisted = jobMatchScoreRepository.findByUserIdAndJobId(completeUser.getId(), adyenJob.getId())
                .orElseThrow();
        assertThat(persisted.getAiScore()).isEqualByComparingTo("96.00");
        assertThat(persisted.getFinalScore()).isEqualByComparingTo("98.00");
        assertThat(persisted.getAiStrongMatches()).hasSize(2);
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

