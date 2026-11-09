package com.eurojobcopilot.modules.job.intelligence.controller;

import com.eurojobcopilot.core.enums.WorkArrangement;
import com.eurojobcopilot.modules.auth.dto.RegisterRequest;
import com.eurojobcopilot.modules.auth.repository.RefreshTokenRepository;
import com.eurojobcopilot.modules.auth.repository.UserRepository;
import com.eurojobcopilot.modules.job.entity.Job;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class JobAiAnalysisUnavailableIntegrationTest {

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

    private String token;

    @BeforeEach
    void setUp() throws Exception {
        cleanDatabase();
        RegisterRequest register = RegisterRequest.builder()
                .email("ai.unavailable@eurojob.eu")
                .password("SecureP@ssw0rd2026!")
                .firstName("Nina")
                .lastName("Hoffmann")
                .build();

        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(register)))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        token = json.get("accessToken").asText();
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
    void analyzeJd_enforcesAuthValidationAndReturns503WhenAiDisabledInTestProfile() throws Exception {
        Job job = jobRepository.saveAndFlush(Job.builder()
                .canonicalHash(TestEntityFactory.sha256Hex("ai-unavailable-job"))
                .companyName("Test GmbH")
                .title("Senior Java Engineer")
                .normalizedTitle("senior java engineer")
                .country("Germany")
                .city("Berlin")
                .location("Berlin, Germany")
                .workArrangement(WorkArrangement.HYBRID)
                .description("Detailed backend engineering posting requiring Java 21 and Spring Boot.")
                .sourceProvider("ARBEITNOW")
                .originalUrl("https://jobs.example.eu/1")
                .postedAt(Instant.now().minus(2, ChronoUnit.HOURS))
                .active(true)
                .build());

        mockMvc.perform(post("/api/v1/jobs/" + job.getId() + "/intelligence/analyze-jd"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));

        mockMvc.perform(post("/api/v1/jobs/0/intelligence/analyze-jd")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        mockMvc.perform(post("/api/v1/jobs/999999/intelligence/analyze-jd")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));

        // Real Spring context with app.ai.enabled=false must fail explicitly with 503 rather than fabricating output
        mockMvc.perform(post("/api/v1/jobs/" + job.getId() + "/intelligence/analyze-jd")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value(503));

        mockMvc.perform(get("/api/v1/jobs/" + job.getId() + "/intelligence/analyze-jd")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));

        mockMvc.perform(get("/api/v1/jobs/" + job.getId() + "/intelligence/analyze-jd/history")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }
}

