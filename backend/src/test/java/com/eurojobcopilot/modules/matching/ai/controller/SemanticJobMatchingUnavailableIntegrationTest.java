package com.eurojobcopilot.modules.matching.ai.controller;

import com.eurojobcopilot.core.enums.WorkArrangement;
import com.eurojobcopilot.modules.auth.dto.RegisterRequest;
import com.eurojobcopilot.modules.auth.entity.User;
import com.eurojobcopilot.modules.auth.repository.RefreshTokenRepository;
import com.eurojobcopilot.modules.auth.repository.UserRepository;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SemanticJobMatchingUnavailableIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private JobMatchScoreRepository jobMatchScoreRepository;
    @Autowired
    private UserSkillRepository userSkillRepository;
    @Autowired
    private UserProfileRepository userProfileRepository;
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
                .email("semantic.unavailable@eurojob.eu")
                .password("SecureP@ssw0rd2026!")
                .firstName("Nina")
                .lastName("Brandt")
                .build();

        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(register)))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        token = json.get("accessToken").asText();
        User user = userRepository.findByEmailIgnoreCase("semantic.unavailable@eurojob.eu").orElseThrow();

        userProfileRepository.saveAndFlush(UserProfile.builder()
                .user(user)
                .currentDesignation("Senior Java Engineer")
                .yearsOfExperience(new BigDecimal("5.0"))
                .currentCountry("Germany")
                .build());

        userSkillRepository.saveAndFlush(UserSkill.builder()
                .user(user)
                .skillName("Java")
                .normalizedSkill("java")
                .yearsExperience(new BigDecimal("5.0"))
                .proficiencyLevel(ProficiencyLevel.EXPERT)
                .primary(true)
                .build());

        job = jobRepository.saveAndFlush(Job.builder()
                .canonicalHash(TestEntityFactory.sha256Hex("semantic-unavailable-job"))
                .companyName("FinTech GmbH")
                .title("Senior Java Engineer")
                .normalizedTitle("senior java engineer")
                .country("Germany")
                .city("Berlin")
                .location("Berlin, Germany")
                .workArrangement(WorkArrangement.HYBRID)
                .description("Build resilient Java 21 backend payment platforms.")
                .sourceProvider("ARBEITNOW")
                .originalUrl("https://jobs.example.eu/semantic-unavail")
                .postedAt(Instant.now().minus(1, ChronoUnit.HOURS))
                .active(true)
                .build());
    }

    @AfterEach
    void cleanDatabase() {
        jobMatchScoreRepository.deleteAllInBatch();
        userSkillRepository.deleteAllInBatch();
        userProfileRepository.deleteAllInBatch();
        jobRepository.deleteAllInBatch();
        refreshTokenRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
    }

    @Test
    void evaluateSemanticJobMatch_returns503WhenAiDisabledWithoutFabricatingScore() throws Exception {
        mockMvc.perform(post("/api/v1/jobs/" + job.getId() + "/intelligence/ai-match")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value(503));
    }
}

