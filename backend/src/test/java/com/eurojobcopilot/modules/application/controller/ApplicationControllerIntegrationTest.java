package com.eurojobcopilot.modules.application.controller;

import com.eurojobcopilot.core.enums.WorkArrangement;
import com.eurojobcopilot.modules.application.dto.CreateApplicationRequest;
import com.eurojobcopilot.modules.application.dto.UpdateApplicationRequest;
import com.eurojobcopilot.modules.application.dto.UpdateApplicationStatusRequest;
import com.eurojobcopilot.modules.application.entity.ApplicationStatus;
import com.eurojobcopilot.modules.application.repository.ApplicationStatusHistoryRepository;
import com.eurojobcopilot.modules.application.repository.JobApplicationRepository;
import com.eurojobcopilot.modules.auth.dto.RegisterRequest;
import com.eurojobcopilot.modules.auth.entity.User;
import com.eurojobcopilot.modules.auth.repository.RefreshTokenRepository;
import com.eurojobcopilot.modules.auth.repository.UserRepository;
import com.eurojobcopilot.modules.coverletter.entity.CoverLetter;
import com.eurojobcopilot.modules.coverletter.entity.CoverLetterTone;
import com.eurojobcopilot.modules.coverletter.repository.CoverLetterRepository;
import com.eurojobcopilot.modules.job.entity.EmploymentType;
import com.eurojobcopilot.modules.job.entity.ExperienceLevel;
import com.eurojobcopilot.modules.job.entity.Job;
import com.eurojobcopilot.modules.job.entity.JobSponsorStatus;
import com.eurojobcopilot.modules.job.repository.JobRepository;
import com.eurojobcopilot.modules.job.repository.UserJobInteractionRepository;
import com.eurojobcopilot.modules.matching.entity.JobMatchScore;
import com.eurojobcopilot.modules.matching.repository.JobMatchScoreRepository;
import com.eurojobcopilot.modules.recruiter.entity.CommunicationChannel;
import com.eurojobcopilot.modules.recruiter.entity.Recruiter;
import com.eurojobcopilot.modules.recruiter.entity.RecruiterInteraction;
import com.eurojobcopilot.modules.recruiter.entity.RecruiterMessageStatus;
import com.eurojobcopilot.modules.recruiter.entity.RecruiterMessageType;
import com.eurojobcopilot.modules.recruiter.repository.RecruiterInteractionRepository;
import com.eurojobcopilot.modules.recruiter.repository.RecruiterRepository;
import com.eurojobcopilot.modules.resume.entity.Resume;
import com.eurojobcopilot.modules.resume.entity.ResumeTemplate;
import com.eurojobcopilot.modules.resume.entity.ResumeType;
import com.eurojobcopilot.modules.resume.entity.StorageProvider;
import com.eurojobcopilot.modules.resume.entity.TailoredResume;
import com.eurojobcopilot.modules.resume.repository.ResumeRepository;
import com.eurojobcopilot.modules.resume.repository.TailoredResumeRepository;
import com.eurojobcopilot.modules.sponsorship.entity.Company;
import com.eurojobcopilot.modules.sponsorship.entity.SponsorConfidenceLevel;
import com.eurojobcopilot.modules.sponsorship.repository.CompanyRepository;
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
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ApplicationControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private RecruiterInteractionRepository recruiterInteractionRepository;
    @Autowired
    private RecruiterRepository recruiterRepository;
    @Autowired
    private ApplicationStatusHistoryRepository statusHistoryRepository;
    @Autowired
    private JobApplicationRepository jobApplicationRepository;
    @Autowired
    private CoverLetterRepository coverLetterRepository;
    @Autowired
    private TailoredResumeRepository tailoredResumeRepository;
    @Autowired
    private ResumeRepository resumeRepository;
    @Autowired
    private UserJobInteractionRepository userJobInteractionRepository;
    @Autowired
    private JobMatchScoreRepository jobMatchScoreRepository;
    @Autowired
    private JobRepository jobRepository;
    @Autowired
    private CompanyRepository companyRepository;
    @Autowired
    private RefreshTokenRepository refreshTokenRepository;
    @Autowired
    private UserRepository userRepository;

    private String tokenOwner;
    private String tokenOther;
    private User ownerUser;
    private User otherUser;
    private Job adyenJob;
    private Job sapJob;
    private TailoredResume ownerTailoredCv;
    private CoverLetter ownerCoverLetter;

    @BeforeEach
    void setUp() throws Exception {
        cleanDatabase();
        tokenOwner = registerUser("pipeline.owner@eurojob.eu", "Lukas", "Becker");
        tokenOther = registerUser("pipeline.other@eurojob.eu", "Anna", "Keller");

        ownerUser = userRepository.findByEmailIgnoreCase("pipeline.owner@eurojob.eu").orElseThrow();
        otherUser = userRepository.findByEmailIgnoreCase("pipeline.other@eurojob.eu").orElseThrow();

        Company adyen = companyRepository.saveAndFlush(Company.builder()
                .name("Adyen N.V.")
                .normalizedName("adyen")
                .headquartersCountry("Netherlands")
                .verifiedSponsor(true)
                .sponsorConfidenceLevel(SponsorConfidenceLevel.VERIFIED)
                .build());

        adyenJob = jobRepository.saveAndFlush(Job.builder()
                .canonicalHash(TestEntityFactory.sha256Hex("app-adyen-1"))
                .company(adyen)
                .companyName("Adyen N.V.")
                .title("Staff Java Architect")
                .normalizedTitle("staff java architect")
                .country("Netherlands")
                .city("Amsterdam")
                .location("Amsterdam, Netherlands")
                .workArrangement(WorkArrangement.HYBRID)
                .employmentType(EmploymentType.FULL_TIME)
                .experienceLevel(ExperienceLevel.LEAD)
                .salaryMin(new BigDecimal("95000.00"))
                .salaryMax(new BigDecimal("125000.00"))
                .salaryCurrency("EUR")
                .sponsorStatus(JobSponsorStatus.VERIFIED_SPONSOR)
                .description("Lead European real-time payments architecture.")
                .sourceProvider("ARBEITNOW")
                .originalUrl("https://careers.adyen.com/staff-java-1")
                .postedAt(Instant.now().minus(4, ChronoUnit.HOURS))
                .active(true)
                .build());

        sapJob = jobRepository.saveAndFlush(Job.builder()
                .canonicalHash(TestEntityFactory.sha256Hex("app-sap-2"))
                .companyName("SAP SE")
                .title("Principal Cloud Engineer")
                .normalizedTitle("principal cloud engineer")
                .country("Germany")
                .city("Walldorf")
                .location("Walldorf, Germany")
                .workArrangement(WorkArrangement.HYBRID)
                .employmentType(EmploymentType.FULL_TIME)
                .sponsorStatus(JobSponsorStatus.RELOCATION_OFFERED)
                .description("Design resilient enterprise Kubernetes workloads.")
                .sourceProvider("ARBEITNOW")
                .originalUrl("https://jobs.sap.com/cloud-2")
                .postedAt(Instant.now().minus(2, ChronoUnit.HOURS))
                .active(true)
                .build());

        jobMatchScoreRepository.saveAndFlush(JobMatchScore.builder()
                .user(ownerUser)
                .job(adyenJob)
                .skillScore(new BigDecimal("92.00"))
                .experienceScore(new BigDecimal("90.00"))
                .titleScore(new BigDecimal("90.00"))
                .locationScore(new BigDecimal("100.00"))
                .sponsorshipScore(new BigDecimal("100.00"))
                .salaryScore(new BigDecimal("85.00"))
                .finalScore(new BigDecimal("91.00"))
                .build());

        Resume ownerMaster = resumeRepository.saveAndFlush(Resume.builder()
                .user(ownerUser)
                .resumeType(ResumeType.MASTER)
                .title("Master Java CV")
                .fileName("master.pdf")
                .filePath("users/" + ownerUser.getUuid() + "/master.pdf")
                .fileSizeBytes(1024L)
                .mimeType("application/pdf")
                .storageProvider(StorageProvider.LOCAL)
                .versionNumber(1)
                .masterApproved(true)
                .build());

        ownerTailoredCv = tailoredResumeRepository.saveAndFlush(TailoredResume.builder()
                .masterResume(ownerMaster)
                .job(adyenJob)
                .user(ownerUser)
                .title("Adyen Payments CV (v1)")
                .templateName(ResumeTemplate.MODERN)
                .resumeDataJson(Map.of("headline", "Staff Java Architect"))
                .versionNumber(1)
                .build());

        ownerCoverLetter = coverLetterRepository.saveAndFlush(CoverLetter.builder()
                .user(ownerUser)
                .job(adyenJob)
                .tailoredResume(ownerTailoredCv)
                .title("Adyen Cover Letter (v1)")
                .content("Dear Hiring Team at Adyen...")
                .tone(CoverLetterTone.PROFESSIONAL)
                .wordCount(42)
                .versionNumber(1)
                .build());
    }

    @AfterEach
    void cleanDatabase() {
        recruiterInteractionRepository.deleteAllInBatch();
        recruiterRepository.deleteAllInBatch();
        statusHistoryRepository.deleteAllInBatch();
        jobApplicationRepository.deleteAllInBatch();
        coverLetterRepository.deleteAllInBatch();
        tailoredResumeRepository.deleteAllInBatch();
        resumeRepository.deleteAllInBatch();
        userJobInteractionRepository.deleteAllInBatch();
        jobMatchScoreRepository.deleteAllInBatch();
        jobRepository.deleteAllInBatch();
        companyRepository.deleteAllInBatch();
        refreshTokenRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
    }

    @Test
    void fullApplicationPipelineLifecycle_statusTransitionsDatesOfferIsolationAndSafeDelete() throws Exception {
        // 1. Auth & validation boundaries
        mockMvc.perform(get("/api/v1/applications"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/v1/applications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"jobId\":" + adyenJob.getId() + "}"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/v1/applications/not-a-uuid")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenOwner))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/v1/applications")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenOwner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"jobId\":999999}"))
                .andExpect(status().isNotFound());

        // Cross-user document reference cannot be linked (other user trying owner's CV UUID -> 404)
        mockMvc.perform(post("/api/v1/applications")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenOther)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(CreateApplicationRequest.builder()
                                .jobId(adyenJob.getId())
                                .tailoredResumeUuid(ownerTailoredCv.getUuid())
                                .build())))
                .andExpect(status().isNotFound());

        // 2. Create tracked application in SAVED stage with linked CV & cover letter
        CreateApplicationRequest createAdyen = CreateApplicationRequest.builder()
                .jobId(adyenJob.getId())
                .status(ApplicationStatus.SAVED)
                .tailoredResumeUuid(ownerTailoredCv.getUuid())
                .coverLetterUuid(ownerCoverLetter.getUuid())
                .nextFollowUpDate(LocalDate.now().minusDays(1)) // overdue reminder to verify filter
                .notes("Tailor SEPA bullets before applying.")
                .transitionNotes("Saved from Job Discovery")
                .build();

        MvcResult createResult = mockMvc.perform(post("/api/v1/applications")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenOwner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createAdyen)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.uuid").isNotEmpty())
                .andExpect(jsonPath("$.jobId").value(adyenJob.getId()))
                .andExpect(jsonPath("$.status").value("SAVED"))
                .andExpect(jsonPath("$.matchPercentage").value(91))
                .andExpect(jsonPath("$.overdueFollowUp").value(true))
                .andExpect(jsonPath("$.tailoredResume.uuid").value(ownerTailoredCv.getUuid()))
                .andExpect(jsonPath("$.coverLetter.uuid").value(ownerCoverLetter.getUuid()))
                .andExpect(jsonPath("$.statusHistory.length()").value(1))
                .andExpect(jsonPath("$.statusHistory[0].newStatus").value("SAVED"))
                .andReturn();

        String adyenAppUuid = objectMapper.readTree(createResult.getResponse().getContentAsString()).get("uuid").asText();

        // Duplicate (userId, jobId) rejected with 409 Conflict
        mockMvc.perform(post("/api/v1/applications")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenOwner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createAdyen)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));

        // Track second job in OFFER stage
        MvcResult sapResult = mockMvc.perform(post("/api/v1/applications")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenOwner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(CreateApplicationRequest.builder()
                                .jobId(sapJob.getId())
                                .status(ApplicationStatus.APPLIED)
                                .nextInterviewDate(Instant.now().plus(3, ChronoUnit.DAYS))
                                .currentSalaryOffered(new BigDecimal("108000.00"))
                                .offeredCurrency("eur")
                                .build())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.appliedDate").isNotEmpty())
                .andExpect(jsonPath("$.offeredCurrency").value("EUR"))
                .andReturn();
        String sapAppUuid = objectMapper.readTree(sapResult.getResponse().getContentAsString()).get("uuid").asText();

        // 3. Stage transitions via PATCH /status append timeline & auto-set appliedDate
        mockMvc.perform(patch("/api/v1/applications/" + adyenAppUuid + "/status")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenOwner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(UpdateApplicationStatusRequest.builder()
                                .newStatus(ApplicationStatus.APPLIED)
                                .transitionNotes("Submitted online with v1 CV & Cover Letter.")
                                .build())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPLIED"))
                .andExpect(jsonPath("$.appliedDate").isNotEmpty())
                .andExpect(jsonPath("$.statusHistory.length()").value(2));

        mockMvc.perform(patch("/api/v1/applications/" + adyenAppUuid + "/status")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenOwner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(UpdateApplicationStatusRequest.builder()
                                .newStatus(ApplicationStatus.TECH_INTERVIEW)
                                .transitionNotes("Passed hacker-rank; scheduled architecture round.")
                                .nextInterviewDate(Instant.now().plus(5, ChronoUnit.DAYS))
                                .build())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("TECH_INTERVIEW"))
                .andExpect(jsonPath("$.nextInterviewDate").isNotEmpty())
                .andExpect(jsonPath("$.statusHistory.length()").value(3))
                .andExpect(jsonPath("$.statusHistory[0].previousStatus").value("APPLIED"))
                .andExpect(jsonPath("$.statusHistory[0].newStatus").value("TECH_INTERVIEW"));

        // Verify job detail now reflects hasApplied=true and tracked application summary
        mockMvc.perform(get("/api/v1/jobs/" + adyenJob.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenOwner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasApplied").value(true))
                .andExpect(jsonPath("$.application.uuid").value(adyenAppUuid))
                .andExpect(jsonPath("$.application.status").value("TECH_INTERVIEW"));

        // 4. Update notes, salary offer, follow-up completion via PUT + optimistic version check
        MvcResult beforePut = mockMvc.perform(get("/api/v1/applications/" + adyenAppUuid)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenOwner))
                .andExpect(status().isOk())
                .andReturn();
        int currentVersion = objectMapper.readTree(beforePut.getResponse().getContentAsString()).get("version").asInt();

        mockMvc.perform(put("/api/v1/applications/" + adyenAppUuid)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenOwner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(UpdateApplicationRequest.builder()
                                .currentSalaryOffered(new BigDecimal("118000.00"))
                                .offeredCurrency("EUR")
                                .followUpCompleted(true)
                                .notes("Strong alignment with SEPA platform team; target 118k base.")
                                .expectedVersion(currentVersion)
                                .build())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentSalaryOffered").value(118000.00))
                .andExpect(jsonPath("$.followUpCompleted").value(true))
                .andExpect(jsonPath("$.overdueFollowUp").value(false))
                .andExpect(jsonPath("$.notes", containsString("118k base")));

        // Stale version fails with 409 Conflict
        mockMvc.perform(put("/api/v1/applications/" + adyenAppUuid)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenOwner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(UpdateApplicationRequest.builder()
                                .notes("Stale write")
                                .expectedVersion(currentVersion)
                                .build())))
                .andExpect(status().isConflict());

        // 5. Filtering on GET /applications
        mockMvc.perform(get("/api/v1/applications")
                        .param("status", "TECH_INTERVIEW")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenOwner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].uuid").value(adyenAppUuid));

        mockMvc.perform(get("/api/v1/applications")
                        .param("query", "Walldorf")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenOwner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].uuid").value(sapAppUuid));

        mockMvc.perform(get("/api/v1/applications")
                        .param("hasInterviewOnly", "true")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenOwner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));

        // 6. Strict cross-user isolation (404 on GET/PUT/PATCH/DELETE and empty list)
        mockMvc.perform(get("/api/v1/applications")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenOther))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        mockMvc.perform(get("/api/v1/applications/" + adyenAppUuid)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenOther))
                .andExpect(status().isNotFound());

        mockMvc.perform(patch("/api/v1/applications/" + adyenAppUuid + "/status")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenOther)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newStatus\":\"REJECTED\"}"))
                .andExpect(status().isNotFound());

        mockMvc.perform(delete("/api/v1/applications/" + adyenAppUuid)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenOther))
                .andExpect(status().isNotFound());

        // 7. Deleting tailored CV safely nulls JobApplication.tailoredResume FK
        mockMvc.perform(delete("/api/v1/tailored-documents/resume/" + ownerTailoredCv.getUuid())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenOwner))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/applications/" + adyenAppUuid)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenOwner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tailoredResume").isEmpty())
                .andExpect(jsonPath("$.coverLetter.uuid").value(ownerCoverLetter.getUuid()));

        // 8. Deleting JobApplication safely detaches linked RecruiterInteraction without FK failure
        var trackedAdyen = jobApplicationRepository.findByUuidAndUserId(adyenAppUuid, ownerUser.getId()).orElseThrow();
        Recruiter rec = recruiterRepository.saveAndFlush(Recruiter.builder()
                .user(ownerUser)
                .fullName("Pieter de Vries")
                .companyName("Adyen N.V.")
                .build());
        RecruiterInteraction interaction = recruiterInteractionRepository.saveAndFlush(RecruiterInteraction.builder()
                .recruiter(rec)
                .application(trackedAdyen)
                .channel(CommunicationChannel.LINKEDIN)
                .messageType(RecruiterMessageType.INITIAL_OUTREACH)
                .status(RecruiterMessageStatus.DRAFT)
                .messageBody("Draft outreach note")
                .build());

        mockMvc.perform(delete("/api/v1/applications/" + adyenAppUuid)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenOwner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        mockMvc.perform(get("/api/v1/applications/" + adyenAppUuid)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenOwner))
                .andExpect(status().isNotFound());

        RecruiterInteraction detached = recruiterInteractionRepository.findById(interaction.getId()).orElseThrow();
        assertThat(detached.getApplication()).isNull();
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

