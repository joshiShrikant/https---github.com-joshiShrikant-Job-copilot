package com.eurojobcopilot.modules.resume.controller;

import com.eurojobcopilot.modules.ai.client.AiJsonPromptRequest;
import com.eurojobcopilot.modules.ai.exception.AiResponseValidationException;
import com.eurojobcopilot.modules.ai.service.AiGatewayService;
import com.eurojobcopilot.modules.ai.service.AiStructuredResponse;
import com.eurojobcopilot.modules.auth.dto.RegisterRequest;
import com.eurojobcopilot.modules.auth.entity.User;
import com.eurojobcopilot.modules.auth.repository.RefreshTokenRepository;
import com.eurojobcopilot.modules.auth.repository.UserRepository;
import com.eurojobcopilot.modules.job.entity.ExperienceLevel;
import com.eurojobcopilot.modules.job.entity.Job;
import com.eurojobcopilot.modules.job.entity.JobSponsorStatus;
import com.eurojobcopilot.modules.job.repository.JobRepository;
import com.eurojobcopilot.modules.matching.entity.JobMatchScore;
import com.eurojobcopilot.modules.matching.repository.JobMatchScoreRepository;
import com.eurojobcopilot.modules.resume.dto.ApproveMasterResumeRequest;
import com.eurojobcopilot.modules.resume.dto.ParsedResumeEducationDto;
import com.eurojobcopilot.modules.resume.dto.ParsedResumeExperienceDto;
import com.eurojobcopilot.modules.resume.dto.ParsedResumeJsonDto;
import com.eurojobcopilot.modules.resume.dto.ParsedResumeProfileDto;
import com.eurojobcopilot.modules.resume.dto.ParsedResumeSkillDto;
import com.eurojobcopilot.modules.resume.dto.ProfileSyncMode;
import com.eurojobcopilot.modules.resume.entity.Resume;
import com.eurojobcopilot.modules.resume.entity.ResumeParseStatus;
import com.eurojobcopilot.modules.resume.entity.ResumeType;
import com.eurojobcopilot.modules.resume.entity.StorageProvider;
import com.eurojobcopilot.modules.resume.parser.RawAiParsedResumePayload;
import com.eurojobcopilot.modules.resume.repository.ResumeRepository;
import com.eurojobcopilot.modules.user.entity.ProficiencyLevel;
import com.eurojobcopilot.modules.user.entity.UserEducation;
import com.eurojobcopilot.modules.user.entity.UserProfile;
import com.eurojobcopilot.modules.user.entity.UserSkill;
import com.eurojobcopilot.modules.user.entity.UserWorkExperience;
import com.eurojobcopilot.modules.user.entity.WorkAuthorizationStatus;
import com.eurojobcopilot.modules.user.repository.UserEducationRepository;
import com.eurojobcopilot.modules.user.repository.UserProfileRepository;
import com.eurojobcopilot.modules.user.repository.UserSkillRepository;
import com.eurojobcopilot.modules.user.repository.UserWorkExperienceRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MasterResumeParserControllerIntegrationTest {

    private static final String MIME_PDF = "application/pdf";
    private static final String MIME_DOCX = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";

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
    private ResumeRepository resumeRepository;

    @Autowired
    private UserEducationRepository userEducationRepository;

    @Autowired
    private UserWorkExperienceRepository userWorkExperienceRepository;

    @Autowired
    private UserSkillRepository userSkillRepository;

    @Autowired
    private UserProfileRepository userProfileRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JobRepository jobRepository;

    @Autowired
    private JobMatchScoreRepository jobMatchScoreRepository;

    @MockitoBean
    private AiGatewayService aiGatewayService;

    @BeforeEach
    @AfterEach
    void cleanUp() throws IOException {
        reset(aiGatewayService);
        jobMatchScoreRepository.deleteAllInBatch();
        jobRepository.deleteAllInBatch();
        resumeRepository.deleteAllInBatch();
        userEducationRepository.deleteAllInBatch();
        userWorkExperienceRepository.deleteAllInBatch();
        userSkillRepository.deleteAllInBatch();
        userProfileRepository.deleteAllInBatch();
        refreshTokenRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();

        if (Files.exists(tempStorageDir)) {
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
    void parsePdfAndApproveWorkflow_extractsTextRequiresHumanReviewAndSyncsProfile() throws Exception {
        String token = registerUser("elena.parser@eurojob.eu", "Elena", "Weber");

        byte[] realPdfBytes = createPdfBytes(
                "Elena Weber - Senior Java Backend Architect",
                "Berlin, Germany | linkedin.com/in/elena-weber | github.com/elena-weber",
                "8 years building high-throughput European payment and clearing platforms.",
                "Skills: Java 21, Spring Boot, PostgreSQL, Apache Kafka, Kubernetes",
                "Work Experience:",
                "Adyen N.V. - Senior Java Engineer (2022-03 to Present) in Amsterdam",
                "Designed resilient SEPA Instant settlement microservices handling 5k TPS.",
                "Education:",
                "Technical University of Munich - M.Sc. Computer Science (2016-10 to 2018-09)"
        );

        MvcResult uploadResult = mockMvc.perform(multipart("/api/v1/resumes/upload")
                        .file(new MockMultipartFile("file", "elena-cv.pdf", MIME_PDF, realPdfBytes))
                        .param("title", "Elena Master Architect CV")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.parseStatus").value("NOT_PARSED"))
                .andExpect(jsonPath("$.hasParsedContent").value(false))
                .andExpect(jsonPath("$.isMasterApproved").value(false))
                .andReturn();
        String resumeUuid = objectMapper.readTree(uploadResult.getResponse().getContentAsString()).get("uuid").asText();

        RawAiParsedResumePayload aiDraft = buildSampleRawPayload("Elena Weber", "Senior Java Architect", "2022-03", null);
        when(aiGatewayService.executeStructuredJsonPrompt(any(AiJsonPromptRequest.class), eq(RawAiParsedResumePayload.class)))
                .thenReturn(new AiStructuredResponse<>("GEMINI", "gemini-2.5-flash", aiDraft, "{}", 420, 310));

        // 1. Parse PDF -> saves structured draft, keeps isMasterApproved = false!
        mockMvc.perform(post("/api/v1/resumes/" + resumeUuid + "/parse")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resumeUuid").value(resumeUuid))
                .andExpect(jsonPath("$.parseStatus").value("PARSED"))
                .andExpect(jsonPath("$.isMasterApproved").value(false))
                .andExpect(jsonPath("$.aiProvider").value("GEMINI"))
                .andExpect(jsonPath("$.aiModelUsed").value("gemini-2.5-flash"))
                .andExpect(jsonPath("$.extractedCharCount").isNumber())
                .andExpect(jsonPath("$.parsedData.profile.currentDesignation").value("Senior Java Architect"))
                .andExpect(jsonPath("$.parsedData.profile.currentCountry").value("Germany"))
                .andExpect(jsonPath("$.parsedData.profile.linkedinUrl").value("https://linkedin.com/in/elena-weber"))
                .andExpect(jsonPath("$.parsedData.experiences[0].startDate").value("2022-03-01"))
                .andExpect(jsonPath("$.parsedData.experiences[0].current").value(true))
                .andExpect(jsonPath("$.warnings").isArray());

        ArgumentCaptor<AiJsonPromptRequest> promptCaptor = ArgumentCaptor.forClass(AiJsonPromptRequest.class);
        verify(aiGatewayService).executeStructuredJsonPrompt(promptCaptor.capture(), eq(RawAiParsedResumePayload.class));
        assertThat(promptCaptor.getValue().userPrompt())
                .contains("Elena Weber", "Adyen N.V.", "SEPA Instant", "Technical University of Munich");

        // Profile MUST remain untouched prior to human approval
        User elena = userRepository.findByEmailIgnoreCase("elena.parser@eurojob.eu").orElseThrow();
        assertThat(userProfileRepository.findByUserId(elena.getId())).isEmpty();
        assertThat(userSkillRepository.countByUserId(elena.getId())).isZero();
        assertThat(userWorkExperienceRepository.countByUserId(elena.getId())).isZero();

        // 2. GET /parsed returns persisted draft (and forceRefresh=false returns cached without extra AI call)
        MvcResult getParsedResult = mockMvc.perform(get("/api/v1/resumes/" + resumeUuid + "/parsed")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.parseStatus").value("PARSED"))
                .andExpect(jsonPath("$.parsedData.skills.length()").value(2))
                .andReturn();

        ParsedResumeJsonDto editedDraft = objectMapper.treeToValue(
                objectMapper.readTree(getParsedResult.getResponse().getContentAsString()).get("parsedData"),
                ParsedResumeJsonDto.class
        );
        // Human edits in review UI: sets country for TUM education, promotes Kubernetes, updates years
        editedDraft.getProfile().setYearsOfExperience(new BigDecimal("8.5"));
        editedDraft.getEducations().get(0).setCountry("Germany");
        editedDraft.getSkills().get(1).setPrimary(true);

        ApproveMasterResumeRequest approveReq = ApproveMasterResumeRequest.builder()
                .resumeUuid(resumeUuid)
                .approvedParsedData(editedDraft)
                .syncMode(ProfileSyncMode.MERGE)
                .build();

        Job activeJavaJob = jobRepository.saveAndFlush(Job.builder()
                .canonicalHash("0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef")
                .companyName("Adyen N.V.")
                .title("Senior Java Architect")
                .country("Germany")
                .experienceLevel(ExperienceLevel.SENIOR)
                .skills(List.of("Java", "Kubernetes"))
                .sponsorStatus(JobSponsorStatus.VERIFIED_SPONSOR)
                .description("Java backend systems.")
                .sourceProvider("ARBEITNOW")
                .originalUrl("https://careers.adyen.com/arch")
                .postedAt(java.time.Instant.now())
                .active(true)
                .build());

        // 3. Approve -> marks approved & syncs profile + skills + experience + education
        mockMvc.perform(post("/api/v1/resumes/master/approve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(approveReq))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.uuid").value(resumeUuid))
                .andExpect(jsonPath("$.isMasterApproved").value(true))
                .andExpect(jsonPath("$.parseStatus").value("APPROVED"))
                .andExpect(jsonPath("$.approvedAt").isNotEmpty())
                .andExpect(jsonPath("$.hasParsedContent").value(true))
                .andExpect(jsonPath("$.profileSyncSummary.syncMode").value("MERGE"))
                .andExpect(jsonPath("$.profileSyncSummary.profileCreated").value(true))
                .andExpect(jsonPath("$.profileSyncSummary.skillsAdded").value(2))
                .andExpect(jsonPath("$.profileSyncSummary.experiencesAdded").value(1))
                .andExpect(jsonPath("$.profileSyncSummary.educationsAdded").value(1));

        JobMatchScore syncedScore = jobMatchScoreRepository.findByUserIdAndJobId(elena.getId(), activeJavaJob.getId())
                .orElseThrow();
        assertThat(syncedScore.getFinalScore()).isGreaterThan(BigDecimal.ZERO);
        assertThat(syncedScore.getSkillScore()).isEqualByComparingTo("100.00");
        assertThat(syncedScore.getExperienceScore()).isEqualByComparingTo("100.00");
        assertThat(syncedScore.getTitleScore()).isEqualByComparingTo("100.00");

        // 4. Verify synced entities via /api/v1/profile
        mockMvc.perform(get("/api/v1/profile")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentDesignation").value("Senior Java Architect"))
                .andExpect(jsonPath("$.yearsOfExperience").value(8.5))
                .andExpect(jsonPath("$.currentCountry").value("Germany"))
                .andExpect(jsonPath("$.skills.length()").value(2))
                .andExpect(jsonPath("$.experiences.length()").value(1))
                .andExpect(jsonPath("$.experiences[0].companyName").value("Adyen N.V."))
                .andExpect(jsonPath("$.educations.length()").value(1))
                .andExpect(jsonPath("$.educations[0].country").value("Germany"));
    }

    @Test
    void parseDocx_mergeVsReplaceModes_andSupersedesPriorApprovedMasterVersion() throws Exception {
        String token = registerUser("lukas.docx@eurojob.eu", "Lukas", "Meyer");
        User lukas = userRepository.findByEmailIgnoreCase("lukas.docx@eurojob.eu").orElseThrow();

        // Seed existing manual profile + manual skill + old resume v1 already approved
        userProfileRepository.saveAndFlush(UserProfile.builder()
                .user(lukas)
                .currentDesignation("Backend Engineer")
                .yearsOfExperience(new BigDecimal("5.0"))
                .expectedSalaryMin(new BigDecimal("95000.00"))
                .expectedSalaryCurrency("EUR")
                .noticePeriodDays(60)
                .workAuthorizationStatus(WorkAuthorizationStatus.EU_CITIZEN)
                .build());
        userSkillRepository.saveAndFlush(UserSkill.builder()
                .user(lukas)
                .skillName("GraphQL")
                .normalizedSkill("graphql")
                .yearsExperience(new BigDecimal("2.0"))
                .proficiencyLevel(ProficiencyLevel.INTERMEDIATE)
                .primary(false)
                .build());
        userWorkExperienceRepository.saveAndFlush(UserWorkExperience.builder()
                .user(lukas)
                .companyName("Old Manual Startup")
                .designation("Junior Dev")
                .startDate(LocalDate.of(2019, 1, 1))
                .endDate(LocalDate.of(2020, 12, 31))
                .current(false)
                .build());

        Resume v1 = resumeRepository.saveAndFlush(Resume.builder()
                .uuid(UUID.randomUUID().toString())
                .user(lukas)
                .resumeType(ResumeType.MASTER)
                .title("Legacy Master v1")
                .fileName("legacy.pdf")
                .filePath(lukas.getUuid() + "/legacy.pdf")
                .fileSizeBytes(100L)
                .mimeType(MIME_PDF)
                .storageProvider(StorageProvider.LOCAL)
                .versionNumber(1)
                .masterApproved(true)
                .parseStatus(ResumeParseStatus.APPROVED)
                .build());

        byte[] docxBytes = createDocxBytes(
                "Lukas Meyer | Amsterdam, Netherlands | github.com/lukas-meyer",
                "Principal Platform Engineer with Java 21, Spring Boot, Kubernetes, and PostgreSQL experience.",
                "Booking.com - Principal Engineer (2021-04 to Present): Cloud FinOps and Platform reliability.",
                "Delft University of Technology - B.Sc. Computer Science (2015-09 to 2018-06), Netherlands"
        );

        MvcResult uploadV2 = mockMvc.perform(multipart("/api/v1/resumes/upload")
                        .file(new MockMultipartFile("file", "lukas-2026.docx", MIME_DOCX, docxBytes))
                        .param("title", "Master CV 2026 DOCX")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isCreated())
                .andReturn();
        String v2Uuid = objectMapper.readTree(uploadV2.getResponse().getContentAsString()).get("uuid").asText();

        RawAiParsedResumePayload docxAiPayload = buildSampleRawPayload("Lukas Meyer", "Principal Platform Engineer", "2021-04-01", "Netherlands");
        when(aiGatewayService.executeStructuredJsonPrompt(any(), eq(RawAiParsedResumePayload.class)))
                .thenReturn(new AiStructuredResponse<>("OPENAI", "gpt-4o-mini", docxAiPayload, "{}", 300, 220));

        mockMvc.perform(post("/api/v1/resumes/" + v2Uuid + "/parse")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.parseStatus").value("PARSED"))
                .andExpect(jsonPath("$.aiProvider").value("OPENAI"));

        ArgumentCaptor<AiJsonPromptRequest> captor = ArgumentCaptor.forClass(AiJsonPromptRequest.class);
        verify(aiGatewayService).executeStructuredJsonPrompt(captor.capture(), eq(RawAiParsedResumePayload.class));
        assertThat(captor.getValue().userPrompt()).contains("Lukas Meyer", "Booking.com", "Delft University");

        ParsedResumeJsonDto confirmedData = ParsedResumeJsonDto.builder()
                .profile(ParsedResumeProfileDto.builder()
                        .currentDesignation("Principal Platform Engineer")
                        .yearsOfExperience(new BigDecimal("7.0"))
                        .currentCountry("Netherlands")
                        .currentCity("Amsterdam")
                        .githubUrl("https://github.com/lukas-meyer")
                        .build())
                .summary("Principal engineer in Amsterdam.")
                .skills(List.of(
                        ParsedResumeSkillDto.builder().skillName("Java 21").proficiencyLevel(ProficiencyLevel.EXPERT).primary(true).build(),
                        ParsedResumeSkillDto.builder().skillName("Kubernetes").proficiencyLevel(ProficiencyLevel.ADVANCED).primary(true).build()
                ))
                .experiences(List.of(
                        ParsedResumeExperienceDto.builder()
                                .companyName("Booking.com")
                                .designation("Principal Engineer")
                                .startDate(LocalDate.of(2021, 4, 1))
                                .current(true)
                                .skillsUsed(List.of("Java 21", "Kubernetes"))
                                .build()
                ))
                .educations(List.of(
                        ParsedResumeEducationDto.builder()
                                .institution("Delft University of Technology")
                                .degree("B.Sc.")
                                .fieldOfStudy("Computer Science")
                                .startDate(LocalDate.of(2015, 9, 1))
                                .endDate(LocalDate.of(2018, 6, 30))
                                .country("Netherlands")
                                .build()
                ))
                .build();

        // Approve v2 in MERGE mode -> preserves GraphQL + Old Manual Startup + salary=95000, AND demotes v1!
        mockMvc.perform(post("/api/v1/resumes/master/approve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(ApproveMasterResumeRequest.builder()
                                .resumeUuid(v2Uuid)
                                .approvedParsedData(confirmedData)
                                .syncMode(ProfileSyncMode.MERGE)
                                .build()))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isMasterApproved").value(true))
                .andExpect(jsonPath("$.profileSyncSummary.syncMode").value("MERGE"))
                .andExpect(jsonPath("$.profileSyncSummary.skillsAdded").value(2));

        Resume reloadedV1 = resumeRepository.findByUuid(v1.getUuid()).orElseThrow();
        assertThat(reloadedV1.isMasterApproved()).isFalse();
        assertThat(reloadedV1.getParseStatus()).isEqualTo(ResumeParseStatus.PARSED);

        assertThat(userSkillRepository.countByUserId(lukas.getId())).isEqualTo(3); // GraphQL + Java 21 + Kubernetes
        assertThat(userWorkExperienceRepository.countByUserId(lukas.getId())).isEqualTo(2);
        UserProfile updatedProfile = userProfileRepository.findByUserId(lukas.getId()).orElseThrow();
        assertThat(updatedProfile.getExpectedSalaryMin()).isEqualByComparingTo("95000.00");
        assertThat(updatedProfile.getNoticePeriodDays()).isEqualTo(60);

        // Re-approve v2 in REPLACE mode -> removes manual entries not present in approved master CV while preserving salary settings
        mockMvc.perform(post("/api/v1/resumes/master/approve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(ApproveMasterResumeRequest.builder()
                                .resumeUuid(v2Uuid)
                                .approvedParsedData(confirmedData)
                                .syncMode(ProfileSyncMode.REPLACE)
                                .build()))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profileSyncSummary.syncMode").value("REPLACE"))
                .andExpect(jsonPath("$.profileSyncSummary.skillsUpdated").value(2));

        assertThat(userSkillRepository.countByUserId(lukas.getId())).isEqualTo(2);
        assertThat(userWorkExperienceRepository.countByUserId(lukas.getId())).isEqualTo(1);
        assertThat(userEducationRepository.countByUserId(lukas.getId())).isEqualTo(1);
        assertThat(userProfileRepository.findByUserId(lukas.getId()).orElseThrow().getExpectedSalaryMin())
                .isEqualByComparingTo("95000.00");
    }

    @Test
    void failureHandlingAndSecurityIsolation_returnsProperStatusesWithoutCorruptingState() throws Exception {
        String tokenOwner = registerUser("owner.fail@eurojob.eu", "Clara", "Berg");
        String tokenOther = registerUser("other.fail@eurojob.eu", "Hans", "Klein");

        // 1. Blank/scanned PDF rejected at extraction with 400 without calling AI
        byte[] blankPdf = createPdfBytes("Scan");
        MvcResult blankUpload = mockMvc.perform(multipart("/api/v1/resumes/upload")
                        .file(new MockMultipartFile("file", "scanned.pdf", MIME_PDF, blankPdf))
                        .param("title", "Scanned CV")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenOwner))
                .andExpect(status().isCreated())
                .andReturn();
        String blankUuid = objectMapper.readTree(blankUpload.getResponse().getContentAsString()).get("uuid").asText();

        mockMvc.perform(post("/api/v1/resumes/" + blankUuid + "/parse")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenOwner))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
        verifyNoInteractions(aiGatewayService);

        // 2. Upload readable PDF, mock AI validation failure -> 502 keeps state NOT_PARSED
        byte[] validPdf = createPdfBytes(
                "Clara Berg - Cloud Security Engineer in Zurich, Switzerland",
                "10 years experience securing Kubernetes clusters and AWS pipelines."
        );
        MvcResult validUpload = mockMvc.perform(multipart("/api/v1/resumes/upload")
                        .file(new MockMultipartFile("file", "clara.pdf", MIME_PDF, validPdf))
                        .param("title", "Clara Master CV")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenOwner))
                .andExpect(status().isCreated())
                .andReturn();
        String validUuid = objectMapper.readTree(validUpload.getResponse().getContentAsString()).get("uuid").asText();

        when(aiGatewayService.executeStructuredJsonPrompt(any(), eq(RawAiParsedResumePayload.class)))
                .thenThrow(new AiResponseValidationException("Malformed AI JSON schema"));

        mockMvc.perform(post("/api/v1/resumes/" + validUuid + "/parse")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenOwner))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.status").value(502));

        Resume unchanged = resumeRepository.findByUuid(validUuid).orElseThrow();
        assertThat(unchanged.getParseStatus()).isEqualTo(ResumeParseStatus.NOT_PARSED);
        assertThat(unchanged.isMasterApproved()).isFalse();

        // 3. Approval missing startDate -> 400
        ApproveMasterResumeRequest missingStartDate = ApproveMasterResumeRequest.builder()
                .resumeUuid(validUuid)
                .approvedParsedData(ParsedResumeJsonDto.builder()
                        .experiences(List.of(ParsedResumeExperienceDto.builder()
                                .companyName("UBS")
                                .designation("Security Architect")
                                .startDate(null)
                                .build()))
                        .build())
                .build();

        mockMvc.perform(post("/api/v1/resumes/master/approve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(missingStartDate))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenOwner))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        // 4. Cross-user isolation -> 404
        mockMvc.perform(post("/api/v1/resumes/" + validUuid + "/parse")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenOther))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/resumes/" + validUuid + "/parsed")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenOther))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/v1/resumes/master/approve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(ApproveMasterResumeRequest.builder()
                                .resumeUuid(validUuid)
                                .approvedParsedData(ParsedResumeJsonDto.builder().summary("Hijack").build())
                                .build()))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenOther))
                .andExpect(status().isNotFound());
    }

    private RawAiParsedResumePayload buildSampleRawPayload(String name, String designation, String expStart, String eduCountry) {
        RawAiParsedResumePayload payload = new RawAiParsedResumePayload();
        RawAiParsedResumePayload.RawProfile p = new RawAiParsedResumePayload.RawProfile();
        p.setFullName(name);
        p.setCurrentDesignation(designation);
        p.setYearsOfExperience(new BigDecimal("8.0"));
        p.setCurrentCountry("germany");
        p.setCurrentCity("Berlin");
        p.setLinkedinUrl("linkedin.com/in/elena-weber");
        p.setWorkAuthorizationStatus("EU_CITIZEN");
        payload.setProfile(p);
        payload.setSummary(name + " - " + designation + " specializing in distributed systems.");

        RawAiParsedResumePayload.RawSkill s1 = new RawAiParsedResumePayload.RawSkill();
        s1.setSkillName("Java 21");
        s1.setYearsExperience(new BigDecimal("8.0"));
        s1.setProficiencyLevel("EXPERT");
        s1.setPrimary(true);

        RawAiParsedResumePayload.RawSkill s2 = new RawAiParsedResumePayload.RawSkill();
        s2.setSkillName("Kubernetes");
        s2.setYearsExperience(new BigDecimal("4.0"));
        s2.setProficiencyLevel("ADVANCED");
        s2.setPrimary(false);

        RawAiParsedResumePayload.RawSkill s2Dup = new RawAiParsedResumePayload.RawSkill();
        s2Dup.setSkillName("kubernetes");
        s2Dup.setPrimary(false);
        payload.setSkills(List.of(s1, s2, s2Dup));

        RawAiParsedResumePayload.RawExperience exp = new RawAiParsedResumePayload.RawExperience();
        exp.setCompanyName("Adyen N.V.");
        exp.setDesignation("Senior Java Engineer");
        exp.setLocation("Amsterdam, Netherlands");
        exp.setStartDate(expStart);
        exp.setEndDate("Present");
        exp.setDescription("Built resilient SEPA clearing engines.");
        exp.setSkillsUsed(List.of("Java 21", "PostgreSQL", "Kafka"));
        payload.setExperiences(List.of(exp));

        RawAiParsedResumePayload.RawEducation edu = new RawAiParsedResumePayload.RawEducation();
        edu.setInstitution("Technical University of Munich");
        edu.setDegree("M.Sc.");
        edu.setFieldOfStudy("Computer Science");
        edu.setStartDate("2016-10-01");
        edu.setEndDate("2018-09-30");
        edu.setCountry(eduCountry);
        payload.setEducations(List.of(edu));
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

    private byte[] createPdfBytes(String... lines) throws IOException {
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage();
            doc.addPage(page);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                cs.beginText();
                cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 11);
                cs.setLeading(15f);
                cs.newLineAtOffset(40, 740);
                for (String line : lines) {
                    cs.showText(line);
                    cs.newLine();
                }
                cs.endText();
            }
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            doc.save(baos);
            return baos.toByteArray();
        }
    }

    private byte[] createDocxBytes(String headerLine, String... bodyLines) throws IOException {
        StringBuilder bodyXml = new StringBuilder("<w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\"><w:body>");
        for (String line : bodyLines) {
            bodyXml.append("<w:p><w:r><w:t>").append(line).append("</w:t></w:r></w:p>");
        }
        bodyXml.append("</w:body></w:document>");

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos, StandardCharsets.UTF_8)) {
            writeZipEntry(zos, "[Content_Types].xml",
                    "<?xml version=\"1.0\" encoding=\"UTF-8\"?><Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\"/>");
            writeZipEntry(zos, "_rels/.rels",
                    "<?xml version=\"1.0\" encoding=\"UTF-8\"?><Relationships/>");
            writeZipEntry(zos, "word/header1.xml",
                    "<?xml version=\"1.0\" encoding=\"UTF-8\"?><w:hdr xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\"><w:p><w:r><w:t>"
                            + headerLine + "</w:t></w:r></w:p></w:hdr>");
            writeZipEntry(zos, "word/document.xml",
                    "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" + bodyXml);
        }
        return baos.toByteArray();
    }

    private void writeZipEntry(ZipOutputStream zos, String name, String content) throws IOException {
        zos.putNextEntry(new ZipEntry(name));
        zos.write(content.getBytes(StandardCharsets.UTF_8));
        zos.closeEntry();
    }
}

