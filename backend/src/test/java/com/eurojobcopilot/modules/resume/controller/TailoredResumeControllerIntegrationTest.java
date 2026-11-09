package com.eurojobcopilot.modules.resume.controller;

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
import com.eurojobcopilot.modules.matching.repository.JobMatchScoreRepository;
import com.eurojobcopilot.modules.resume.dto.GenerateTailoredResumeRequest;
import com.eurojobcopilot.modules.resume.dto.ParsedResumeEducationDto;
import com.eurojobcopilot.modules.resume.dto.ParsedResumeExperienceDto;
import com.eurojobcopilot.modules.resume.dto.ParsedResumeJsonDto;
import com.eurojobcopilot.modules.resume.dto.ParsedResumeProfileDto;
import com.eurojobcopilot.modules.resume.dto.ParsedResumeProjectDto;
import com.eurojobcopilot.modules.resume.dto.ParsedResumeSkillDto;
import com.eurojobcopilot.modules.resume.dto.UpdateTailoredResumeRequest;
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
import com.eurojobcopilot.modules.resume.tailored.RawAiTailoredResumePayload;
import com.eurojobcopilot.modules.resume.tailored.RawAiTailoredResumePayload.RawOptimizedExperience;
import com.eurojobcopilot.modules.resume.tailored.RawAiTailoredResumePayload.RawTailoredProject;
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
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
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
class TailoredResumeControllerIntegrationTest {

    private static final String MIME_PDF = "application/pdf";
    private static final String MIME_DOCX = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
    private static final TypeReference<Map<String, Object>> MAP_TYPE_REF = new TypeReference<>() {
    };

    @TempDir
    static Path tempStorageDir;

    @DynamicPropertySource
    static void registerStorageProperties(DynamicPropertyRegistry registry) {
        registry.add("app.storage.resumes-path", () -> tempStorageDir.toString());
        registry.add("app.storage.max-file-size-bytes", () -> String.valueOf(10L * 1024L * 1024L));
    }

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private ResumeTextExtractor resumeTextExtractor;
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
    private String tokenNoResumeUser;
    private User ownerUser;
    private User noResumeUser;
    private Resume approvedMaster;
    private Resume importedTypeResume;
    private Job adyenJob;
    private Job sapJob;

