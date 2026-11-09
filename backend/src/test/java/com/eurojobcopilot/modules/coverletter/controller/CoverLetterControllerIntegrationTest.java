package com.eurojobcopilot.modules.coverletter.controller;

import com.eurojobcopilot.core.enums.WorkArrangement;
import com.eurojobcopilot.modules.ai.client.AiJsonPromptRequest;
import com.eurojobcopilot.modules.ai.service.AiGatewayService;
import com.eurojobcopilot.modules.ai.service.AiStructuredResponse;
import com.eurojobcopilot.modules.application.entity.ApplicationStatus;
import com.eurojobcopilot.modules.application.entity.JobApplication;
import com.eurojobcopilot.modules.application.repository.JobApplicationRepository;
import com.eurojobcopilot.modules.auth.dto.RegisterRequest;
import com.eurojobcopilot.modules.auth.entity.User;
import com.eurojobcopilot.modules.auth.repository.RefreshTokenRepository;
import com.eurojobcopilot.modules.auth.repository.UserRepository;
import com.eurojobcopilot.modules.coverletter.dto.GenerateCoverLetterRequest;
import com.eurojobcopilot.modules.coverletter.dto.UpdateCoverLetterRequest;
import com.eurojobcopilot.modules.coverletter.entity.CoverLetter;
import com.eurojobcopilot.modules.coverletter.entity.CoverLetterTone;
import com.eurojobcopilot.modules.coverletter.generator.RawAiCoverLetterPayload;
import com.eurojobcopilot.modules.coverletter.repository.CoverLetterRepository;
import com.eurojobcopilot.modules.job.entity.EmploymentType;
import com.eurojobcopilot.modules.job.entity.ExperienceLevel;
import com.eurojobcopilot.modules.job.entity.Job;
import com.eurojobcopilot.modules.job.entity.JobAiAnalysis;
import com.eurojobcopilot.modules.job.entity.JobSponsorStatus;
import com.eurojobcopilot.modules.job.repository.JobAiAnalysisRepository;
import com.eurojobcopilot.modules.job.repository.JobRepository;
import com.eurojobcopilot.modules.matching.repository.JobMatchScoreRepository;
import com.eurojobcopilot.modules.resume.dto.TailoredResumeDataDto;
import com.eurojobcopilot.modules.resume.dto.TailoredResumeHeaderDto;
import com.eurojobcopilot.modules.resume.entity.Resume;
import com.eurojobcopilot.modules.resume.entity.ResumeParseStatus;
import com.eurojobcopilot.modules.resume.entity.ResumeTemplate;
import com.eurojobcopilot.modules.resume.entity.ResumeType;
import com.eurojobcopilot.modules.resume.entity.StorageProvider;
import com.eurojobcopilot.modules.resume.entity.TailoredResume;
import com.eurojobcopilot.modules.resume.parser.ExtractedResumeText;
import com.eurojobcopilot.modules.resume.parser.ResumeTextExtractor;
import com.eurojobcopilot.modules.resume.repository.ResumeRepository;
import com.eurojobcopilot.modules.resume.repository.TailoredResumeRepository;
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
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CoverLetterControllerIntegrationTest {

    private static final String MIME_PDF = "application/pdf";
    private static final String MIME_DOCX = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
    private static final String MIME_TEXT = "text/plain";
    private static final TypeReference<Map<String, Object>> MAP_TYPE_REF = new TypeReference<>() {
    };

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private ResumeTextExtractor resumeTextExtractor;
    @Autowired
    private CoverLetterRepository coverLetterRepository;
    @Autowired
    private JobApplicationRepository jobApplicationRepository;
    @Autowired
    private TailoredResumeRepository tailoredResumeRepository;
    @Autowired
    private ResumeRepository resumeRepository;
    @Autowired
    private JobMatchScoreRepository jobMatchScoreRepository;
    @Autowired
    private JobAiAnalysisRepository jobAiAnalysisRepository;
    @Autowired
    private JobRepository jobRepository;
    @Autowired
    private CompanyRepository companyRepository;
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
    private RefreshTokenRepository refreshTokenRepository;
    @Autowired
    private UserRepository userRepository;

    @MockitoBean
    private AiGatewayService aiGatewayService;

    private String tokenOwner;
    private String tokenEmptyUser;
    private User ownerUser;
    private Job adyenJob;
    private Job sapJob;
    private TailoredResume linkedTailoredResume;

    @BeforeEach
    void setUp() throws Exception {
        cleanDatabase();
        tokenOwner = registerUser("cover.owner@eurojob.eu", "Elena", "Weber");
        tokenEmptyUser = registerUser("cover.empty@eurojob.eu", "Markus", "Klein");

        ownerUser = userRepository.findByEmailIgnoreCase("cover.owner@eurojob.eu").orElseThrow();

        userProfileRepository.saveAndFlush(UserProfile.builder()
                .user(ownerUser)
                .currentDesignation("Senior Java Backend Engineer")
                .yearsOfExperience(new BigDecimal("6.5"))
                .currentCountry("Germany")
                .currentCity("Berlin")
                .noticePeriodDays(30)
                .workAuthorizationStatus(WorkAuthorizationStatus.NEEDS_SPONSORSHIP)
                .relocationPreference(RelocationPreference.WILLING_TO_RELOCATE)
                .linkedinUrl("https://linkedin.com/in/elena-weber")
                .bio("Backend architect focused on distributed Java payment clearing services.")
                .build());

        userSkillRepository.saveAllAndFlush(List.of(
                UserSkill.builder()
                        .user(ownerUser)
                        .skillName("Java 21")
                        .normalizedSkill("java 21")
                        .yearsExperience(new BigDecimal("6.5"))
                        .proficiencyLevel(ProficiencyLevel.EXPERT)
                        .primary(true)
                        .build(),
                UserSkill.builder()
                        .user(ownerUser)
                        .skillName("Spring Boot")
                        .normalizedSkill("spring boot")
                        .yearsExperience(new BigDecimal("5.5"))
                        .proficiencyLevel(ProficiencyLevel.EXPERT)
                        .primary(true)
                        .build(),
                UserSkill.builder()
                        .user(ownerUser)
                        .skillName("PostgreSQL")
                        .normalizedSkill("postgresql")
                        .yearsExperience(new BigDecimal("5.0"))
                        .proficiencyLevel(ProficiencyLevel.ADVANCED)
                        .build()
        ));

        userWorkExperienceRepository.saveAndFlush(UserWorkExperience.builder()
                .user(ownerUser)
                .companyName("ClearPay Systems")
                .designation("Senior Java Engineer")
                .location("Berlin, Germany")
                .startDate(LocalDate.of(2021, 3, 1))
                .current(true)
                .description("Built SEPA Instant settlement microservices processing 5,000 TPS.")
                .skillsUsed(List.of("Java 21", "Spring Boot", "PostgreSQL"))
                .build());

        userEducationRepository.saveAndFlush(UserEducation.builder()
                .user(ownerUser)
                .institution("Technical University of Munich")
                .degree("M.Sc.")
                .fieldOfStudy("Computer Science")
                .startDate(LocalDate.of(2016, 10, 1))
                .endDate(LocalDate.of(2018, 9, 30))
                .gradeOrGpa("1.3")
                .country("Germany")
                .build());

        userJobPreferenceRepository.saveAndFlush(UserJobPreference.builder()
                .user(ownerUser)
                .preferredJobTitles(List.of("Staff Java Engineer"))
                .preferredCountries(List.of("Netherlands", "Germany"))
                .preferredCities(List.of("Amsterdam", "Berlin"))
                .requiresSponsorship(true)
                .requiresRelocation(true)
                .workArrangement(WorkArrangement.HYBRID)
                .build());

        Resume masterResume = resumeRepository.saveAndFlush(Resume.builder()
                .user(ownerUser)
                .resumeType(ResumeType.MASTER)
                .title("Master Backend CV")
                .fileName("elena-master.pdf")
                .filePath("users/" + ownerUser.getUuid() + "/elena-master.pdf")
                .fileSizeBytes(20480L)
                .mimeType("application/pdf")
                .storageProvider(StorageProvider.LOCAL)
                .versionNumber(1)
                .masterApproved(true)
                .parseStatus(ResumeParseStatus.APPROVED)
                .build());

        Company adyen = companyRepository.saveAndFlush(Company.builder()
                .name("Adyen N.V.")
                .normalizedName("adyen")
                .headquartersCountry("Netherlands")
                .verifiedSponsor(true)
                .sponsorConfidenceLevel(SponsorConfidenceLevel.VERIFIED)
                .industry("FinTech")
                .companySize("1000-5000")
                .description("Unified commerce and European SEPA instant clearing infrastructure.")
                .techStack(List.of("Java 21", "Spring Boot", "PostgreSQL"))
                .build());

        adyenJob = jobRepository.saveAndFlush(Job.builder()
                .canonicalHash(TestEntityFactory.sha256Hex("cover-adyen-1"))
                .company(adyen)
                .companyName("Adyen N.V.")
                .title("Staff Java Clearing Architect")
                .normalizedTitle("staff java clearing architect")
                .location("Amsterdam, Netherlands")
                .city("Amsterdam")
                .country("Netherlands")
                .workArrangement(WorkArrangement.HYBRID)
                .employmentType(EmploymentType.FULL_TIME)
                .experienceLevel(ExperienceLevel.SENIOR)
                .minExperienceYears(new BigDecimal("5.0"))
                .skills(List.of("Java 21", "Spring Boot", "PostgreSQL", "Rust"))
                .description("Architect SEPA instant payment APIs in Java 21, Spring Boot, PostgreSQL, and Rust.")
                .sourceProvider("ARBEITNOW")
                .originalUrl("https://careers.adyen.com/cover-staff-1")
                .sponsorStatus(JobSponsorStatus.VERIFIED_SPONSOR)
                .postedAt(Instant.now().minus(2, ChronoUnit.HOURS))
                .active(true)
                .build());

        jobAiAnalysisRepository.saveAndFlush(JobAiAnalysis.builder()
                .job(adyenJob)
                .analysisVersion(1)
                .requiredSkills(List.of("Java 21", "Spring Boot", "PostgreSQL"))
                .niceToHaveSkills(List.of("Rust"))
                .responsibilities(List.of("Scale SEPA clearing microservices"))
                .languageRequirements(List.of("English"))
                .sponsorshipOffered(true)
                .visaSponsorshipAnalysis("Registered Netherlands IND sponsor providing Highly Skilled Migrant visas.")
                .relocationAssistanceOffered(true)
                .relocationAnalysis("Relocation package provided for EU/international engineers.")
                .summary("Staff payments role in Amsterdam.")
                .aiProvider("GEMINI")
                .aiModelUsed("gemini-2.5-flash")
                .build());

        sapJob = jobRepository.saveAndFlush(Job.builder()
                .canonicalHash(TestEntityFactory.sha256Hex("cover-sap-2"))
                .companyName("SAP SE")
                .title("Senior Cloud Engineer")
                .normalizedTitle("senior cloud engineer")
                .location("Walldorf, Germany")
                .city("Walldorf")
                .country("Germany")
                .workArrangement(WorkArrangement.HYBRID)
                .employmentType(EmploymentType.FULL_TIME)
                .experienceLevel(ExperienceLevel.SENIOR)
                .skills(List.of("Java 21", "Spring Boot"))
                .description("Build enterprise cloud microservices in Germany.")
                .sourceProvider("ARBEITNOW")
                .originalUrl("https://jobs.sap.com/cover-cloud-2")
                .postedAt(Instant.now().minus(1, ChronoUnit.HOURS))
                .active(true)
                .build());

        TailoredResumeDataDto trData = TailoredResumeDataDto.builder()
                .header(TailoredResumeHeaderDto.builder()
                        .fullName("Elena Weber")
                        .headline("Staff Java Backend Engineer")
                        .location("Berlin, Germany")
                        .build())
                .tailoredSummary("SEPA Instant specialist scaling Java 21 payment gateways.")
                .highlightedSkills(List.of("Java 21", "Spring Boot", "PostgreSQL"))
                .build();

        linkedTailoredResume = tailoredResumeRepository.saveAndFlush(TailoredResume.builder()
                .masterResume(masterResume)
                .job(adyenJob)
                .user(ownerUser)
                .title("Staff Java · Adyen (v1)")
                .tailoringFocus("SEPA Instant ledger performance")
                .templateName(ResumeTemplate.MODERN)
                .resumeDataJson(objectMapper.convertValue(trData, MAP_TYPE_REF))
                .atsKeywordCoverage(new BigDecimal("85.00"))
                .versionNumber(1)
                .build());
    }

    @AfterEach
    void cleanDatabase() {
        reset(aiGatewayService);
        jobApplicationRepository.deleteAllInBatch();
        coverLetterRepository.deleteAllInBatch();
        tailoredResumeRepository.deleteAllInBatch();
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
    void fullCoverLetterLifecycle_generateCacheToneNotesEditExportAndDelete() throws Exception {
        // 1. Auth & Validation boundaries
        mockMvc.perform(post("/api/v1/tailored-documents/cover-letter/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"jobId\":" + adyenJob.getId() + "}"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/v1/tailored-documents/cover-letters"))
                .andExpect(status().isUnauthorized());

        // Candidate with zero evidence gets 400 BusinessValidationException
        mockMvc.perform(post("/api/v1/tailored-documents/cover-letter/generate")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenEmptyUser)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"jobId\":" + adyenJob.getId() + "}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        // Non-existent job -> 404
        mockMvc.perform(post("/api/v1/tailored-documents/cover-letter/generate")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenOwner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"jobId\":999999}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));

        // Cross-user or unknown tailoredResumeUuid -> 404
        mockMvc.perform(post("/api/v1/tailored-documents/cover-letter/generate")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenOwner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(GenerateCoverLetterRequest.builder()
                                .jobId(adyenJob.getId())
                                .tailoredResumeUuid(UUID.randomUUID().toString())
                                .build())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));

        // 2. Mock AI response including a fabricated skill "Rust" so validator filters & warns
        RawAiCoverLetterPayload aiV1 = buildValidAiPayload("Adyen N.V.", true);
        when(aiGatewayService.executeStructuredJsonPrompt(
                any(AiJsonPromptRequest.class),
                eq(RawAiCoverLetterPayload.class)))
                .thenReturn(new AiStructuredResponse<>(
                        "GEMINI",
                        "gemini-2.5-flash",
                        aiV1,
                        objectMapper.writeValueAsString(aiV1),
                        620,
                        310
                ));

        // Generate v1 omitting tailoredResumeUuid -> auto-links latest tailored resume for Adyen job
        GenerateCoverLetterRequest genV1 = GenerateCoverLetterRequest.builder()
                .jobId(adyenJob.getId())
                .tone(CoverLetterTone.CONFIDENT)
                .personalNotes("Highlight SEPA Instant scalability and readiness to relocate from Berlin to Amsterdam.")
                .build();

        MvcResult v1Result = mockMvc.perform(post("/api/v1/tailored-documents/cover-letter/generate")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenOwner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(genV1)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.uuid").isNotEmpty())
                .andExpect(jsonPath("$.jobId").value(adyenJob.getId()))
                .andExpect(jsonPath("$.tailoredResumeUuid").value(linkedTailoredResume.getUuid()))
                .andExpect(jsonPath("$.tone").value("CONFIDENT"))
                .andExpect(jsonPath("$.versionNumber").value(1))
                .andExpect(jsonPath("$.editedByUser").value(false))
                .andExpect(jsonPath("$.aiProvider").value("GEMINI"))
                .andExpect(jsonPath("$.wordCount").isNumber())
                .andExpect(jsonPath("$.metadata.highlightedSkills.length()").value(3))
                .andExpect(jsonPath("$.metadata.groundingWarnings[0]").value(containsString("Rust")))
                .andExpect(jsonPath("$.metadata.workAuthorizationSummary").value(containsString("Requires Visa Sponsorship")))
                .andExpect(jsonPath("$.metadata.relocationReadinessNote").value(anyOf(
                        containsString("Amsterdam"),
                        containsString("Netherlands"))))
                .andReturn();

        String v1Uuid = objectMapper.readTree(v1Result.getResponse().getContentAsString()).get("uuid").asText();

        ArgumentCaptor<AiJsonPromptRequest> promptCaptor = ArgumentCaptor.forClass(AiJsonPromptRequest.class);
        verify(aiGatewayService, times(1)).executeStructuredJsonPrompt(promptCaptor.capture(), eq(RawAiCoverLetterPayload.class));
        assertThat(promptCaptor.getValue().userPrompt())
                .contains("CONFIDENT", "SEPA Instant scalability", "NEEDS_SPONSORSHIP", "Adyen N.V.", "ClearPay Systems");

        // 3. forceRefresh=false with same parameters returns cached v1 without calling AI gateway
        mockMvc.perform(post("/api/v1/tailored-documents/cover-letter/generate")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenOwner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(GenerateCoverLetterRequest.builder()
                                .jobId(adyenJob.getId())
                                .tone(CoverLetterTone.CONFIDENT)
                                .personalNotes("Highlight SEPA Instant scalability and readiness to relocate from Berlin to Amsterdam.")
                                .forceRefresh(false)
                                .build())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.uuid").value(v1Uuid))
                .andExpect(jsonPath("$.versionNumber").value(1));
        verify(aiGatewayService, times(1)).executeStructuredJsonPrompt(any(), eq(RawAiCoverLetterPayload.class));

        // 4. Changing tone -> creates v2 even with forceRefresh=false
        mockMvc.perform(post("/api/v1/tailored-documents/cover-letter/generate")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenOwner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(GenerateCoverLetterRequest.builder()
                                .jobId(adyenJob.getId())
                                .tailoredResumeUuid(linkedTailoredResume.getUuid())
                                .tone(CoverLetterTone.CONCISE)
                                .forceRefresh(false)
                                .build())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.versionNumber").value(2))
                .andExpect(jsonPath("$.tone").value("CONCISE"));

        // 5. Generate for SAP job without any tailored resume -> works seamlessly -> v1
        mockMvc.perform(post("/api/v1/tailored-documents/cover-letter/generate")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenOwner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(GenerateCoverLetterRequest.builder()
                                .jobId(sapJob.getId())
                                .tone(CoverLetterTone.ACADEMIC)
                                .build())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.versionNumber").value(1))
                .andExpect(jsonPath("$.tone").value("ACADEMIC"))
                .andExpect(jsonPath("$.tailoredResumeUuid").isEmpty());

        // 6. List unfiltered (3) vs filtered by adyenJob (2 ordered v2, v1) vs invalid ?jobId=0
        mockMvc.perform(get("/api/v1/tailored-documents/cover-letters")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenOwner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3));

        mockMvc.perform(get("/api/v1/tailored-documents/cover-letters")
                        .param("jobId", String.valueOf(adyenJob.getId()))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenOwner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].versionNumber").value(2))
                .andExpect(jsonPath("$[1].versionNumber").value(1));

        mockMvc.perform(get("/api/v1/tailored-documents/cover-letters")
                        .param("jobId", "0")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenOwner))
                .andExpect(status().isBadRequest());

        // 7. GET detail and PUT manual edit marks editedByUser=true & recomputes wordCount
        mockMvc.perform(get("/api/v1/tailored-documents/cover-letter/" + v1Uuid)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenOwner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.uuid").value(v1Uuid));

        UpdateCoverLetterRequest updateReq = UpdateCoverLetterRequest.builder()
                .title("Custom Adyen Executive Letter")
                .tone(CoverLetterTone.PROFESSIONAL)
                .personalNotes("Refined closing paragraph")
                .content("Dear Hiring Team at Adyen N.V.,\n\nI built SEPA Instant services in Java 21 and Spring Boot at ClearPay Systems and welcome relocating to Amsterdam with sponsorship.\n\nSincerely,\nElena Weber")
                .build();

        mockMvc.perform(put("/api/v1/tailored-documents/cover-letter/" + v1Uuid)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenOwner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Custom Adyen Executive Letter"))
                .andExpect(jsonPath("$.tone").value("PROFESSIONAL"))
                .andExpect(jsonPath("$.editedByUser").value(true))
                .andExpect(jsonPath("$.wordCount").value(30));

        // 8. Export PDF, DOCX, TXT + invalid format/uuid checks
        MvcResult pdfExport = mockMvc.perform(get("/api/v1/tailored-documents/cover-letter/" + v1Uuid + "/export")
                        .param("format", "pdf")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenOwner))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, containsString(MIME_PDF)))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("attachment")))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("-cover-letter.pdf")))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("no-store")))
                .andReturn();
        byte[] pdfBytes = pdfExport.getResponse().getContentAsByteArray();
        assertThat(new String(pdfBytes, 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
        ExtractedResumeText extractedPdf = resumeTextExtractor.extract(
                new ByteArrayResource(pdfBytes), MIME_PDF, "cl.pdf");
        assertThat(extractedPdf.text()).contains("Elena Weber", "Adyen N.V.", "ClearPay Systems");

        MvcResult docxExport = mockMvc.perform(get("/api/v1/tailored-documents/cover-letter/" + v1Uuid + "/export")
                        .param("format", "DOCX")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenOwner))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, containsString(MIME_DOCX)))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("-cover-letter.docx")))
                .andReturn();
        byte[] docxBytes = docxExport.getResponse().getContentAsByteArray();
        assertThat(docxBytes[0]).isEqualTo((byte) 0x50);
        assertThat(docxBytes[1]).isEqualTo((byte) 0x4B);
        ExtractedResumeText extractedDocx = resumeTextExtractor.extract(
                new ByteArrayResource(docxBytes), MIME_DOCX, "cl.docx");
        assertThat(extractedDocx.text()).contains("Elena Weber", "Adyen N.V.", "ClearPay Systems");

        MvcResult txtExport = mockMvc.perform(get("/api/v1/tailored-documents/cover-letter/" + v1Uuid + "/export")
                        .param("format", "txt")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenOwner))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, containsString(MIME_TEXT)))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("-cover-letter.txt")))
                .andReturn();
        assertThat(txtExport.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .contains("Elena Weber", "Re: Application for Staff Java Clearing Architect", "ClearPay Systems");

        mockMvc.perform(get("/api/v1/tailored-documents/cover-letter/" + v1Uuid + "/export")
                        .param("format", "rtf")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenOwner))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/v1/tailored-documents/cover-letter/bad-uuid/export")
                        .param("format", "pdf")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenOwner))
                .andExpect(status().isBadRequest());

        // 9. Cross-user isolation -> 404 on read, edit, export, delete, and linking owner's tailoredResume
        mockMvc.perform(get("/api/v1/tailored-documents/cover-letter/" + v1Uuid)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenEmptyUser))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/v1/tailored-documents/cover-letter/" + v1Uuid + "/export")
                        .param("format", "pdf")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenEmptyUser))
                .andExpect(status().isNotFound());

        mockMvc.perform(put("/api/v1/tailored-documents/cover-letter/" + v1Uuid)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenEmptyUser)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isNotFound());

        mockMvc.perform(delete("/api/v1/tailored-documents/cover-letter/" + v1Uuid)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenEmptyUser))
                .andExpect(status().isNotFound());

        // 10. Deleting a cover letter referenced by a JobApplication safely nulls FK before delete
        CoverLetter v1Entity = coverLetterRepository.findByUuidAndUserId(v1Uuid, ownerUser.getId()).orElseThrow();
        jobApplicationRepository.saveAndFlush(JobApplication.builder()
                .user(ownerUser)
                .job(adyenJob)
                .status(ApplicationStatus.APPLIED)
                .coverLetter(v1Entity)
                .appliedDate(Instant.now())
                .build());

        mockMvc.perform(delete("/api/v1/tailored-documents/cover-letter/" + v1Uuid)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenOwner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        mockMvc.perform(get("/api/v1/tailored-documents/cover-letter/" + v1Uuid)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenOwner))
                .andExpect(status().isNotFound());

        JobApplication refreshedApp = jobApplicationRepository.findByUserIdAndJobId(ownerUser.getId(), adyenJob.getId())
                .orElseThrow();
        assertThat(refreshedApp.getCoverLetter()).isNull();
    }

    private RawAiCoverLetterPayload buildValidAiPayload(String company, boolean includeHallucinatedRust) {
        RawAiCoverLetterPayload payload = new RawAiCoverLetterPayload();
        payload.setSalutation("Dear Hiring Team at " + company + ",");
        payload.setOpeningParagraph("With 6.5 years designing high-throughput European JVM payment gateways, I am excited to apply for Staff Java Clearing Architect at " + company + ".");
        payload.setBodyParagraphs(List.of(
                "At ClearPay Systems, I led Java 21 and Spring Boot SEPA Instant microservices handling 5,000 TPS on sharded PostgreSQL clusters.",
                "My M.Sc. in Computer Science from Technical University of Munich and hands-on focus on deterministic reliability align closely with Adyen's unified commerce platform."
        ));
        payload.setRelocationAndAuthorizationParagraph(
                "Based in Berlin, I am enthusiastic about relocating to Amsterdam (30-day notice) and appreciate Adyen's verified IND Highly Skilled Migrant sponsorship support."
        );
        payload.setClosingParagraph("Thank you for considering my application; I would welcome an engineering architecture discussion.");
        payload.setSignOff("Sincerely,\nElena Weber");
        payload.setCompanyConnectionSummary("Direct SEPA Instant payments alignment with Adyen's Amsterdam platform.");
        payload.setKeyAlignmentPoints(List.of(
                "SEPA Instant clearing at 5,000 TPS in Java 21",
                "Verified M.Sc. Computer Science from TU Munich"
        ));
        payload.setHighlightedSkills(includeHallucinatedRust
                ? List.of("Java 21", "Spring Boot", "PostgreSQL", "Rust")
                : List.of("Java 21", "Spring Boot", "PostgreSQL"));
        payload.setWarnings(List.of());
        return payload;
    }

    private String registerUser(String email, String firstName, String lastName) throws Exception {
        RegisterRequest req = RegisterRequest.builder()
                .email(email)
                .password("SecureP@ssw0rd2026!")
                .firstName(firstName)
                .lastName(lastName)
                .build();

        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        return json.get("accessToken").asText();
    }
}
