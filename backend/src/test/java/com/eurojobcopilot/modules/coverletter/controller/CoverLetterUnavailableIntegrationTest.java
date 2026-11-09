package com.eurojobcopilot.modules.coverletter.controller;

import com.eurojobcopilot.core.enums.WorkArrangement;
import com.eurojobcopilot.modules.auth.dto.RegisterRequest;
import com.eurojobcopilot.modules.auth.entity.User;
import com.eurojobcopilot.modules.auth.repository.RefreshTokenRepository;
import com.eurojobcopilot.modules.auth.repository.UserRepository;
import com.eurojobcopilot.modules.coverletter.dto.GenerateCoverLetterRequest;
import com.eurojobcopilot.modules.coverletter.entity.CoverLetterTone;
import com.eurojobcopilot.modules.coverletter.repository.CoverLetterRepository;
import com.eurojobcopilot.modules.job.entity.Job;
import com.eurojobcopilot.modules.job.repository.JobRepository;
import com.eurojobcopilot.modules.matching.repository.JobMatchScoreRepository;
import com.eurojobcopilot.modules.user.entity.ProficiencyLevel;
import com.eurojobcopilot.modules.user.entity.UserProfile;
import com.eurojobcopilot.modules.user.entity.UserSkill;
import com.eurojobcopilot.modules.user.repository.UserProfileRepository;
import com.eurojobcopilot.modules.user.repository.UserSkillRepository;
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
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CoverLetterUnavailableIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private CoverLetterRepository coverLetterRepository;
    @Autowired
    private UserSkillRepository userSkillRepository;
    @Autowired
    private UserProfileRepository userProfileRepository;
    @Autowired
    private JobMatchScoreRepository jobMatchScoreRepository;
    @Autowired
    private JobRepository jobRepository;
    @Autowired
    private RefreshTokenRepository refreshTokenRepository;
    @Autowired
    private UserRepository userRepository;

    private String token;
    private Job job;

    @BeforeEach
    void setUp() throws Exception {
        cleanDatabase();
        RegisterRequest register = RegisterRequest.builder()
                .email("cover.unavail@eurojob.eu")
                .password("SecureP@ssw0rd2026!")
                .firstName("Clara")
                .lastName("Hoffmann")
                .build();

        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(register)))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        token = json.get("accessToken").asText();
        User user = userRepository.findByEmailIgnoreCase("cover.unavail@eurojob.eu").orElseThrow();

        userProfileRepository.saveAndFlush(UserProfile.builder()
                .user(user)
                .currentDesignation("Senior Java Engineer")
                .yearsOfExperience(new BigDecimal("6.0"))
                .currentCountry("Germany")
                .build());

        userSkillRepository.saveAndFlush(UserSkill.builder()
                .user(user)
                .skillName("Java 21")
                .normalizedSkill("java 21")
                .yearsExperience(new BigDecimal("6.0"))
                .proficiencyLevel(ProficiencyLevel.EXPERT)
                .primary(true)
                .build());

        job = jobRepository.saveAndFlush(Job.builder()
                .canonicalHash(TestEntityFactory.sha256Hex("cover-unavail-job"))
                .companyName("FinTech GmbH")
                .title("Senior Java Engineer")
                .normalizedTitle("senior java engineer")
                .country("Germany")
                .city("Berlin")
                .location("Berlin, Germany")
                .workArrangement(WorkArrangement.HYBRID)
                .description("Build resilient Java 21 backend payment platforms.")
                .sourceProvider("ARBEITNOW")
                .originalUrl("https://jobs.example.eu/cover-unavail")
                .postedAt(Instant.now().minus(1, ChronoUnit.HOURS))
                .active(true)
                .build());
    }

    @AfterEach
    void cleanDatabase() {
        coverLetterRepository.deleteAllInBatch();
        jobMatchScoreRepository.deleteAllInBatch();
        userSkillRepository.deleteAllInBatch();
        userProfileRepository.deleteAllInBatch();
        jobRepository.deleteAllInBatch();
        refreshTokenRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
    }

    @Test
    void generateCoverLetter_returns503WhenAiDisabled_whileReadEndpointsRemainOperational() throws Exception {
        GenerateCoverLetterRequest request = GenerateCoverLetterRequest.builder()
                .jobId(job.getId())
                .tone(CoverLetterTone.PROFESSIONAL)
                .build();

        mockMvc.perform(post("/api/v1/tailored-documents/cover-letter/generate")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value(503));

        mockMvc.perform(get("/api/v1/tailored-documents/cover-letters")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        mockMvc.perform(get("/api/v1/tailored-documents/cover-letter/" + UUID.randomUUID())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }
}