    @BeforeEach
    void setUp() throws Exception {
        cleanDatabase();
        tokenOwner = registerUser("tailor.owner@eurojob.eu", "Elena", "Weber");
        tokenNoResumeUser = registerUser("tailor.empty@eurojob.eu", "Markus", "Klein");

        ownerUser = userRepository.findByEmailIgnoreCase("tailor.owner@eurojob.eu").orElseThrow();
        noResumeUser = userRepository.findByEmailIgnoreCase("tailor.empty@eurojob.eu").orElseThrow();

        userProfileRepository.saveAndFlush(UserProfile.builder()
                .user(ownerUser)
                .currentDesignation("Senior Java Backend Engineer")
                .yearsOfExperience(new BigDecimal("6.5"))
                .currentCountry("Germany")
                .currentCity("Berlin")
                .expectedSalaryMin(new BigDecimal("90000.00"))
                .expectedSalaryCurrency("EUR")
                .workAuthorizationStatus(WorkAuthorizationStatus.EU_CITIZEN)
                .relocationPreference(RelocationPreference.ALREADY_IN_EUROPE)
                .linkedinUrl("https://linkedin.com/in/elena-weber")
                .githubUrl("https://github.com/elena-weber")
                .bio("Backend architect focused on distributed Java systems and high-throughput clearing.")
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
                .description("- Built resilient SEPA Instant ledger processing 5,000 TPS in Java 21 and Spring Boot\n- Designed PostgreSQL horizontal sharding")
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
                .preferredJobTitles(List.of("Senior Java Engineer", "Staff Backend Engineer"))
                .preferredCountries(List.of("Netherlands", "Germany"))
                .preferredCities(List.of("Amsterdam", "Berlin"))
                .minSalary(new BigDecimal("90000.00"))
                .salaryCurrency("EUR")
                .workArrangement(WorkArrangement.HYBRID)
                .build());

        ParsedResumeJsonDto parsedMaster = ParsedResumeJsonDto.builder()
                .profile(ParsedResumeProfileDto.builder()
                        .fullName("Elena Weber")
                        .currentDesignation("Senior Java Backend Engineer")
                        .yearsOfExperience(new BigDecimal("6.5"))
                        .currentCountry("Germany")
                        .currentCity("Berlin")
                        .build())
                .summary("Distributed backend specialist with SEPA payment platform track record.")
                .skills(List.of(
                        ParsedResumeSkillDto.builder().skillName("Java 21").primary(true).build(),
                        ParsedResumeSkillDto.builder().skillName("Spring Boot").primary(true).build(),
                        ParsedResumeSkillDto.builder().skillName("PostgreSQL").build(),
                        ParsedResumeSkillDto.builder().skillName("Docker").build()
                ))
                .experiences(List.of(
                        ParsedResumeExperienceDto.builder()
                                .companyName("ClearPay Systems")
                                .designation("Senior Java Engineer")
                                .startDate(LocalDate.of(2021, 3, 1))
                                .current(true)
                                .skillsUsed(List.of("Java 21", "Spring Boot", "PostgreSQL"))
                                .build()
                ))
                .educations(List.of(
                        ParsedResumeEducationDto.builder()
                                .institution("Technical University of Munich")
                                .degree("M.Sc.")
                                .fieldOfStudy("Computer Science")
                                .country("Germany")
                                .build()
                ))
                .projects(List.of(
                        ParsedResumeProjectDto.builder()
                                .name("SEPA Instant Core")
                                .description("Sub-50ms ISO 20022 clearing engine using Java 21 and PostgreSQL.")
                                .technologies(List.of("Java 21", "PostgreSQL"))
                                .url("https://github.com/elena-weber/sepa-core")
                                .build()
                ))
                .languages(List.of("English (C2)", "German (B2)"))
                .certifications(List.of("Oracle Certified Professional: Java SE 21"))
                .build();

        approvedMaster = resumeRepository.saveAndFlush(Resume.builder()
                .user(ownerUser)
                .resumeType(ResumeType.MASTER)
                .title("Approved Master Backend CV")
                .fileName("elena-master.pdf")
                .filePath("users/" + ownerUser.getUuid() + "/elena-master.pdf")
                .fileSizeBytes(32768L)
                .mimeType("application/pdf")
                .storageProvider(StorageProvider.LOCAL)
                .versionNumber(1)
                .masterApproved(true)
                .parseStatus(ResumeParseStatus.APPROVED)
                .parsedContentJson(objectMapper.convertValue(parsedMaster, MAP_TYPE_REF))
                .build());

        importedTypeResume = resumeRepository.saveAndFlush(Resume.builder()
                .user(ownerUser)
                .resumeType(ResumeType.IMPORTED)
                .title("Non-master imported resume")
                .fileName("exported.pdf")
                .filePath("users/" + ownerUser.getUuid() + "/exported.pdf")
                .fileSizeBytes(12000L)
                .mimeType("application/pdf")
                .storageProvider(StorageProvider.LOCAL)
                .versionNumber(2)
                .masterApproved(false)
                .parseStatus(ResumeParseStatus.NOT_PARSED)
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
                .canonicalHash(TestEntityFactory.sha256Hex("tailored-adyen-1"))
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
                .salaryMin(new BigDecimal("95000.00"))
                .salaryMax(new BigDecimal("120000.00"))
                .salaryCurrency("EUR")
                .salaryPeriod(SalaryPeriod.YEARLY)
                .skills(List.of("Java 21", "Spring Boot", "PostgreSQL", "Kafka"))
                .description("Build fault-tolerant multi-currency SEPA settlement services with Java, Spring Boot, PostgreSQL, and Kafka.")
                .sourceProvider("ARBEITNOW")
                .originalUrl("https://careers.adyen.com/staff-java-1")
                .sponsorStatus(JobSponsorStatus.VERIFIED_SPONSOR)
                .postedAt(Instant.now().minus(2, ChronoUnit.HOURS))
                .active(true)
                .build());

        jobAiAnalysisRepository.saveAndFlush(JobAiAnalysis.builder()
                .job(adyenJob)
                .analysisVersion(1)
                .requiredSkills(List.of("Java 21", "Spring Boot", "PostgreSQL", "Kafka"))
                .niceToHaveSkills(List.of("Docker"))
                .responsibilities(List.of("Lead payment settlement microservices"))
                .languageRequirements(List.of("English"))
                .summary("Staff backend role on core European payments infrastructure.")
                .aiProvider("GEMINI")
                .aiModelUsed("gemini-2.5-flash")
                .build());

        sapJob = jobRepository.saveAndFlush(Job.builder()
                .canonicalHash(TestEntityFactory.sha256Hex("tailored-sap-2"))
                .companyName("SAP SE")
                .title("Senior Backend Engineer")
                .normalizedTitle("senior backend engineer")
                .location("Walldorf, Germany")
                .city("Walldorf")
                .country("Germany")
                .workArrangement(WorkArrangement.HYBRID)
                .employmentType(EmploymentType.FULL_TIME)
                .experienceLevel(ExperienceLevel.SENIOR)
                .skills(List.of("Java 21", "Spring Boot"))
                .description("Enterprise Java cloud services role in Walldorf.")
                .sourceProvider("ARBEITNOW")
                .originalUrl("https://jobs.sap.com/backend-2")
                .postedAt(Instant.now().minus(1, ChronoUnit.HOURS))
                .active(true)
                .build());
    }

