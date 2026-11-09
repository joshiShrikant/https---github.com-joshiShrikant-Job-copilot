package com.eurojobcopilot.modules.recruiter.controller;

import com.eurojobcopilot.modules.auth.dto.RegisterRequest;
import com.eurojobcopilot.modules.auth.entity.User;
import com.eurojobcopilot.modules.auth.repository.RefreshTokenRepository;
import com.eurojobcopilot.modules.auth.repository.UserRepository;
import com.eurojobcopilot.modules.recruiter.dto.CreateRecruiterRequest;
import com.eurojobcopilot.modules.recruiter.dto.GenerateOutreachRequest;
import com.eurojobcopilot.modules.recruiter.entity.CommunicationChannel;
import com.eurojobcopilot.modules.recruiter.entity.RecruiterMessageType;
import com.eurojobcopilot.modules.recruiter.repository.RecruiterInteractionRepository;
import com.eurojobcopilot.modules.recruiter.repository.RecruiterRepository;
import com.eurojobcopilot.modules.user.entity.UserProfile;
import com.eurojobcopilot.modules.user.repository.UserProfileRepository;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RecruiterOutreachUnavailableIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private RecruiterInteractionRepository interactionRepository;
    @Autowired
    private RecruiterRepository recruiterRepository;
    @Autowired
    private UserProfileRepository userProfileRepository;
    @Autowired
    private RefreshTokenRepository refreshTokenRepository;
    @Autowired
    private UserRepository userRepository;

    private String token;
    private Long recruiterId;

    @BeforeEach
    void setUp() throws Exception {
        cleanDatabase();
        RegisterRequest req = RegisterRequest.builder()
                .email("crm.unavail@eurojob.eu")
                .password("SecureP@ssw0rd2026!")
                .firstName("Lina")
                .lastName("Vogel")
                .build();

        MvcResult regRes = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode regJson = objectMapper.readTree(regRes.getResponse().getContentAsString());
        token = regJson.get("accessToken").asText();
        User user = userRepository.findByEmailIgnoreCase("crm.unavail@eurojob.eu").orElseThrow();

        userProfileRepository.saveAndFlush(UserProfile.builder()
                .user(user)
                .currentDesignation("Senior Java Engineer")
                .yearsOfExperience(new BigDecimal("6.0"))
                .currentCountry("Germany")
                .build());

        MvcResult createRec = mockMvc.perform(post("/api/v1/recruiters")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(CreateRecruiterRequest.builder()
                                .fullName("Hans Visser")
                                .companyName("Mollie B.V.")
                                .build())))
                .andExpect(status().isCreated())
                .andReturn();
        recruiterId = objectMapper.readTree(createRec.getResponse().getContentAsString()).get("id").asLong();
    }

    @AfterEach
    void cleanDatabase() {
        interactionRepository.deleteAllInBatch();
        recruiterRepository.deleteAllInBatch();
        userProfileRepository.deleteAllInBatch();
        refreshTokenRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
    }

    @Test
    void generateOutreach_returns503WhenAiDisabled_whileCrmReadsAndManualDraftsWork() throws Exception {
        mockMvc.perform(post("/api/v1/recruiters/" + recruiterId + "/outreach/generate")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(GenerateOutreachRequest.builder()
                                .channel(CommunicationChannel.EMAIL)
                                .messageType(RecruiterMessageType.INITIAL_OUTREACH)
                                .build())))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value(503));

        mockMvc.perform(get("/api/v1/recruiters/" + recruiterId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Hans Visser"));
    }
}

