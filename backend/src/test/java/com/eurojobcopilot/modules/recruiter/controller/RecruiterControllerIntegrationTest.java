package com.eurojobcopilot.modules.recruiter.controller;

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
import com.eurojobcopilot.modules.job.entity.Job;
import com.eurojobcopilot.modules.job.entity.JobSponsorStatus;
import com.eurojobcopilot.modules.job.repository.JobRepository;
import com.eurojobcopilot.modules.matching.repository.JobMatchScoreRepository;
import com.eurojobcopilot.modules.recruiter.ai.RawAiOutreachPayload;
import com.eurojobcopilot.modules.recruiter.dto.CreateRecruiterInteractionRequest;
import com.eurojobcopilot.modules.recruiter.dto.CreateRecruiterRequest;
import com.eurojobcopilot.modules.recruiter.dto.GenerateOutreachRequest;
import com.eurojobcopilot.modules.recruiter.dto.RecruiterInteractionAction;
import com.eurojobcopilot.modules.recruiter.dto.UpdateRecruiterInteractionRequest;
import com.eurojobcopilot.modules.recruiter.dto.UpdateRecruiterRequest;
import com.eurojobcopilot.modules.recruiter.entity.CommunicationChannel;
import com.eurojobcopilot.modules.recruiter.entity.RecruiterMessageType;
import com.eurojobcopilot.modules.recruiter.repository.RecruiterInteractionRepository;
import com.eurojobcopilot.modules.recruiter.repository.RecruiterRepository;
import com.eurojobcopilot.modules.sponsorship.entity.Company;
import com.eurojobcopilot.modules.sponsorship.entity.SponsorConfidenceLevel;
import com.eurojobcopilot.modules.sponsorship.repository.CompanyRepository;
import com.eurojobcopilot.modules.user.entity.ProficiencyLevel;
import com.eurojobcopilot.modules.user.entity.RelocationPreference;
import com.eurojobcopilot.modules.user.entity.UserJobPreference;
import com.eurojobcopilot.modules.user.entity.UserProfile;
import com.eurojobcopilot.modules.user.entity.UserSkill;
import com.eurojobcopilot.modules.user.entity.UserWorkExperience;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RecruiterControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private RecruiterInteractionRepository interactionRepository;
    @Autowired
    private RecruiterRepository recruiterRepository;
    @Autowired
    private JobApplicationRepository jobApplicationRepository;
    @Autowired
    private JobMatchScoreRepository jobMatchScoreRepository;
    @Autowired
    private JobRepository jobRepository;
    @Autowired
    private CompanyRepository companyRepository;
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
    private Company adyenCompany;
    private Job adyenJob;
    private JobApplication adyenApplication;

    @BeforeEach
    void setUp() throws Exception {
        cleanDatabase();
        tokenOwner = registerUser("crm.owner@eurojob.eu", "Elena", "Weber");
        tokenEmptyUser = registerUser("crm.empty@eurojob.eu", "Stefan", "Braun");

        ownerUser = userRepository.findByEmailIgnoreCase("crm.owner@eurojob.eu").orElseThrow();

        userProfileRepository.saveAndFlush(UserProfile.builder()
                .user(ownerUser)
                .currentDesignation("Senior Java Backend Engineer")
                .yearsOfExperience(new BigDecimal("6.5"))
                .currentCity("Berlin")
                .currentCountry("Germany")
                .noticePeriodDays(30)
                .workAuthorizationStatus(WorkAuthorizationStatus.NEEDS_SPONSORSHIP)
                .relocationPreference(RelocationPreference.WILLING_TO_RELOCATE)
                .bio("Backend architect focused on distributed European JVM payment platforms.")
                .build());

        userSkillRepository.saveAndFlush(UserSkill.builder()
                .user(ownerUser)
                .skillName("Java 21")
                .normalizedSkill("java 21")
                .yearsExperience(new BigDecimal("6.5"))
                .proficiencyLevel(ProficiencyLevel.EXPERT)
                .primary(true)
                .build());

        userWorkExperienceRepository.saveAndFlush(UserWorkExperience.builder()
                .user(ownerUser)
                .companyName("ClearPay Systems")
                .designation("Senior Java Engineer")
                .startDate(LocalDate.of(2021, 3, 1))
                .current(true)
                .description("Scaled SEPA Instant payment clearing throughput.")
                .build());

        userJobPreferenceRepository.saveAndFlush(UserJobPreference.builder()
                .user(ownerUser)
                .requiresSponsorship(true)
                .requiresRelocation(true)
                .build());

        adyenCompany = companyRepository.saveAndFlush(Company.builder()
                .name("Adyen N.V.")
                .normalizedName("adyen")
                .headquartersCountry("Netherlands")
                .verifiedSponsor(true)
                .sponsorConfidenceLevel(SponsorConfidenceLevel.VERIFIED)
                .industry("FinTech")
                .techStack(List.of("Java 21", "Spring Boot"))
                .build());

        adyenJob = jobRepository.saveAndFlush(Job.builder()
                .canonicalHash(TestEntityFactory.sha256Hex("crm-adyen-1"))
                .company(adyenCompany)
                .companyName("Adyen N.V.")
                .title("Staff Java Payments Architect")
                .normalizedTitle("staff java payments architect")
                .country("Netherlands")
                .city("Amsterdam")
                .location("Amsterdam, Netherlands")
                .workArrangement(WorkArrangement.HYBRID)
                .sponsorStatus(JobSponsorStatus.VERIFIED_SPONSOR)
                .skills(List.of("Java 21", "Spring Boot"))
                .description("Architect European payments clearing microservices.")
                .sourceProvider("ARBEITNOW")
                .originalUrl("https://careers.adyen.com/crm-1")
                .postedAt(Instant.now().minus(3, ChronoUnit.HOURS))
                .active(true)
                .build());

        adyenApplication = jobApplicationRepository.saveAndFlush(JobApplication.builder()
                .user(ownerUser)
                .job(adyenJob)
                .status(ApplicationStatus.TECH_INTERVIEW)
                .appliedDate(Instant.now().minus(4, ChronoUnit.DAYS))
                .nextFollowUpDate(LocalDate.now())
                .nextInterviewDate(Instant.now().plus(2, ChronoUnit.DAYS))
                .build());
    }

    @AfterEach
    void cleanDatabase() {
        reset(aiGatewayService);
        interactionRepository.deleteAllInBatch();
        recruiterRepository.deleteAllInBatch();
        jobApplicationRepository.deleteAllInBatch();
        jobMatchScoreRepository.deleteAllInBatch();
        jobRepository.deleteAllInBatch();
        companyRepository.deleteAllInBatch();
        userWorkExperienceRepository.deleteAllInBatch();
        userSkillRepository.deleteAllInBatch();
        userJobPreferenceRepository.deleteAllInBatch();
        userProfileRepository.deleteAllInBatch();
        refreshTokenRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
    }

    @Test
    void fullRecruiterCrmAndAiOutreachWorkflow_neverAutoSendsAndEnforcesApprovalAndIsolation() throws Exception {
        // 1. Auth guard & URL validation
        mockMvc.perform(get("/api/v1/recruiters"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/v1/recruiters")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenOwner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(CreateRecruiterRequest.builder()
                                .fullName("Sophie van Dijk")
                                .linkedinUrl("javascript:alert(1)")
                                .build())))
                .andExpect(status().isBadRequest());

        // 2. Create & Update Recruiter
        MvcResult createRecRes = mockMvc.perform(post("/api/v1/recruiters")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenOwner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(CreateRecruiterRequest.builder()
                                .fullName("Sophie van Dijk")
                                .roleTitle("Senior Technical Recruiter")
                                .companyId(adyenCompany.getId())
                                .email("sophie.vandijk@adyen.example")
                                .linkedinUrl("https://www.linkedin.com/in/sophievandijk")
                                .phone("+31 20 555 0101")
                                .notes("Leads JVM backend hiring in Amsterdam.")
                                .build())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.companyName").value("Adyen N.V."))
                .andExpect(jsonPath("$.companyVerifiedSponsor").value(true))
                .andReturn();

        Long recruiterId = objectMapper.readTree(createRecRes.getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(put("/api/v1/recruiters/" + recruiterId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenOwner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(UpdateRecruiterRequest.builder()
                                .fullName("Sophie van Dijk")
                                .roleTitle("Lead Talent Acquisition Partner")
                                .companyId(adyenCompany.getId())
                                .companyName("Adyen N.V.")
                                .email("sophie.vandijk@adyen.example")
                                .linkedinUrl("https://www.linkedin.com/in/sophievandijk")
                                .build())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roleTitle").value("Lead Talent Acquisition Partner"));

        // 3. AI Outreach Generation across all 4 required Prompt 40 modes -> ALWAYS unsent DRAFT
        RawAiOutreachPayload aiConnPayload = new RawAiOutreachPayload();
        aiConnPayload.setMessageBody("Hi Sophie, I applied for the Staff Java role at Adyen and would love to connect regarding my SEPA Instant backend work. Best, Elena");
        aiConnPayload.setKeyTalkingPoints(List.of("SEPA Instant Java 21 background", "Amsterdam relocation interest"));
        aiConnPayload.setSuggestedFollowUpDays(5);

        RawAiOutreachPayload aiIntroPayload = new RawAiOutreachPayload();
        aiIntroPayload.setSubjectLine("Introduction: Staff Java Payments Architect at Adyen");
        aiIntroPayload.setMessageBody("Dear Sophie,\n\nWith 6.5 years scaling SEPA Instant systems at ClearPay Systems in Java 21, I recently applied for Adyen's Staff Java role in Amsterdam.\n\nBest regards,\nElena Weber");
        aiIntroPayload.setKeyTalkingPoints(List.of("ClearPay SEPA scaling", "Verified Java 21 depth"));
        aiIntroPayload.setSuggestedFollowUpDays(5);

        RawAiOutreachPayload aiFollowUpPayload = new RawAiOutreachPayload();
        aiFollowUpPayload.setSubjectLine("Follow-up: Staff Java Payments Architect application");
        aiFollowUpPayload.setMessageBody("Hi Sophie,\n\nChecking in politely on next steps for my Staff Java Payments Architect application after this week's technical round.\n\nWarm regards,\nElena Weber");
        aiFollowUpPayload.setKeyTalkingPoints(List.of("Technical interview milestone"));
        aiFollowUpPayload.setSuggestedFollowUpDays(4);

        RawAiOutreachPayload aiThanksPayload = new RawAiOutreachPayload();
        aiThanksPayload.setSubjectLine("Thank you — Staff Java Payments Architect interview");
        aiThanksPayload.setMessageBody("Dear Sophie,\n\nThank you for coordinating today's architecture conversation with Adyen's clearing team. Discussing idempotent clearing queues reaffirmed my excitement to relocate to Amsterdam.\n\nSincerely,\nElena Weber");
        aiThanksPayload.setKeyTalkingPoints(List.of("Thank-you & architecture excitement"));
        aiThanksPayload.setSuggestedFollowUpDays(3);

        when(aiGatewayService.executeStructuredJsonPrompt(any(AiJsonPromptRequest.class), eq(RawAiOutreachPayload.class)))
                .thenReturn(
                        wrap("GEMINI", "gemini-2.5-flash", aiConnPayload),
                        wrap("GEMINI", "gemini-2.5-flash", aiIntroPayload),
                        wrap("GEMINI", "gemini-2.5-flash", aiFollowUpPayload),
                        wrap("GEMINI", "gemini-2.5-flash", aiThanksPayload)
                );

        // 3a. LINKEDIN_CONNECTION
        MvcResult connRes = mockMvc.perform(post("/api/v1/recruiters/" + recruiterId + "/outreach/generate")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenOwner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(GenerateOutreachRequest.builder()
                                .channel(CommunicationChannel.LINKEDIN)
                                .messageType(RecruiterMessageType.LINKEDIN_CONNECTION)
                                .applicationUuid(adyenApplication.getUuid())
                                .followUpReminderDate(LocalDate.now().plusDays(5))
                                .build())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.requiresManualApproval").value(true))
                .andExpect(jsonPath("$.autoSent").value(false))
                .andExpect(jsonPath("$.interaction.status").value("DRAFT"))
                .andExpect(jsonPath("$.interaction.sentAt").isEmpty())
                .andExpect(jsonPath("$.interaction.aiGenerated").value(true))
                .andExpect(jsonPath("$.interaction.applicationUuid").value(adyenApplication.getUuid()))
                .andReturn();

        Long connInteractionId = objectMapper.readTree(connRes.getResponse().getContentAsString())
                .get("interaction").get("id").asLong();

        // 3b. INITIAL_OUTREACH
        mockMvc.perform(post("/api/v1/recruiters/" + recruiterId + "/outreach/generate")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenOwner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(GenerateOutreachRequest.builder()
                                .channel(CommunicationChannel.EMAIL)
                                .messageType(RecruiterMessageType.INITIAL_OUTREACH)
                                .jobId(adyenJob.getId())
                                .build())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.interaction.status").value("DRAFT"))
                .andExpect(jsonPath("$.interaction.sentAt").isEmpty())
                .andExpect(jsonPath("$.interaction.subjectLine").isNotEmpty());

        // 3c. FOLLOW_UP
        mockMvc.perform(post("/api/v1/recruiters/" + recruiterId + "/outreach/generate")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenOwner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(GenerateOutreachRequest.builder()
                                .channel(CommunicationChannel.EMAIL)
                                .messageType(RecruiterMessageType.FOLLOW_UP)
                                .applicationUuid(adyenApplication.getUuid())
                                .followUpReminderDate(LocalDate.now().minusDays(2))
                                .build())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.interaction.overdueReminder").value(true))
                .andExpect(jsonPath("$.interaction.status").value("DRAFT"));

        // 3d. POST_INTERVIEW_THANKS
        mockMvc.perform(post("/api/v1/recruiters/" + recruiterId + "/outreach/generate")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenOwner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(GenerateOutreachRequest.builder()
                                .channel(CommunicationChannel.EMAIL)
                                .messageType(RecruiterMessageType.POST_INTERVIEW_THANKS)
                                .applicationUuid(adyenApplication.getUuid())
                                .personalContext("Discussed idempotent clearing queues with the principal engineer.")
                                .build())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.interaction.status").value("DRAFT"))
                .andExpect(jsonPath("$.interaction.sentAt").isEmpty());

        ArgumentCaptor<AiJsonPromptRequest> promptCaptor = ArgumentCaptor.forClass(AiJsonPromptRequest.class);
        verify(aiGatewayService, times(4)).executeStructuredJsonPrompt(promptCaptor.capture(), eq(RawAiOutreachPayload.class));
        assertThat(promptCaptor.getAllValues().getLast().userPrompt())
                .contains("POST_INTERVIEW_THANKS", "idempotent clearing queues", "Sophie van Dijk", "Adyen N.V.");

        // 4. Edit -> Approve -> Explicit Mark Sent safety checks
        // 4a. Save user edit without changing sentAt
        mockMvc.perform(put("/api/v1/recruiters/" + recruiterId + "/interactions/" + connInteractionId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenOwner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(UpdateRecruiterInteractionRequest.builder()
                                .messageBody("Hi Sophie, connecting after applying for Staff Java Architect at Adyen. Would welcome staying in touch! — Elena")
                                .action(RecruiterInteractionAction.SAVE_DRAFT)
                                .build())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.editedByUser").value(true))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.sentAt").isEmpty());

        // 4b. APPROVE transitions status to APPROVED without setting sentAt
        mockMvc.perform(put("/api/v1/recruiters/" + recruiterId + "/interactions/" + connInteractionId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenOwner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(UpdateRecruiterInteractionRequest.builder()
                                .action(RecruiterInteractionAction.APPROVE)
                                .build())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.approvedAt").isNotEmpty())
                .andExpect(jsonPath("$.sentAt").isEmpty());

        // 4c. MARK_SENT without explicitApprovalConfirmed=true is blocked (400)
        mockMvc.perform(put("/api/v1/recruiters/" + recruiterId + "/interactions/" + connInteractionId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenOwner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(UpdateRecruiterInteractionRequest.builder()
                                .action(RecruiterInteractionAction.MARK_SENT)
                                .explicitApprovalConfirmed(false)
                                .build())))
                .andExpect(status().isBadRequest());

        // 4d. MARK_SENT with explicitApprovalConfirmed=true succeeds
        mockMvc.perform(put("/api/v1/recruiters/" + recruiterId + "/interactions/" + connInteractionId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenOwner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(UpdateRecruiterInteractionRequest.builder()
                                .action(RecruiterInteractionAction.MARK_SENT)
                                .explicitApprovalConfirmed(true)
                                .reminderCompleted(true)
                                .build())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SENT"))
                .andExpect(jsonPath("$.sentAt").isNotEmpty())
                .andExpect(jsonPath("$.reminderCompleted").value(true));

        // 5. Manual interaction logging check
        mockMvc.perform(post("/api/v1/recruiters/" + recruiterId + "/interactions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenOwner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(CreateRecruiterInteractionRequest.builder()
                                .channel(CommunicationChannel.PHONE)
                                .messageType(RecruiterMessageType.FOLLOW_UP)
                                .messageBody("Spoke briefly about Amsterdam relocation package timeline.")
                                .markAsSent(true)
                                .explicitApprovalConfirmed(false)
                                .build())))
                .andExpect(status().isBadRequest());

        MvcResult manualRes = mockMvc.perform(post("/api/v1/recruiters/" + recruiterId + "/interactions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenOwner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(CreateRecruiterInteractionRequest.builder()
                                .applicationUuid(adyenApplication.getUuid())
                                .channel(CommunicationChannel.PHONE)
                                .messageType(RecruiterMessageType.FOLLOW_UP)
                                .messageBody("15-minute intro call covering Amsterdam relocation timeline.")
                                .markAsSent(true)
                                .explicitApprovalConfirmed(true)
                                .build())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("SENT"))
                .andExpect(jsonPath("$.aiGenerated").value(false))
                .andReturn();
        Long manualInteractionId = objectMapper.readTree(manualRes.getResponse().getContentAsString()).get("id").asLong();

        // 6. Unified Reminders Endpoint aggregates both recruiter interaction & application reminders
        mockMvc.perform(get("/api/v1/recruiters/reminders")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenOwner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.overdueCount").value(1))
                .andExpect(jsonPath("$.dueTodayCount").value(1))
                .andExpect(jsonPath("$.totalPendingCount").value(2))
                .andExpect(jsonPath("$.items[0].urgency").value("OVERDUE"))
                .andExpect(jsonPath("$.items[1].urgency").value("DUE_TODAY"));

        // 7. Cross-user isolation checks
        mockMvc.perform(get("/api/v1/recruiters/" + recruiterId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenEmptyUser))
                .andExpect(status().isNotFound());

        mockMvc.perform(put("/api/v1/recruiters/" + recruiterId + "/interactions/" + connInteractionId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenEmptyUser)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"SAVE_DRAFT\",\"messageBody\":\"tamper\"}"))
                .andExpect(status().isNotFound());

        // Candidate with empty profile gets 400 when generating AI outreach on their own recruiter
        MvcResult otherRec = mockMvc.perform(post("/api/v1/recruiters")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenEmptyUser)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"Other Recruiter\",\"companyName\":\"SAP SE\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        Long emptyRecId = objectMapper.readTree(otherRec.getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(post("/api/v1/recruiters/" + emptyRecId + "/outreach/generate")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenEmptyUser)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"channel\":\"EMAIL\",\"messageType\":\"INITIAL_OUTREACH\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("candidate profile")));

        // 8. Delete interaction & delete recruiter
        mockMvc.perform(delete("/api/v1/recruiters/" + recruiterId + "/interactions/" + manualInteractionId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenOwner))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/recruiters")
                        .param("query", "Sophie")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenOwner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].interactionsCount").value(4));

        mockMvc.perform(delete("/api/v1/recruiters/" + recruiterId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenOwner))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/recruiters/" + recruiterId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenOwner))
                .andExpect(status().isNotFound());
    }

    private AiStructuredResponse<RawAiOutreachPayload> wrap(
            String provider,
            String model,
            RawAiOutreachPayload payload
    ) throws Exception {
        return new AiStructuredResponse<>(
                provider,
                model,
                payload,
                objectMapper.writeValueAsString(payload),
                280,
                110
        );
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