    @AfterEach
    void cleanDatabase() throws IOException {
        reset(aiGatewayService);
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

        if (tempStorageDir != null && Files.exists(tempStorageDir)) {
            try (Stream<Path> walk = Files.walk(tempStorageDir)) {
                walk.filter(p -> !p.equals(tempStorageDir))
                        .sorted((a, b) -> b.getNameCount() - a.getNameCount())
                        .forEach(p -> {
                            try {
                                Files.deleteIfExists(p);
                            } catch (IOException ignored) {
                            }
                        });
            }
        }
    }

    @Test
    void fullTailoredResumeLifecycle_generateCacheEditFilterDeleteAndSecurityBoundaries() throws Exception {
        // 1. Unauthenticated access rejected with 401
        mockMvc.perform(post("/api/v1/tailored-documents/resume/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"jobId\":" + adyenJob.getId() + "}"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/v1/tailored-documents/resumes"))
                .andExpect(status().isUnauthorized());

        // 2. Error handling before generation: missing master resume -> 400, non-existent job -> 404, non-MASTER CV -> 409
        mockMvc.perform(post("/api/v1/tailored-documents/resume/generate")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenNoResumeUser)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"jobId\":" + adyenJob.getId() + "}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        mockMvc.perform(post("/api/v1/tailored-documents/resume/generate")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenOwner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"jobId\":999999}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));

        mockMvc.perform(post("/api/v1/tailored-documents/resume/generate")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenOwner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(GenerateTailoredResumeRequest.builder()
                                .jobId(adyenJob.getId())
                                .masterResumeUuid(importedTypeResume.getUuid())
                                .templateName(ResumeTemplate.MODERN)
                                .build())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));

        // 3. Mock AI returning valid grounded draft PLUS hallucinated skill ("Rust") to test validator filtering
        RawAiTailoredResumePayload aiDraftV1 = buildGroundedAiPayload(
                "Staff Java Settlement Engineer",
                List.of("Java 21", "Spring Boot", "Docker", "Rust"),
                List.of("ClearPay Systems")
        );

        when(aiGatewayService.executeStructuredJsonPrompt(
                any(AiJsonPromptRequest.class),
                eq(RawAiTailoredResumePayload.class)))
                .thenReturn(new AiStructuredResponse<>(
                        "GEMINI",
                        "gemini-2.5-flash",
                        aiDraftV1,
                        objectMapper.writeValueAsString(aiDraftV1),
                        780,
                        410
                ));

        // Generate v1 without specifying masterResumeUuid -> picks approvedMaster automatically
        GenerateTailoredResumeRequest genV1Req = GenerateTailoredResumeRequest.builder()
                .jobId(adyenJob.getId())
                .templateName(ResumeTemplate.MODERN)
                .tailoringFocus("Foreground SEPA Instant throughput and PostgreSQL reliability")
                .build();

        MvcResult genV1Result = mockMvc.perform(post("/api/v1/tailored-documents/resume/generate")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenOwner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(genV1Req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.uuid").isNotEmpty())
                .andExpect(jsonPath("$.masterResumeUuid").value(approvedMaster.getUuid()))
                .andExpect(jsonPath("$.jobId").value(adyenJob.getId()))
                .andExpect(jsonPath("$.versionNumber").value(1))
                .andExpect(jsonPath("$.templateName").value("MODERN"))
                .andExpect(jsonPath("$.editedByUser").value(false))
                .andExpect(jsonPath("$.hasPdfExport").value(false))
                .andExpect(jsonPath("$.hasDocxExport").value(false))
                .andExpect(jsonPath("$.aiProvider").value("GEMINI"))
                .andExpect(jsonPath("$.aiModelUsed").value("gemini-2.5-flash"))
                .andExpect(jsonPath("$.atsKeywordCoverage").value(80.0))
                .andExpect(jsonPath("$.highlightedSkills.length()").value(3))
                .andExpect(jsonPath("$.missingKeywords.length()").value(1))
                .andExpect(jsonPath("$.missingKeywords[0]").value("Kafka"))
                .andExpect(jsonPath("$.resumeData.matchedKeywords.length()").value(4))
                .andExpect(jsonPath("$.resumeData.groundingWarnings[0]").value(org.hamcrest.Matchers.containsString("Rust")))
                .andExpect(jsonPath("$.optimizedExperiences[0].companyName").value("ClearPay Systems"))
                .andExpect(jsonPath("$.optimizedExperiences[0].designation").value("Senior Java Engineer"))
                .andExpect(jsonPath("$.resumeData.header.fullName").value("Elena Weber"))
                .andExpect(jsonPath("$.resumeData.educations[0].institution").value("Technical University of Munich"))
                .andExpect(jsonPath("$.resumeData.languages[0]").value("English (C2)"))
                .andExpect(jsonPath("$.resumeData.groundingWarnings").isArray())
                .andReturn();

        String v1Uuid = objectMapper.readTree(genV1Result.getResponse().getContentAsString()).get("uuid").asText();

        ArgumentCaptor<AiJsonPromptRequest> promptCaptor = ArgumentCaptor.forClass(AiJsonPromptRequest.class);
        verify(aiGatewayService, times(1)).executeStructuredJsonPrompt(promptCaptor.capture(), eq(RawAiTailoredResumePayload.class));
        assertThat(promptCaptor.getValue().userPrompt())
                .contains("MODERN", "Foreground SEPA Instant throughput", "ClearPay Systems", "SEPA Instant Core", "Adyen N.V.");

        // 4. forceRefresh=false returns cached v1 without calling AI gateway again
        mockMvc.perform(post("/api/v1/tailored-documents/resume/generate")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenOwner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(GenerateTailoredResumeRequest.builder()
                                .jobId(adyenJob.getId())
                                .templateName(ResumeTemplate.MODERN)
                                .forceRefresh(false)
                                .build())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.uuid").value(v1Uuid))
                .andExpect(jsonPath("$.versionNumber").value(1));
        verify(aiGatewayService, times(1)).executeStructuredJsonPrompt(any(), eq(RawAiTailoredResumePayload.class));

        // 5. Generate v2 with EUROPASS template for the same job -> version increments to 2
        mockMvc.perform(post("/api/v1/tailored-documents/resume/generate")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenOwner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(GenerateTailoredResumeRequest.builder()
                                .jobId(adyenJob.getId())
                                .masterResumeUuid(approvedMaster.getUuid())
                                .templateName(ResumeTemplate.EUROPASS)
                                .build())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.versionNumber").value(2))
                .andExpect(jsonPath("$.templateName").value("EUROPASS"));

        // 6. Generate v1 for SAP job (separate version sequence starting at 1)
        mockMvc.perform(post("/api/v1/tailored-documents/resume/generate")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenOwner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(GenerateTailoredResumeRequest.builder()
                                .jobId(sapJob.getId())
                                .templateName(ResumeTemplate.MINIMAL_ATS)
                                .build())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.versionNumber").value(1))
                .andExpect(jsonPath("$.atsKeywordCoverage").value(100.0));

        // 7. List unfiltered (3) vs filtered by adyenJob.getId() (2, ordered v2 then v1)
        mockMvc.perform(get("/api/v1/tailored-documents/resumes")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenOwner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3));

        mockMvc.perform(get("/api/v1/tailored-documents/resumes")
                        .param("jobId", String.valueOf(adyenJob.getId()))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenOwner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].versionNumber").value(2))
                .andExpect(jsonPath("$[1].versionNumber").value(1));

        mockMvc.perform(get("/api/v1/tailored-documents/resumes")
                        .param("jobId", "0")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenOwner))
                .andExpect(status().isBadRequest());

        // 8. GET detail and PUT manual edit recalculating ATS keyword coverage -> 100% and sets editedByUser=true
        mockMvc.perform(get("/api/v1/tailored-documents/resume/" + v1Uuid)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenOwner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.uuid").value(v1Uuid));

        UpdateTailoredResumeRequest updateReq = UpdateTailoredResumeRequest.builder()
                .title("Custom Adyen Staff Java Draft")
                .templateName(ResumeTemplate.CLASSIC)
                .tailoredSummary("Distributed Java 21, Spring Boot, PostgreSQL, Docker, and Kafka clearing architect.")
                .highlightedSkills(List.of("Java 21", "Spring Boot", "PostgreSQL", "Docker", "Kafka"))
                .build();

        mockMvc.perform(put("/api/v1/tailored-documents/resume/" + v1Uuid)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenOwner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Custom Adyen Staff Java Draft"))
                .andExpect(jsonPath("$.templateName").value("CLASSIC"))
                .andExpect(jsonPath("$.editedByUser").value(true))
                .andExpect(jsonPath("$.atsKeywordCoverage").value(100.0))
                .andExpect(jsonPath("$.missingKeywords.length()").value(0));

        // 9. Cross-user isolation -> another user cannot read, update, delete, or reference owner's tailored CV / master CV
        mockMvc.perform(get("/api/v1/tailored-documents/resume/" + v1Uuid)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenNoResumeUser))
                .andExpect(status().isNotFound());

        mockMvc.perform(put("/api/v1/tailored-documents/resume/" + v1Uuid)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenNoResumeUser)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isNotFound());

        mockMvc.perform(delete("/api/v1/tailored-documents/resume/" + v1Uuid)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenNoResumeUser))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/api/v1/tailored-documents/resume/generate")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenNoResumeUser)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(GenerateTailoredResumeRequest.builder()
                                .jobId(adyenJob.getId())
                                .masterResumeUuid(approvedMaster.getUuid())
                                .build())))
                .andExpect(status().isNotFound());

        // 10. Hallucinated AI payload (all fabricated employers) fails with 502 and persists nothing new
        RawAiTailoredResumePayload hallucinatedAi = buildGroundedAiPayload(
                "CTO of Everything",
                List.of("Java 21"),
                List.of("Totally Fabricated Inc.")
        );
        when(aiGatewayService.executeStructuredJsonPrompt(any(), eq(RawAiTailoredResumePayload.class)))
                .thenReturn(new AiStructuredResponse<>("GEMINI", "gemini-2.5-flash", hallucinatedAi, "{}", 120, 90));

        mockMvc.perform(post("/api/v1/tailored-documents/resume/generate")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenOwner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(GenerateTailoredResumeRequest.builder()
                                .jobId(adyenJob.getId())
                                .templateName(ResumeTemplate.MODERN)
                                .build())))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.status").value(502));

