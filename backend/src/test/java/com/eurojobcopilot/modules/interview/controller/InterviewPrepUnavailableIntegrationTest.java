package com.eurojobcopilot.modules.interview.controller;

import com.eurojobcopilot.modules.auth.dto.RegisterRequest;
import com.eurojobcopilot.modules.auth.repository.RefreshTokenRepository;
import com.eurojobcopilot.modules.auth.repository.UserRepository;
import com.eurojobcopilot.modules.interview.dto.CreateInterviewPrepSessionRequest;
import com.eurojobcopilot.modules.job.entity.Job;
import com.eurojobcopilot.modules.job.repository.JobRepository;
import com.eurojobcopilot.modules.matching.repository.JobMatchScoreRepository;
import com.eurojobcopilot.modules.sponsorship.entity.Company;
import com.eurojobcopilot.modules.sponsorship.repository.CompanyRepository;
import com.eurojobcopilot.support.TestEntityFactory;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class InterviewPrepUnavailableIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
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

    private String token;
    private Job job;

    @BeforeEach
    void setUp() throws Exception {
        cleanDatabase();
        RegisterRequest reg = RegisterRequest.builder()
                .email("prep.unavail@eurojob.eu")
                .password("SecureP@ssw0rd2026!")
                .firstName("Lukas")
                .lastName("Mueller")
                .build();
        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reg)))
                .andExpect(status().isCreated())
                .andReturn();
        token = objectMapper.readTree(result.getResponse().getContentAsString()).get("accessToken").asText();

        Company company = companyRepository.saveAndFlush(
                TestEntityFactory.createCompany("Zalando Unavail", "Germany", true));
        job = jobRepository.saveAndFlush(
                TestEntityFactory.createJob(company, "Senior Backend Java Engineer", "Germany", "unavail-prep"));
    }

    @AfterEach
    void cleanDatabase() {
        jobMatchScoreRepository.deleteAllInBatch();
        jobRepository.deleteAllInBatch();
        companyRepository.deleteAllInBatch();
        refreshTokenRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
    }

    @Test
    void createSession_returns503WhenAiDisabled_whileListAndHistoryRemainOperational() throws Exception {
        CreateInterviewPrepSessionRequest req = CreateInterviewPrepSessionRequest.builder()
                .jobId(job.getId())
                .questionCount(5)
                .build();

        mockMvc.perform(post("/api/v1/interview-prep/sessions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value(503));

        mockMvc.perform(get("/api/v1/interview-prep/sessions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        mockMvc.perform(get("/api/v1/interview-prep/history")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalSessions").value(0));
    }
}

