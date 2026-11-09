package com.eurojobcopilot.modules.job.intelligence.controller;

import com.eurojobcopilot.core.enums.WorkArrangement;
import com.eurojobcopilot.modules.ai.client.AiJsonPromptRequest;
import com.eurojobcopilot.modules.ai.service.AiGatewayService;
import com.eurojobcopilot.modules.ai.service.AiStructuredResponse;
import com.eurojobcopilot.modules.auth.dto.RegisterRequest;
import com.eurojobcopilot.modules.auth.entity.User;
import com.eurojobcopilot.modules.auth.repository.RefreshTokenRepository;
import com.eurojobcopilot.modules.auth.repository.UserRepository;
import com.eurojobcopilot.modules.job.entity.Job;
import com.eurojobcopilot.modules.job.entity.JobAiAnalysis;
import com.eurojobcopilot.modules.job.entity.JobAiAnalysisHistory;
import com.eurojobcopilot.modules.job.intelligence.analyzer.RawAiJdExtractionPayload;
import com.eurojobcopilot.modules.job.repository.JobAiAnalysisHistoryRepository;
import com.eurojobcopilot.modules.job.repository.JobAiAnalysisRepository;
import com.eurojobcopilot.modules.job.repository.JobRepository;
import com.eurojobcopilot.modules.matching.repository.JobMatchScoreRepository;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

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
class JobAiAnalysisControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JobAiAnalysisHistoryRepository jobAiAnalysisHistoryRepository;

    @Autowired
    private JobAiAnalysisRepository jobAiAnalysisRepository;

    @Autowired
    private JobMatchScoreRepository jobMatchScoreRepository;

    @Autowired
    private JobRepository jobRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private UserRepository userRepository;

    @MockitoBean
    private AiGatewayService aiGatewayService;

    private String token;
    private User testUser;
    private Job adyenJob;

    @BeforeEach
    void setUp() throws Exception {
        cleanDatabase();
        RegisterRequest register = RegisterRequest.builder()
                .email("ai.analyzer@eurojob.eu")
                .password("SecureP@ssw0rd2026!")
                .firstName("Lukas")
                .lastName("Vermeer")
                .build();

        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(register)))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        token = json.get("accessToken").asText();
        testUser = userRepository.findByEmailIgnoreCase("ai.analyzer@eurojob.eu").orElseThrow();

        adyenJob = jobRepository.saveAndFlush(Job.builder()
                .canonicalHash(TestEntityFactory.sha256Hex("ai-controller-adyen"))
                .companyName("Adyen N.V.")
                .title("Senior Java & Spring Boot Engineer")
                .normalizedTitle("senior java spring boot engineer")
                .country("Netherlands")
                .city("Amsterdam")
                .location("Amsterdam, Netherlands")
                .workArrangement(WorkArrangement.HYBRID)
                .description("""
                        <p>Adyen is hiring a Senior Java &amp; Spring Boot Engineer in Amsterdam (Hybrid).</p>
                        <p>Requires 5+ years of distributed systems experience, Java 21, Spring Boot, PostgreSQL.
                        Nice to have: Kafka, Kubernetes. Salary: EUR 90,000 - 115,000 per year.</p>
                        <p>Full EU Blue Card / IND visa sponsorship and international relocation package provided.
                        Fluent English required; Dutch is a plus.</p>
                        """)
                .sourceProvider("ARBEITNOW")
                .originalUrl("https://careers.adyen.com/jobs/123")
                .postedAt(Instant.now().minus(1, ChronoUnit.HOURS))
                .active(true)
                .build());
    }

    @AfterEach
    void cleanDatabase() {
        jobAiAnalysisHistoryRepository.deleteAllInBatch();
        jobAiAnalysisRepository.deleteAllInBatch();
        jobMatchScoreRepository.deleteAllInBatch();
        jobRepository.deleteAllInBatch();
        refreshTokenRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
    }

    @Test
    void analyzeJd_validatesStoresVersionedHistoryRefreshesCacheAndRejectsInvalidAiOutput() throws Exception {
        RawAiJdExtractionPayload payloadV1 = RawAiJdExtractionPayload.builder()
                .jobTitle("Senior Java & Spring Boot Engineer")
                .company("Adyen N.V.")
                .country("nl")
                .location("Amsterdam, Netherlands")
                .requiredExperienceYears(new BigDecimal("5.0"))
                .requiredExperienceLevel("SENIOR")
                .requiredExperienceSummary("5+ years building high-throughput Java backend systems")
                .requiredSkills(List.of("Java 21", "Spring Boot", "PostgreSQL"))
                .preferredSkills(List.of("Kafka", "Kubernetes"))
                .responsibilities(List.of(
                        "Architect low-latency payment processing services",
                        "Collaborate with cross-functional FinTech platform squads"
                ))
                .benefits(List.of("25 vacation days", "Commuter allowance"))
                .languageRequirements(List.of("English (Fluent)", "Dutch (Nice to have)"))
                .salaryMin(new BigDecimal("90000.00"))
                .salaryMax(new BigDecimal("115000.00"))
                .salaryCurrency("EUR")
                .salaryPeriod("YEARLY")
                .workArrangement("HYBRID")
                .employmentType("FULL_TIME")
                .sponsorshipOffered(true)
                .visaSponsorshipAnalysis("IND Recognized Sponsor confirming EU Blue Card and HSM sponsorship.")
                .relocationAssistanceOffered(true)
                .relocationAnalysis("Full international relocation flight and temporary housing included.")
                .summary("Senior backend Java engineering role on Adyen's core payment platform.")
                .build();

        String jsonV1 = objectMapper.writeValueAsString(payloadV1);
        when(aiGatewayService.executeStructuredJsonPrompt(any(AiJsonPromptRequest.class), eq(RawAiJdExtractionPayload.class)))
                .thenReturn(new AiStructuredResponse<>("GEMINI", "gemini-2.5-flash", payloadV1, jsonV1, 510, 240));

        // 1. First analysis run persists v1 in cache + v1 in history
        mockMvc.perform(post("/api/v1/jobs/" + adyenJob.getId() + "/intelligence/analyze-jd")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobId").value(adyenJob.getId()))
                .andExpect(jsonPath("$.analysisVersion").value(1))
                .andExpect(jsonPath("$.extractedTitle").value("Senior Java & Spring Boot Engineer"))
                .andExpect(jsonPath("$.extractedCompany").value("Adyen N.V."))
                .andExpect(jsonPath("$.extractedCountry").value("Netherlands"))
                .andExpect(jsonPath("$.extractedLocation").value("Amsterdam, Netherlands"))
                .andExpect(jsonPath("$.requiredExperienceYears").value(5.0))
                .andExpect(jsonPath("$.requiredExperienceLevel").value("SENIOR"))
                .andExpect(jsonPath("$.requiredExperienceSummary").value("5+ years building high-throughput Java backend systems"))
                .andExpect(jsonPath("$.requiredSkills.length()").value(3))
                .andExpect(jsonPath("$.niceToHaveSkills.length()").value(2))
                .andExpect(jsonPath("$.responsibilities.length()").value(2))
                .andExpect(jsonPath("$.languageRequirements.length()").value(2))
                .andExpect(jsonPath("$.salaryMin").value(90000.0))
                .andExpect(jsonPath("$.salaryMax").value(115000.0))
                .andExpect(jsonPath("$.salaryCurrency").value("EUR"))
                .andExpect(jsonPath("$.salaryPeriod").value("YEARLY"))
                .andExpect(jsonPath("$.workArrangement").value("HYBRID"))
                .andExpect(jsonPath("$.employmentType").value("FULL_TIME"))
                .andExpect(jsonPath("$.sponsorshipOffered").value(true))
                .andExpect(jsonPath("$.relocationAssistanceOffered").value(true))
                .andExpect(jsonPath("$.aiProvider").value("GEMINI"))
                .andExpect(jsonPath("$.aiModelUsed").value("gemini-2.5-flash"))
                .andExpect(jsonPath("$.analyzedAt").isNotEmpty());

        // 2. GET /api/v1/jobs/{id} & GET .../analyze-jd both reflect cached v1
        mockMvc.perform(get("/api/v1/jobs/" + adyenJob.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.aiAnalysis.analysisVersion").value(1))
                .andExpect(jsonPath("$.aiAnalysis.extractedTitle").value("Senior Java & Spring Boot Engineer"))
                .andExpect(jsonPath("$.aiAnalysis.salaryCurrency").value("EUR"))
                .andExpect(jsonPath("$.aiAnalysis.sponsorshipOffered").value(true))
                .andExpect(jsonPath("$.aiAnalysis.relocationAssistanceOffered").value(true));

        mockMvc.perform(get("/api/v1/jobs/" + adyenJob.getId() + "/intelligence/analyze-jd")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.analysisVersion").value(1));

        // 3. Re-analysis creates v2 with different provider/model & appends to immutable history
        RawAiJdExtractionPayload payloadV2 = RawAiJdExtractionPayload.builder()
                .jobTitle("Senior Java Platform Engineer")
                .company("Adyen N.V.")
                .country("Netherlands")
                .location("Amsterdam")
                .requiredExperienceYears(new BigDecimal("6.0"))
                .requiredExperienceLevel("SENIOR")
                .requiredSkills(List.of("Java 21", "Spring Boot", "PostgreSQL", "Kafka"))
                .preferredSkills(List.of("Kubernetes"))
                .responsibilities(List.of("Lead resilient payment ledger architecture"))
                .languageRequirements(List.of("English (C1)"))
                .salaryMin(new BigDecimal("95000.00"))
                .salaryMax(new BigDecimal("120000.00"))
                .salaryCurrency("EUR")
                .salaryPeriod("YEARLY")
                .workArrangement("HYBRID")
                .employmentType("FULL_TIME")
                .sponsorshipOffered(true)
                .visaSponsorshipAnalysis("Visa sponsorship explicitly offered.")
                .relocationAssistanceOffered(true)
                .relocationAnalysis("Full relocation budget available.")
                .summary("Updated v2 role analysis prioritizing Kafka ledger expertise.")
                .build();

        String jsonV2 = objectMapper.writeValueAsString(payloadV2);
        when(aiGatewayService.executeStructuredJsonPrompt(any(AiJsonPromptRequest.class), eq(RawAiJdExtractionPayload.class)))
                .thenReturn(new AiStructuredResponse<>("OPENAI", "gpt-4o-mini", payloadV2, jsonV2, 480, 210));

        mockMvc.perform(post("/api/v1/jobs/" + adyenJob.getId() + "/intelligence/analyze-jd")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.analysisVersion").value(2))
                .andExpect(jsonPath("$.aiProvider").value("OPENAI"))
                .andExpect(jsonPath("$.aiModelUsed").value("gpt-4o-mini"))
                .andExpect(jsonPath("$.salaryMax").value(120000.0));

        // 4. forceRefresh=false returns cached v2 without invoking gateway again
        mockMvc.perform(post("/api/v1/jobs/" + adyenJob.getId() + "/intelligence/analyze-jd")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"forceRefresh\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.analysisVersion").value(2))
                .andExpect(jsonPath("$.aiProvider").value("OPENAI"));

        verify(aiGatewayService, times(2)).executeStructuredJsonPrompt(any(), eq(RawAiJdExtractionPayload.class));

        // 5. History endpoint returns [v2, v1] descending
        mockMvc.perform(get("/api/v1/jobs/" + adyenJob.getId() + "/intelligence/analyze-jd/history")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].analysisVersion").value(2))
                .andExpect(jsonPath("$[0].aiProvider").value("OPENAI"))
                .andExpect(jsonPath("$[1].analysisVersion").value(1))
                .andExpect(jsonPath("$[1].aiProvider").value("GEMINI"));

        // 6. Invalid AI payload (salaryMin > salaryMax) returns 502 and leaves v2 cache & 2-entry history intact
        RawAiJdExtractionPayload invalidPayload = RawAiJdExtractionPayload.builder()
                .jobTitle("Corrupted AI Extraction")
                .salaryMin(new BigDecimal("150000"))
                .salaryMax(new BigDecimal("80000"))
                .summary("Inverted compensation range")
                .build();

        when(aiGatewayService.executeStructuredJsonPrompt(any(AiJsonPromptRequest.class), eq(RawAiJdExtractionPayload.class)))
                .thenReturn(new AiStructuredResponse<>(
                        "OPENAI",
                        "gpt-4o-mini",
                        invalidPayload,
                        objectMapper.writeValueAsString(invalidPayload),
                        100,
                        40
                ));

        mockMvc.perform(post("/api/v1/jobs/" + adyenJob.getId() + "/intelligence/analyze-jd")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.status").value(502));

        JobAiAnalysis persistedCache = jobAiAnalysisRepository.findByJobId(adyenJob.getId()).orElseThrow();
        assertThat(persistedCache.getAnalysisVersion()).isEqualTo(2);
        assertThat(persistedCache.getExtractedTitle()).isEqualTo("Senior Java Platform Engineer");

        List<JobAiAnalysisHistory> persistedHistory =
                jobAiAnalysisHistoryRepository.findAllByJobIdOrderByVersionNumberDesc(adyenJob.getId());
        assertThat(persistedHistory).hasSize(2);
        assertThat(persistedHistory.getFirst().getTriggeredByUser().getId()).isEqualTo(testUser.getId());
    }
}