        assertThat(tailoredResumeRepository.findAllWithDetailsByUserIdOrderByCreatedAtDesc(ownerUser.getId())).hasSize(3);

        // 11. Owner deletes v1 cleanly; list drops to 2 and GET returns 404
        mockMvc.perform(delete("/api/v1/tailored-documents/resume/" + v1Uuid)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenOwner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        mockMvc.perform(get("/api/v1/tailored-documents/resume/" + v1Uuid)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenOwner))
                .andExpect(status().isNotFound());

        List<TailoredResume> remaining = tailoredResumeRepository.findAllWithDetailsByUserIdOrderByCreatedAtDesc(ownerUser.getId());
        assertThat(remaining).hasSize(2);
    }

    @Test
    void exportTailoredResume_pdfAndDocxTemplatesCachingInvalidationAndSecurityBoundaries() throws Exception {
        RawAiTailoredResumePayload aiDraft = buildGroundedAiPayload(
                "Staff Java Settlement Engineer",
                List.of("Java 21", "Spring Boot", "PostgreSQL", "Docker"),
                List.of("ClearPay Systems")
        );
        when(aiGatewayService.executeStructuredJsonPrompt(
                any(AiJsonPromptRequest.class),
                eq(RawAiTailoredResumePayload.class)))
                .thenReturn(new AiStructuredResponse<>(
                        "GEMINI",
                        "gemini-2.5-flash",
                        aiDraft,
                        objectMapper.writeValueAsString(aiDraft),
                        700,
                        350
                ));

        MvcResult genResult = mockMvc.perform(post("/api/v1/tailored-documents/resume/generate")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenOwner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(GenerateTailoredResumeRequest.builder()
                                .jobId(adyenJob.getId())
                                .templateName(ResumeTemplate.MODERN)
                                .build())))
                .andExpect(status().isCreated())
                .andReturn();

        String tailoredUuid = objectMapper.readTree(genResult.getResponse().getContentAsString()).get("uuid").asText();

        // 1. Security & validation boundaries
        mockMvc.perform(get("/api/v1/tailored-documents/resume/" + tailoredUuid + "/export")
                        .param("format", "pdf"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/v1/tailored-documents/resume/not-a-uuid/export")
                        .param("format", "pdf")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenOwner))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        mockMvc.perform(get("/api/v1/tailored-documents/resume/" + tailoredUuid + "/export")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenOwner))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        mockMvc.perform(get("/api/v1/tailored-documents/resume/" + tailoredUuid + "/export")
                        .param("format", "txt")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenOwner))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        mockMvc.perform(get("/api/v1/tailored-documents/resume/" + tailoredUuid + "/export")
                        .param("format", "pdf")
                        .param("template", "INVALID_TEMPLATE")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenOwner))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        // Cross-user isolation & unknown UUID -> 404
        mockMvc.perform(get("/api/v1/tailored-documents/resume/" + tailoredUuid + "/export")
                        .param("format", "pdf")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenNoResumeUser))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/v1/tailored-documents/resume/" + UUID.randomUUID() + "/export")
                        .param("format", "docx")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenOwner))
                .andExpect(status().isNotFound());

        // 2. Export PDF (default saved template MODERN) -> valid PDF stream, safe headers, text extractable, cached on disk
        MvcResult pdfResult = mockMvc.perform(get("/api/v1/tailored-documents/resume/" + tailoredUuid + "/export")
                        .param("format", "pdf")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenOwner))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, containsString(MIME_PDF)))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("attachment")))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("-modern-cv.pdf")))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("no-store")))
                .andReturn();

        byte[] pdfBytes = pdfResult.getResponse().getContentAsByteArray();
        assertThat(pdfBytes.length).isGreaterThan(500);
        assertThat(new String(pdfBytes, 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
        ExtractedResumeText pdfExtracted = resumeTextExtractor.extract(
                new ByteArrayResource(pdfBytes), MIME_PDF, "exported.pdf");
        assertThat(pdfExtracted.text())
                .contains("Elena Weber", "ClearPay Systems", "SEPA Instant", "Technical University of Munich");

        // 3. Export DOCX (case-insensitive param "DOCX") -> valid OOXML zip, extractable text, cached on disk
        MvcResult docxResult = mockMvc.perform(get("/api/v1/tailored-documents/resume/" + tailoredUuid + "/export")
                        .param("format", "DOCX")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenOwner))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, containsString(MIME_DOCX)))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("-modern-cv.docx")))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("no-store")))
                .andReturn();

        byte[] docxBytes = docxResult.getResponse().getContentAsByteArray();
        assertThat(docxBytes.length).isGreaterThan(500);
        assertThat(docxBytes[0]).isEqualTo((byte) 0x50);
        assertThat(docxBytes[1]).isEqualTo((byte) 0x4B);
        ExtractedResumeText docxExtracted = resumeTextExtractor.extract(
                new ByteArrayResource(docxBytes), MIME_DOCX, "exported.docx");
        assertThat(docxExtracted.text())
                .contains("Elena Weber", "ClearPay Systems", "SEPA Instant", "Technical University of Munich");

        TailoredResume cachedEntity = tailoredResumeRepository.findByUuidAndUserId(tailoredUuid, ownerUser.getId()).orElseThrow();
        String firstPdfKey = cachedEntity.getPdfFilePath();
        String firstDocxKey = cachedEntity.getDocxFilePath();
        assertThat(firstPdfKey).isNotBlank();
        assertThat(firstDocxKey).isNotBlank();
        assertThat(Files.exists(tempStorageDir.resolve(firstPdfKey))).isTrue();
        assertThat(Files.exists(tempStorageDir.resolve(firstDocxKey))).isTrue();

        mockMvc.perform(get("/api/v1/tailored-documents/resume/" + tailoredUuid)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenOwner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasPdfExport").value(true))
                .andExpect(jsonPath("$.hasDocxExport").value(true));

        // 4. Subsequent GET without regenerate reuses exact cached file; regenerate=true rotates storage key & deletes old file
        MvcResult cachedPdfHit = mockMvc.perform(get("/api/v1/tailored-documents/resume/" + tailoredUuid + "/export")
                        .param("format", "pdf")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenOwner))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(cachedPdfHit.getResponse().getContentAsByteArray()).isEqualTo(pdfBytes);
        assertThat(tailoredResumeRepository.findByUuidAndUserId(tailoredUuid, ownerUser.getId())
                .orElseThrow().getPdfFilePath()).isEqualTo(firstPdfKey);

        mockMvc.perform(get("/api/v1/tailored-documents/resume/" + tailoredUuid + "/export")
                        .param("format", "pdf")
                        .param("regenerate", "true")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenOwner))
                .andExpect(status().isOk());
        String regeneratedPdfKey = tailoredResumeRepository.findByUuidAndUserId(tailoredUuid, ownerUser.getId())
                .orElseThrow().getPdfFilePath();
        assertThat(regeneratedPdfKey).isNotBlank().isNotEqualTo(firstPdfKey);
        assertThat(Files.exists(tempStorageDir.resolve(firstPdfKey))).isFalse();
        assertThat(Files.exists(tempStorageDir.resolve(regeneratedPdfKey))).isTrue();

        // 5. Template override streams on-the-fly with EUROPASS heading without overwriting saved MODERN template or cache keys
        MvcResult overrideResult = mockMvc.perform(get("/api/v1/tailored-documents/resume/" + tailoredUuid + "/export")
                        .param("format", "pdf")
                        .param("template", "EUROPASS")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenOwner))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("-europass-cv.pdf")))
                .andReturn();

        ExtractedResumeText overrideExtracted = resumeTextExtractor.extract(
                new ByteArrayResource(overrideResult.getResponse().getContentAsByteArray()), MIME_PDF, "europass.pdf");
        assertThat(overrideExtracted.text()).contains("PERSONAL PROFILE & SUMMARY", "WORK EXPERIENCE", "Elena Weber");

        TailoredResume afterOverride = tailoredResumeRepository.findByUuidAndUserId(tailoredUuid, ownerUser.getId()).orElseThrow();
        assertThat(afterOverride.getTemplateName()).isEqualTo(ResumeTemplate.MODERN);
        assertThat(afterOverride.getPdfFilePath()).isEqualTo(regeneratedPdfKey);
        assertThat(afterOverride.getDocxFilePath()).isEqualTo(firstDocxKey);

        // 6. PUT draft edit clears cached export paths, deletes stale files on disk, and updates future exports
        UpdateTailoredResumeRequest editReq = UpdateTailoredResumeRequest.builder()
                .title("Edited Principal Ledger Draft")
                .templateName(ResumeTemplate.CLASSIC)
                .tailoredSummary("Principal Distributed Ledger Architect scaling Java 21, Spring Boot, PostgreSQL, Docker, and Kafka.")
                .highlightedSkills(List.of("Java 21", "Spring Boot", "PostgreSQL", "Docker", "Kafka"))
                .build();

        mockMvc.perform(put("/api/v1/tailored-documents/resume/" + tailoredUuid)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenOwner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(editReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.templateName").value("CLASSIC"))
                .andExpect(jsonPath("$.editedByUser").value(true))
                .andExpect(jsonPath("$.hasPdfExport").value(false))
                .andExpect(jsonPath("$.hasDocxExport").value(false));

        assertThat(Files.exists(tempStorageDir.resolve(regeneratedPdfKey))).isFalse();
        assertThat(Files.exists(tempStorageDir.resolve(firstDocxKey))).isFalse();

        MvcResult editedPdfResult = mockMvc.perform(get("/api/v1/tailored-documents/resume/" + tailoredUuid + "/export")
                        .param("format", "pdf")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenOwner))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("-classic-cv.pdf")))
                .andReturn();

        ExtractedResumeText editedExtracted = resumeTextExtractor.extract(
                new ByteArrayResource(editedPdfResult.getResponse().getContentAsByteArray()), MIME_PDF, "classic-edited.pdf");
        assertThat(editedExtracted.text())
                .contains("EXECUTIVE SUMMARY", "Principal Distributed Ledger Architect", "Kafka");

        String postEditPdfKey = tailoredResumeRepository.findByUuidAndUserId(tailoredUuid, ownerUser.getId())
                .orElseThrow().getPdfFilePath();
        assertThat(postEditPdfKey).isNotBlank();
        assertThat(Files.exists(tempStorageDir.resolve(postEditPdfKey))).isTrue();

        // 7. DELETE tailored resume removes entity and cleans up exported binary from disk
        mockMvc.perform(delete("/api/v1/tailored-documents/resume/" + tailoredUuid)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenOwner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        assertThat(Files.exists(tempStorageDir.resolve(postEditPdfKey))).isFalse();
    }

    private RawAiTailoredResumePayload buildGroundedAiPayload(
            String headline,
            List<String> highlightedSkills,
            List<String> companies
    ) {
        List<RawOptimizedExperience> experiences = companies.stream().map(company -> {
            RawOptimizedExperience exp = new RawOptimizedExperience();
            exp.setCompanyName(company);
            exp.setDesignation("Senior Java Engineer");
            exp.setTailoredBullets(List.of(
                    "Architected SEPA Instant clearing workflows processing 5,000 TPS in Java 21 & Spring Boot",
                    "Scaled PostgreSQL ledger clusters with zero downtime"
            ));
            exp.setMatchedKeywords(List.of("Java 21", "Spring Boot", "PostgreSQL"));
            exp.setRelevanceScore(93);
            return exp;
        }).toList();

        RawTailoredProject proj = new RawTailoredProject();
        proj.setName("SEPA Instant Core");
        proj.setDescription("Sub-50ms ISO 20022 clearing engine using Java 21 and PostgreSQL.");
        proj.setTailoredBullets(List.of("Implemented resilient multi-currency ledger settlement"));
        proj.setTechnologies(List.of("Java 21", "PostgreSQL"));

        RawAiTailoredResumePayload payload = new RawAiTailoredResumePayload();
        payload.setHeadline(headline);
        payload.setTailoredSummary("Senior Java Backend Architect specializing in Java 21, Spring Boot, PostgreSQL, and Docker clearing platforms.");
        payload.setHighlightedSkills(highlightedSkills);
        payload.setAdditionalSkills(List.of("Docker"));
        payload.setMissingKeywords(List.of("Kafka"));
        payload.setTailoringRationale("Focused on SEPA ledger latency and JVM stability.");
        payload.setOptimizedExperiences(experiences);
        payload.setProjects(List.of(proj));
        return payload;
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
