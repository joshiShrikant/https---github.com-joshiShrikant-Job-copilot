package com.eurojobcopilot.modules.interview.controller;

import com.eurojobcopilot.modules.ai.client.AiJsonPromptRequest;
import com.eurojobcopilot.modules.ai.service.AiGatewayService;
import com.eurojobcopilot.modules.ai.service.AiStructuredResponse;
import com.eurojobcopilot.modules.auth.dto.RegisterRequest;
import com.eurojobcopilot.modules.auth.entity.User;
import com.eurojobcopilot.modules.auth.repository.RefreshTokenRepository;
import com.eurojobcopilot.modules.auth.repository.UserRepository;
import com.eurojobcopilot.modules.interview.ai.RawAiAnswerEvaluationPayload;
import com.eurojobcopilot.modules.interview.ai.RawAiInterviewSessionPayload;
import com.eurojobcopilot.modules.interview.ai.RawAiInterviewSessionPayload.RawQuestion;
import com.eurojobcopilot.modules.interview.ai.RawAiInterviewSessionPayload.RawTopic;
import com.eurojobcopilot.modules.interview.dto.CreateInterviewPrepSessionRequest;
import com.eurojobcopilot.modules.interview.dto.SubmitInterviewAnswerRequest;
import com.eurojobcopilot.modules.interview.entity.QuestionCategory;
import com.eurojobcopilot.modules.interview.entity.QuestionDifficulty;
import com.eurojobcopilot.modules.interview.repository.InterviewPrepSessionRepository;
import com.eurojobcopilot.modules.interview.repository.InterviewQuestionRepository;
import com.eurojobcopilot.modules.job.entity.Job;
import com.eurojobcopilot.modules.job.repository.JobRepository;
import com.eurojobcopilot.modules.matching.repository.JobMatchScoreRepository;
import com.eurojobcopilot.modules.sponsorship.entity.Company;
import com.eurojobcopilot.modules.sponsorship.repository.CompanyRepository;
import com.eurojobcopilot.modules.user.entity.UserProfile;
import com.eurojobcopilot.modules.user.repository.UserProfileRepository;
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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class InterviewPrepControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private InterviewQuestionRepository questionRepository;
    @Autowired
    private InterviewPrepSessionRepository sessionRepository;
    @Autowired
    private UserProfileRepository profileRepository;
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

    @MockitoBean
    private AiGatewayService aiGatewayService;

    private String tokenOwner;
    private String tokenOther;
    private Job targetJob;

    @BeforeEach
    void setUp() throws Exception {
        cleanDatabase();
        tokenOwner = registerUser("prep.owner@eurojob.eu", "Lukas", "Mueller");
        tokenOther = registerUser("prep.other@eurojob.eu", "Maya", "Keller");

        User owner = userRepository.findByEmailIgnoreCase("prep.owner@eurojob.eu").orElseThrow();
        profileRepository.saveAndFlush(UserProfile.builder()
                .user(owner)
                .currentDesignation("Senior Fullstack Engineer")
                .yearsOfExperience(new BigDecimal("6.0"))
                .currentCountry("India")
                .build());

        Company company = companyRepository.saveAndFlush(
                TestEntityFactory.createCompany("Klarna Prep SE", "Sweden", true));
        targetJob = jobRepository.saveAndFlush(
                TestEntityFactory.createJob(company, "Senior Angular & Java Engineer", "Sweden", "prep-1"));
    }

    @AfterEach
    void cleanDatabase() {
        reset(aiGatewayService);
        questionRepository.deleteAllInBatch();
        sessionRepository.deleteAllInBatch();
        jobMatchScoreRepository.deleteAllInBatch();
        profileRepository.deleteAllInBatch();
        jobRepository.deleteAllInBatch();
        companyRepository.deleteAllInBatch();
        refreshTokenRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
    }

    @Test
    void interviewPrepLifecycle_generatePracticeGradeHistoryDeleteAndIsolation() throws Exception {
        when(aiGatewayService.executeStructuredJsonPrompt(any(), eq(RawAiInterviewSessionPayload.class)))
                .thenReturn(new AiStructuredResponse<>(
                        "GEMINI",
                        "gemini-2.5-flash",
                        new RawAiInterviewSessionPayload(
                                "Klarna Senior Angular & Java Technical Round",
                                List.of(new RawTopic(
                                        "Checkout State Integrity",
                                        "FinTech payment state machines require predictable UI transitions.",
                                        "Company directory FinTech profile & job stack"
                                )),
                                List.of(
                                        new RawQuestion(
                                                "ANGULAR",
                                                "SENIOR",
                                                "When should you prefer Angular Signals vs RxJS Observables in complex state stores?",
                                                "Use Signals for synchronous component state and RxJS streams for async event orchestration bridged via toSignal.",
                                                List.of("Angular 20", "Signals")
                                        ),
                                        new RawQuestion(
                                                "JAVA",
                                                "SENIOR",
                                                "How do you design idempotent Spring Boot webhooks for payment retries?",
                                                "Persist unique idempotency keys under unique constraints inside repeatable transactional boundaries.",
                                                List.of("Spring Boot", "Idempotency")
                                        ),
                                        new RawQuestion(
                                                "BEHAVIORAL",
                                                "SENIOR",
                                                "Describe resolving a high-severity production latency spike across frontend and backend.",
                                                "Outline concrete telemetry diagnosis, mitigation, and postmortem prevention using STAR.",
                                                List.of("STAR", "Ownership")
                                        )
                                ),
                                "Emphasize end-to-end reliability across Angular UI and Java microservices.",
                                List.of("Practice concise tradeoff articulation")
                        ),
                        "{}",
                        150,
                        120
                ));

        when(aiGatewayService.executeStructuredJsonPrompt(any(), eq(RawAiAnswerEvaluationPayload.class)))
                .thenReturn(new AiStructuredResponse<>(
                        "GEMINI",
                        "gemini-2.5-flash",
                        new RawAiAnswerEvaluationPayload(
                                new BigDecimal("87.50"),
                                "Strong clear separation of synchronous UI Signals and async HTTP RxJS pipelines.",
                                List.of("Accurate Signals vs RxJS boundary", "Mentioned toSignal interoperability"),
                                List.of("Could highlight cleanup with DestroyRef on custom subscriptions"),
                                "Strong start at 87.5% — continue with Java idempotency scenario.",
                                List.of("Review DestroyRef lifecycle cleanup", "Prepare concrete webhook retry metrics")
                        ),
                        "{}",
                        110,
                        90
                ));

        CreateInterviewPrepSessionRequest createReq = CreateInterviewPrepSessionRequest.builder()
                .jobId(targetJob.getId())
                .categories(List.of(QuestionCategory.ANGULAR, QuestionCategory.JAVA, QuestionCategory.BEHAVIORAL))
                .difficulty(QuestionDifficulty.SENIOR)
                .questionCount(3)
                .focusNotes("Signals + idempotent APIs")
                .build();

        MvcResult createdRes = mockMvc.perform(post("/api/v1/interview-prep/sessions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenOwner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sessionTitle").value("Klarna Senior Angular & Java Technical Round"))
                .andExpect(jsonPath("$.questions.length()").value(3))
                .andExpect(jsonPath("$.companyPrepTopics.length()").value(1))
                .andExpect(jsonPath("$.answeredQuestions").value(0))
                .andReturn();

        ArgumentCaptor<AiJsonPromptRequest> genCaptor = ArgumentCaptor.forClass(AiJsonPromptRequest.class);
        verify(aiGatewayService).executeStructuredJsonPrompt(genCaptor.capture(), eq(RawAiInterviewSessionPayload.class));
        assertThat(genCaptor.getValue().userPrompt()).contains("Klarna Prep SE", "Signals + idempotent APIs");

        JsonNode createdJson = objectMapper.readTree(createdRes.getResponse().getContentAsString());
        long sessionId = createdJson.get("id").asLong();
        long q1Id = createdJson.get("questions").get(0).get("id").asLong();

        // Submit practice answer -> updates question score, feedback & session average score
        SubmitInterviewAnswerRequest answerReq = SubmitInterviewAnswerRequest.builder()
                .questionId(q1Id)
                .userAnswer("Use Angular Signals for synchronous derived view state and RxJS switchMap/toSignal for HTTP orchestration.")
                .build();

        mockMvc.perform(post("/api/v1/interview-prep/sessions/" + sessionId + "/answers")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenOwner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(answerReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answeredQuestions").value(1))
                .andExpect(jsonPath("$.score").value(87.5))
                .andExpect(jsonPath("$.questions[0].score").value(87.5))
                .andExpect(jsonPath("$.questions[0].strongPoints.length()").value(2))
                .andExpect(jsonPath("$.questions[0].missingElements.length()").value(1));

        // Performance history reflects graded question stats & improvement recommendations
        mockMvc.perform(get("/api/v1/interview-prep/history")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenOwner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalSessions").value(1))
                .andExpect(jsonPath("$.totalQuestions").value(3))
                .andExpect(jsonPath("$.totalAnsweredQuestions").value(1))
                .andExpect(jsonPath("$.averageScore").value(87.5))
                .andExpect(jsonPath("$.recentScoreTrend.length()").value(1));

        // Tenant isolation: other user cannot view or delete owner's session
        mockMvc.perform(get("/api/v1/interview-prep/sessions/" + sessionId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenOther))
                .andExpect(status().isNotFound());

        mockMvc.perform(delete("/api/v1/interview-prep/sessions/" + sessionId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenOther))
                .andExpect(status().isNotFound());

        // Owner deletes cleanly
        mockMvc.perform(delete("/api/v1/interview-prep/sessions/" + sessionId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenOwner))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/interview-prep/sessions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenOwner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    private String registerUser(String email, String firstName, String lastName) throws Exception {
        RegisterRequest reg = RegisterRequest.builder()
                .email(email)
                .password("SecureP@ssw0rd2026!")
                .firstName(firstName)
                .lastName(lastName)
                .build();
        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reg)))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        return json.get("accessToken").asText();
    }
}

