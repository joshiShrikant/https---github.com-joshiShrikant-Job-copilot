package com.eurojobcopilot.modules.user.controller;

import com.eurojobcopilot.core.enums.WorkArrangement;
import com.eurojobcopilot.modules.auth.dto.RegisterRequest;
import com.eurojobcopilot.modules.auth.repository.RefreshTokenRepository;
import com.eurojobcopilot.modules.auth.repository.UserRepository;
import com.eurojobcopilot.modules.user.dto.UpdateJobPreferencesRequest;
import com.eurojobcopilot.modules.user.repository.UserEducationRepository;
import com.eurojobcopilot.modules.user.repository.UserJobPreferenceRepository;
import com.eurojobcopilot.modules.user.repository.UserProfileRepository;
import com.eurojobcopilot.modules.user.repository.UserSkillRepository;
import com.eurojobcopilot.modules.user.repository.UserWorkExperienceRepository;
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
import java.util.List;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class JobPreferenceControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private UserProfileRepository userProfileRepository;

    @Autowired
    private UserSkillRepository userSkillRepository;

    @Autowired
    private UserEducationRepository userEducationRepository;

    @Autowired
    private UserWorkExperienceRepository userWorkExperienceRepository;

    @Autowired
    private UserJobPreferenceRepository userJobPreferenceRepository;

    @BeforeEach
    @AfterEach
    void cleanDatabase() {
        userJobPreferenceRepository.deleteAllInBatch();
        userWorkExperienceRepository.deleteAllInBatch();
        userEducationRepository.deleteAllInBatch();
        userSkillRepository.deleteAllInBatch();
        userProfileRepository.deleteAllInBatch();
        refreshTokenRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
    }

    @Test
    void preferencesEndpoints_unauthenticated_return401() throws Exception {
        mockMvc.perform(get("/api/v1/preferences"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));

        mockMvc.perform(put("/api/v1/preferences")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void preferencesLifecycle_readDefaultsUpdateSyncWithProfileAndTenantIsolation() throws Exception {
        String tokenA = registerUser("pref.a@eurojob.eu", "Alice", "Weber");
        String tokenB = registerUser("pref.b@eurojob.eu", "Bob", "Klein");

        // Fresh user receives default preference values cleanly
        mockMvc.perform(get("/api/v1/preferences")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.salaryCurrency").value("EUR"))
                .andExpect(jsonPath("$.requiresSponsorship").value(true))
                .andExpect(jsonPath("$.requiresRelocation").value(true))
                .andExpect(jsonPath("$.workArrangement").value("ANY"))
                .andExpect(jsonPath("$.englishSpeakingOnly").value(true))
                .andExpect(jsonPath("$.minMatchScore").value(60))
                .andExpect(jsonPath("$.autopilotEnabled").value(false))
                .andExpect(jsonPath("$.autopilotPaused").value(false))
                .andExpect(jsonPath("$.emailNotificationsEnabled").value(false));

        // Update preferences for User A
        UpdateJobPreferencesRequest updateRequest = UpdateJobPreferencesRequest.builder()
                .preferredJobTitles(List.of("Senior Java Engineer", "Staff Full-Stack Engineer"))
                .preferredCountries(List.of("Germany", "Netherlands"))
                .preferredCities(List.of("Berlin", "Amsterdam"))
                .minSalary(new BigDecimal("95000.00"))
                .salaryCurrency("eur")
                .requiresSponsorship(true)
                .requiresRelocation(false)
                .workArrangement(WorkArrangement.HYBRID)
                .englishSpeakingOnly(true)
                .minMatchScore(80)
                .autopilotEnabled(true)
                .autopilotPaused(true)
                .emailNotificationsEnabled(true)
                .build();

        mockMvc.perform(put("/api/v1/preferences")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.preferredJobTitles", containsInAnyOrder("Senior Java Engineer", "Staff Full-Stack Engineer")))
                .andExpect(jsonPath("$.preferredCountries", containsInAnyOrder("Germany", "Netherlands")))
                .andExpect(jsonPath("$.preferredCities", containsInAnyOrder("Berlin", "Amsterdam")))
                .andExpect(jsonPath("$.minSalary").value(95000.0))
                .andExpect(jsonPath("$.salaryCurrency").value("EUR"))
                .andExpect(jsonPath("$.requiresRelocation").value(false))
                .andExpect(jsonPath("$.workArrangement").value("HYBRID"))
                .andExpect(jsonPath("$.minMatchScore").value(80))
                .andExpect(jsonPath("$.autopilotEnabled").value(true))
                .andExpect(jsonPath("$.autopilotPaused").value(true))
                .andExpect(jsonPath("$.emailNotificationsEnabled").value(true))
                .andExpect(jsonPath("$.updatedAt").isNotEmpty());

        // User B remains strictly isolated with unmodified defaults
        mockMvc.perform(get("/api/v1/preferences")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.preferredCountries.length()").value(0))
                .andExpect(jsonPath("$.minMatchScore").value(60))
                .andExpect(jsonPath("$.autopilotEnabled").value(false));

        // Creating/updating User A's profile inherits/reads shared preferredJobTitles & preferredCountries cleanly
        mockMvc.perform(put("/api/v1/profile")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentDesignation\":\"Senior Java Engineer\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.preferredJobTitles", containsInAnyOrder("Senior Java Engineer", "Staff Full-Stack Engineer")))
                .andExpect(jsonPath("$.preferredCountries", containsInAnyOrder("Germany", "Netherlands")));
    }

    @Test
    void preferencesValidation_invalidScoreSalaryCurrencyAndWorkArrangement_returns400() throws Exception {
        String token = registerUser("pref.valid@eurojob.eu", "Elena", "Vance");

        UpdateJobPreferencesRequest invalid = UpdateJobPreferencesRequest.builder()
                .minSalary(new BigDecimal("-100.00"))
                .salaryCurrency("EURO")
                .minMatchScore(120)
                .build();

        mockMvc.perform(put("/api/v1/preferences")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.validationErrors[*].field", hasItem("minSalary")))
                .andExpect(jsonPath("$.validationErrors[*].field", hasItem("salaryCurrency")))
                .andExpect(jsonPath("$.validationErrors[*].field", hasItem("minMatchScore")));

        mockMvc.perform(put("/api/v1/preferences")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"workArrangement\": \"FULL_TIME_ONLY\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    private String registerUser(String email, String firstName, String lastName) throws Exception {
        RegisterRequest register = RegisterRequest.builder()
                .email(email)
                .password("SecureP@ssw0rd2026!")
                .firstName(firstName)
                .lastName(lastName)
                .build();

        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(register)))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        return json.get("accessToken").asText();
    }
}
