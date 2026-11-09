package com.eurojobcopilot.modules.assistant.controller;

import com.eurojobcopilot.modules.ai.client.AiJsonPromptRequest;
import com.eurojobcopilot.modules.ai.service.AiGatewayService;
import com.eurojobcopilot.modules.ai.service.AiStructuredResponse;
import com.eurojobcopilot.modules.assistant.ai.RawAiAssistantPayload;
import com.eurojobcopilot.modules.assistant.dto.JobAssistantRespondRequest;
import com.eurojobcopilot.modules.assistant.dto.UpdateJobAssistantMessageRequest;
import com.eurojobcopilot.modules.assistant.entity.AssistantActionType;
import com.eurojobcopilot.modules.assistant.repository.JobAssistantMessageRepository;
import com.eurojobcopilot.modules.auth.dto.RegisterRequest;
import com.eurojobcopilot.modules.auth.entity.User;
import com.eurojobcopilot.modules.auth.repository.RefreshTokenRepository;
import com.eurojobcopilot.modules.auth.repository.UserRepository;
import com.eurojobcopilot.modules.job.entity.Job;
import com.eurojobcopilot.modules.job.repository.JobRepository;
import com.eurojobcopilot.modules.matching.repository.JobMatchScoreRepository;
import com.eurojobcopilot.modules.sponsorship.entity.Company;
import com.eurojobcopilot.modules.sponsorship.repository.CompanyRepository;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class JobAssistantControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private JobAssistantMessageRepository messageRepository;
    @Autowired
    private UserSkillRepository userSkillRepository;
    @Autowired
    private UserProfileRepository userProfileRepository;
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
        tokenOwner = registerUser("assistant.owner@eurojob.eu", "Lukas", "Mueller");
        tokenOther = registerUser("assistant.other@eurojob.eu", "Sofia", "Berg");

        User owner = userRepository.findByEmailIgnoreCase("assistant.owner@eurojob.eu").orElseThrow();
        userProfileRepository.saveAndFlush(UserProfile.builder()
                .user(owner)
                .currentDesignation("Senior Fullstack Engineer")
                .yearsOfExperience(new BigDecimal("6.5"))
                .currentCountry("India")
                .build());
        userSkillRepository.saveAndFlush(UserSkill.builder()
                .user(owner)
                .skillName("Angular")
                .normalizedSkill("angular")
                .yearsExperience(new BigDecimal("5.0"))
                .proficiencyLevel(ProficiencyLevel.EXPERT)
                .primary(true)
                .build());

        Company company = companyRepository.saveAndFlush(
                TestEntityFactory.createCompany("Adyen Assistant BV", "Netherlands", true));
        targetJob = jobRepository.saveAndFlush(
                TestEntityFactory.createJob(company, "Senior Fullstack Java + Angular Engineer", "Netherlands", "asst-1"));
    }

    @AfterEach
    void cleanDatabase() {
        reset(aiGatewayService);
        messageRepository.deleteAllInBatch();
        jobMatchScoreRepository.deleteAllInBatch();
        userSkillRepository.deleteAllInBatch();
        userProfileRepository.deleteAllInBatch();
        jobRepository.deleteAllInBatch();
        companyRepository.deleteAllInBatch();
        refreshTokenRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
    }

    @Test
    void assistantLifecycle_respondEditHistoryDeleteAndTenantIsolation() throws Exception {
        when(aiGatewayService.executeStructuredJsonPrompt(any(), eq(RawAiAssistantPayload.class)))
                .thenReturn(new AiStructuredResponse<>(
                        "GEMINI",
                        "gemini-2.5-flash",
                        new RawAiAssistantPayload(
                                "Sponsorship & Fit Brief for Adyen Assistant BV",
                                "Subject: Senior Fullstack Application\n\nAdyen Assistant BV is listed as a verified sponsor. Your Angular and Java 21 background fits cleanly.",
                                List.of("Verified NL IND Sponsor", "Confirm vacancy sponsorship during recruiter call"),
                                List.of("Job posting + IND registry"),
                                List.of()
                        ),
                        "{}",
                        100,
                        80
                ));

        JobAssistantRespondRequest req = JobAssistantRespondRequest.builder()
                .actionType(AssistantActionType.EXPLAIN_SPONSORSHIP)
                .additionalContext("Targeting Amsterdam relocation in Q3")
                .build();

        MvcResult createResult = mockMvc.perform(post("/api/v1/jobs/" + targetJob.getId() + "/assistant/respond")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenOwner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.jobId").value(targetJob.getId()))
                .andExpect(jsonPath("$.actionType").value("EXPLAIN_SPONSORSHIP"))
                .andExpect(jsonPath("$.assistantTitle").value("Sponsorship & Fit Brief for Adyen Assistant BV"))
                .andExpect(jsonPath("$.editedByUser").value(false))
                .andExpect(jsonPath("$.highlights.length()").value(2))
                .andReturn();

        ArgumentCaptor<AiJsonPromptRequest> captor = ArgumentCaptor.forClass(AiJsonPromptRequest.class);
        verify(aiGatewayService).executeStructuredJsonPrompt(captor.capture(), eq(RawAiAssistantPayload.class));
        assertThat(captor.getValue().userPrompt()).contains("Lukas Mueller", "Adyen Assistant BV", "Targeting Amsterdam relocation");

        long messageId = objectMapper.readTree(createResult.getResponse().getContentAsString()).get("id").asLong();

        // Validation: CUSTOM_QUESTION without userPrompt returns 400
        mockMvc.perform(post("/api/v1/jobs/" + targetJob.getId() + "/assistant/respond")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenOwner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"actionType\":\"CUSTOM_QUESTION\",\"userPrompt\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        // Edit & save inline response
        UpdateJobAssistantMessageRequest editReq = UpdateJobAssistantMessageRequest.builder()
                .editableContent("Custom edited message tailored by Lukas for Adyen hiring manager.")
                .build();

        mockMvc.perform(put("/api/v1/jobs/" + targetJob.getId() + "/assistant/" + messageId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenOwner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(editReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.editedByUser").value(true))
                .andExpect(jsonPath("$.editableContent").value("Custom edited message tailored by Lukas for Adyen hiring manager."));

        // Owner sees history; other user sees empty history and gets 404 when mutating owner's message
        mockMvc.perform(get("/api/v1/jobs/" + targetJob.getId() + "/assistant/history")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenOwner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));

        mockMvc.perform(get("/api/v1/jobs/" + targetJob.getId() + "/assistant/history")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenOther))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        mockMvc.perform(delete("/api/v1/jobs/" + targetJob.getId() + "/assistant/" + messageId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenOther))
                .andExpect(status().isNotFound());

        // Owner deletes cleanly
        mockMvc.perform(delete("/api/v1/jobs/" + targetJob.getId() + "/assistant/" + messageId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenOwner))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/jobs/" + targetJob.getId() + "/assistant/history")
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

