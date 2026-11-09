package com.eurojobcopilot.modules.user.controller;

import com.eurojobcopilot.modules.auth.dto.RegisterRequest;
import com.eurojobcopilot.modules.auth.repository.RefreshTokenRepository;
import com.eurojobcopilot.modules.auth.repository.UserRepository;
import com.eurojobcopilot.modules.user.dto.UpsertEducationRequest;
import com.eurojobcopilot.modules.user.dto.UpsertProfileRequest;
import com.eurojobcopilot.modules.user.dto.UpsertSkillRequest;
import com.eurojobcopilot.modules.user.dto.UpsertWorkExperienceRequest;
import com.eurojobcopilot.modules.user.entity.ProficiencyLevel;
import com.eurojobcopilot.modules.user.entity.RelocationPreference;
import com.eurojobcopilot.modules.user.entity.WorkAuthorizationStatus;
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
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class UserProfileControllerIntegrationTest {

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
    void profileEndpoints_unauthenticatedRequests_return401() throws Exception {
        mockMvc.perform(get("/api/v1/profile"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));

        mockMvc.perform(put("/api/v1/profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/v1/profile/skills"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/v1/profile/education"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/v1/profile/experience"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void profileLifecycle_createReadUpdateDelete_persistsAllFieldsAndEnforcesRestSemantics() throws Exception {
        String token = registerUser("alex@eurojob.eu", "Alex", "Mueller");

        // GET before create -> 404
        mockMvc.perform(get("/api/v1/profile")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));

        // POST create profile -> 201
        UpsertProfileRequest createRequest = UpsertProfileRequest.builder()
                .firstName("Alex")
                .lastName("Mueller")
                .currentDesignation("Senior Java Backend Engineer")
                .yearsOfExperience(new BigDecimal("6.5"))
                .currentCountry("India")
                .currentCity("Bengaluru")
                .expectedSalaryMin(new BigDecimal("85000.00"))
                .expectedSalaryCurrency("eur")
                .noticePeriodDays(30)
                .workAuthorizationStatus(WorkAuthorizationStatus.NEEDS_SPONSORSHIP)
                .relocationPreference(RelocationPreference.WILLING_TO_RELOCATE)
                .linkedinUrl("https://www.linkedin.com/in/alex-mueller")
                .githubUrl("https://github.com/alex-mueller")
                .portfolioUrl("https://alexmueller.dev")
                .bio("Backend specialist building high-throughput Java platforms.")
                .preferredJobTitles(List.of("Senior Java Engineer", "Staff Backend Engineer"))
                .preferredCountries(List.of("Germany", "Netherlands"))
                .build();

        mockMvc.perform(post("/api/v1/profile")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("alex@eurojob.eu"))
                .andExpect(jsonPath("$.fullName").value("Alex Mueller"))
                .andExpect(jsonPath("$.currentDesignation").value("Senior Java Backend Engineer"))
                .andExpect(jsonPath("$.yearsOfExperience").value(6.5))
                .andExpect(jsonPath("$.currentCountry").value("India"))
                .andExpect(jsonPath("$.currentCity").value("Bengaluru"))
                .andExpect(jsonPath("$.expectedSalaryMin").value(85000.0))
                .andExpect(jsonPath("$.expectedSalaryCurrency").value("EUR"))
                .andExpect(jsonPath("$.noticePeriodDays").value(30))
                .andExpect(jsonPath("$.workAuthorizationStatus").value("NEEDS_SPONSORSHIP"))
                .andExpect(jsonPath("$.relocationPreference").value("WILLING_TO_RELOCATE"))
                .andExpect(jsonPath("$.linkedinUrl").value("https://www.linkedin.com/in/alex-mueller"))
                .andExpect(jsonPath("$.githubUrl").value("https://github.com/alex-mueller"))
                .andExpect(jsonPath("$.portfolioUrl").value("https://alexmueller.dev"))
                .andExpect(jsonPath("$.preferredJobTitles", containsInAnyOrder("Senior Java Engineer", "Staff Backend Engineer")))
                .andExpect(jsonPath("$.preferredCountries", containsInAnyOrder("Germany", "Netherlands")))
                .andExpect(jsonPath("$.createdAt").isNotEmpty());

        // Second POST -> 409 Conflict
        mockMvc.perform(post("/api/v1/profile")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));

        // PUT update profile (also updating name, salary, country, authorization, preferences) -> 200
        UpsertProfileRequest updateRequest = UpsertProfileRequest.builder()
                .firstName("Alexander")
                .lastName("Mueller-Schmidt")
                .currentDesignation("Principal Platform Engineer")
                .yearsOfExperience(new BigDecimal("8.0"))
                .currentCountry("Germany")
                .currentCity("Munich")
                .expectedSalaryMin(new BigDecimal("105000.00"))
                .expectedSalaryCurrency("EUR")
                .noticePeriodDays(60)
                .workAuthorizationStatus(WorkAuthorizationStatus.BLUE_CARD_HOLDER)
                .relocationPreference(RelocationPreference.ALREADY_IN_EUROPE)
                .linkedinUrl("https://www.linkedin.com/in/alexander-ms")
                .githubUrl("https://github.com/alexander-ms")
                .preferredJobTitles(List.of("Principal Backend Engineer"))
                .preferredCountries(List.of("Germany", "Switzerland", "Austria"))
                .build();

        mockMvc.perform(put("/api/v1/profile")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName").value("Alexander"))
                .andExpect(jsonPath("$.lastName").value("Mueller-Schmidt"))
                .andExpect(jsonPath("$.email").value("alex@eurojob.eu"))
                .andExpect(jsonPath("$.currentCity").value("Munich"))
                .andExpect(jsonPath("$.currentCountry").value("Germany"))
                .andExpect(jsonPath("$.workAuthorizationStatus").value("BLUE_CARD_HOLDER"))
                .andExpect(jsonPath("$.relocationPreference").value("ALREADY_IN_EUROPE"))
                .andExpect(jsonPath("$.preferredJobTitles[0]").value("Principal Backend Engineer"))
                .andExpect(jsonPath("$.preferredCountries", containsInAnyOrder("Germany", "Switzerland", "Austria")));

        // GET complete profile -> 200
        mockMvc.perform(get("/api/v1/profile")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentDesignation").value("Principal Platform Engineer"))
                .andExpect(jsonPath("$.expectedSalaryMin").value(105000.0));

        // DELETE profile -> 200, subsequent GET -> 404
        mockMvc.perform(delete("/api/v1/profile")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        mockMvc.perform(get("/api/v1/profile")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void profileValidation_invalidUrlsSalaryCurrencyNoticeAndEnums_returns400() throws Exception {
        String token = registerUser("validator@eurojob.eu", "Elena", "Vance");

        UpsertProfileRequest invalid = UpsertProfileRequest.builder()
                .firstName("A")
                .yearsOfExperience(new BigDecimal("-1.0"))
                .expectedSalaryMin(new BigDecimal("-500.00"))
                .expectedSalaryCurrency("EURO")
                .noticePeriodDays(900)
                .linkedinUrl("javascript:alert(1)")
                .githubUrl("not-a-valid-url")
                .build();

        mockMvc.perform(put("/api/v1/profile")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.validationErrors[*].field", hasItem("firstName")))
                .andExpect(jsonPath("$.validationErrors[*].field", hasItem("yearsOfExperience")))
                .andExpect(jsonPath("$.validationErrors[*].field", hasItem("expectedSalaryMin")))
                .andExpect(jsonPath("$.validationErrors[*].field", hasItem("expectedSalaryCurrency")))
                .andExpect(jsonPath("$.validationErrors[*].field", hasItem("noticePeriodDays")))
                .andExpect(jsonPath("$.validationErrors[*].field", hasItem("linkedinUrl")))
                .andExpect(jsonPath("$.validationErrors[*].field", hasItem("githubUrl")));

        // Invalid enum value returns structured 400 ApiErrorResponse
        String badEnumJson = """
                {"workAuthorizationStatus": "INVALID_VISA_STATUS"}
                """;
        mockMvc.perform(put("/api/v1/profile")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(badEnumJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value(containsString("Malformed request payload")));

        // Padded 1-character firstName fails trimmed-length rule with 400
        mockMvc.perform(put("/api/v1/profile")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\":\" A \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value(containsString("First name")));

        // Invalid path variable type returns structured 400 ApiErrorResponse
        mockMvc.perform(get("/api/v1/profile/skills/not-a-number")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void subResourcesCrud_skillsEducationAndWorkExperience_persistedAndIncludedInProfile() throws Exception {
        String token = registerUser("subres@eurojob.eu", "Marcus", "Weber");

        mockMvc.perform(put("/api/v1/profile")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentDesignation\":\"Java Engineer\"}"))
                .andExpect(status().isOk());

        // Skills CRUD
        MvcResult createSkillResult = mockMvc.perform(post("/api/v1/profile/skills")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(UpsertSkillRequest.builder()
                                .skillName("Spring Boot")
                                .yearsExperience(new BigDecimal("5.0"))
                                .proficiencyLevel(ProficiencyLevel.EXPERT)
                                .primary(true)
                                .build())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.normalizedSkill").value("spring boot"))
                .andExpect(jsonPath("$.primary").value(true))
                .andReturn();

        Long skillId = objectMapper.readTree(createSkillResult.getResponse().getContentAsString()).get("id").asLong();

        // Duplicate normalized skill -> 409
        mockMvc.perform(post("/api/v1/profile/skills")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(UpsertSkillRequest.builder()
                                .skillName("  SPRING BOOT ")
                                .yearsExperience(new BigDecimal("2.0"))
                                .proficiencyLevel(ProficiencyLevel.INTERMEDIATE)
                                .primary(false)
                                .build())))
                .andExpect(status().isConflict());

        // Update skill
        mockMvc.perform(put("/api/v1/profile/skills/" + skillId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(UpsertSkillRequest.builder()
                                .skillName("Spring Framework")
                                .yearsExperience(new BigDecimal("6.0"))
                                .proficiencyLevel(ProficiencyLevel.EXPERT)
                                .primary(true)
                                .build())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.normalizedSkill").value("spring framework"))
                .andExpect(jsonPath("$.yearsExperience").value(6.0));

        // Education CRUD + date range validation
        mockMvc.perform(post("/api/v1/profile/education")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(UpsertEducationRequest.builder()
                                .institution("TU Munich")
                                .degree("M.Sc.")
                                .fieldOfStudy("Computer Science")
                                .startDate(LocalDate.of(2020, 10, 1))
                                .endDate(LocalDate.of(2019, 9, 1))
                                .country("Germany")
                                .build())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("end date")));

        MvcResult createEduResult = mockMvc.perform(post("/api/v1/profile/education")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(UpsertEducationRequest.builder()
                                .institution("TU Munich")
                                .degree("M.Sc.")
                                .fieldOfStudy("Computer Science")
                                .startDate(LocalDate.of(2018, 10, 1))
                                .endDate(LocalDate.of(2020, 9, 30))
                                .gradeOrGpa("1.3")
                                .country("Germany")
                                .build())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.institution").value("TU Munich"))
                .andReturn();
        Long eduId = objectMapper.readTree(createEduResult.getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(put("/api/v1/profile/education/" + eduId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(UpsertEducationRequest.builder()
                                .institution("Technical University of Munich")
                                .degree("M.Sc.")
                                .fieldOfStudy("Distributed Systems")
                                .startDate(LocalDate.of(2018, 10, 1))
                                .endDate(LocalDate.of(2020, 9, 30))
                                .gradeOrGpa("1.2")
                                .country("Germany")
                                .build())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.institution").value("Technical University of Munich"));

        // Work experience CRUD
        MvcResult createExpResult = mockMvc.perform(post("/api/v1/profile/experience")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(UpsertWorkExperienceRequest.builder()
                                .companyName("Adyen")
                                .designation("Senior Java Engineer")
                                .location("Amsterdam, Netherlands")
                                .startDate(LocalDate.of(2022, 3, 1))
                                .current(true)
                                .description("Built payment routing services.")
                                .skillsUsed(List.of("Java", "Kafka", "PostgreSQL"))
                                .build())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.current").value(true))
                .andExpect(jsonPath("$.skillsUsed", containsInAnyOrder("Java", "Kafka", "PostgreSQL")))
                .andReturn();
        Long expId = objectMapper.readTree(createExpResult.getResponse().getContentAsString()).get("id").asLong();

        // Complete GET /api/v1/profile embeds skills, educations, experiences
        mockMvc.perform(get("/api/v1/profile")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.skills[0].skillName").value("Spring Framework"))
                .andExpect(jsonPath("$.educations[0].fieldOfStudy").value("Distributed Systems"))
                .andExpect(jsonPath("$.experiences[0].companyName").value("Adyen"));

        // Single-item GET endpoints by ID return owned records
        mockMvc.perform(get("/api/v1/profile/skills/" + skillId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.skillName").value("Spring Framework"));

        mockMvc.perform(get("/api/v1/profile/education/" + eduId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.institution").value("Technical University of Munich"));

        mockMvc.perform(get("/api/v1/profile/experience/" + expId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.companyName").value("Adyen"));

        // Delete sub-resources
        mockMvc.perform(delete("/api/v1/profile/skills/" + skillId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());
        mockMvc.perform(delete("/api/v1/profile/education/" + eduId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());
        mockMvc.perform(delete("/api/v1/profile/experience/" + expId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());

        assertThat(userSkillRepository.count()).isZero();
        assertThat(userEducationRepository.count()).isZero();
        assertThat(userWorkExperienceRepository.count()).isZero();
    }

    @Test
    void ownershipIsolation_usersCannotAccessOrModifyAnotherUsersProfileResources() throws Exception {
        String tokenUserA = registerUser("owner.a@eurojob.eu", "Alice", "Owner");
        String tokenUserB = registerUser("intruder.b@eurojob.eu", "Bob", "Intruder");

        // User A creates profile, skill, education, experience
        mockMvc.perform(post("/api/v1/profile")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenUserA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(UpsertProfileRequest.builder()
                                .currentDesignation("Alice Private Title")
                                .preferredCountries(List.of("Netherlands"))
                                .build())))
                .andExpect(status().isCreated());

        Long skillIdA = objectMapper.readTree(mockMvc.perform(post("/api/v1/profile/skills")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenUserA)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(UpsertSkillRequest.builder()
                                        .skillName("Kotlin")
                                        .yearsExperience(new BigDecimal("3.0"))
                                        .proficiencyLevel(ProficiencyLevel.ADVANCED)
                                        .primary(true)
                                        .build())))
                        .andExpect(status().isCreated())
                        .andReturn().getResponse().getContentAsString())
                .get("id").asLong();

        Long eduIdA = objectMapper.readTree(mockMvc.perform(post("/api/v1/profile/education")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenUserA)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(UpsertEducationRequest.builder()
                                        .institution("ETH Zurich")
                                        .degree("M.Sc.")
                                        .fieldOfStudy("CS")
                                        .startDate(LocalDate.of(2019, 9, 1))
                                        .country("Switzerland")
                                        .build())))
                        .andExpect(status().isCreated())
                        .andReturn().getResponse().getContentAsString())
                .get("id").asLong();

        Long expIdA = objectMapper.readTree(mockMvc.perform(post("/api/v1/profile/experience")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenUserA)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(UpsertWorkExperienceRequest.builder()
                                        .companyName("Booking.com")
                                        .designation("Software Engineer")
                                        .startDate(LocalDate.of(2021, 5, 1))
                                        .current(true)
                                        .build())))
                        .andExpect(status().isCreated())
                        .andReturn().getResponse().getContentAsString())
                .get("id").asLong();

        // User B has no profile -> GET /api/v1/profile returns 404 (cannot see User A's profile)
        mockMvc.perform(get("/api/v1/profile")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenUserB))
                .andExpect(status().isNotFound());

        // User B lists own skills/education/experience -> empty lists
        mockMvc.perform(get("/api/v1/profile/skills")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenUserB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        // User B attempts IDOR single-item GET, update & delete on User A's skill/education/experience -> 404
        mockMvc.perform(get("/api/v1/profile/skills/" + skillIdA)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenUserB))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/v1/profile/education/" + eduIdA)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenUserB))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/v1/profile/experience/" + expIdA)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenUserB))
                .andExpect(status().isNotFound());

        mockMvc.perform(put("/api/v1/profile/skills/" + skillIdA)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenUserB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(UpsertSkillRequest.builder()
                                .skillName("Hacked")
                                .yearsExperience(new BigDecimal("1.0"))
                                .proficiencyLevel(ProficiencyLevel.BEGINNER)
                                .primary(false)
                                .build())))
                .andExpect(status().isNotFound());

        mockMvc.perform(delete("/api/v1/profile/skills/" + skillIdA)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenUserB))
                .andExpect(status().isNotFound());

        mockMvc.perform(put("/api/v1/profile/education/" + eduIdA)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenUserB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(UpsertEducationRequest.builder()
                                .institution("Tampered")
                                .degree("B.Sc.")
                                .fieldOfStudy("CS")
                                .startDate(LocalDate.of(2020, 1, 1))
                                .country("France")
                                .build())))
                .andExpect(status().isNotFound());

        mockMvc.perform(delete("/api/v1/profile/education/" + eduIdA)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenUserB))
                .andExpect(status().isNotFound());

        mockMvc.perform(put("/api/v1/profile/experience/" + expIdA)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenUserB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(UpsertWorkExperienceRequest.builder()
                                .companyName("Tampered")
                                .designation("Tampered")
                                .startDate(LocalDate.of(2021, 1, 1))
                                .current(true)
                                .build())))
                .andExpect(status().isNotFound());

        mockMvc.perform(delete("/api/v1/profile/experience/" + expIdA)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenUserB))
                .andExpect(status().isNotFound());

        // User B attempts to overwrite auth email via profile update -> email stays immutable
        mockMvc.perform(put("/api/v1/profile")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenUserB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\":\"Bob\",\"email\":\"owner.a@eurojob.eu\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("intruder.b@eurojob.eu"));

        // Verify User A's data stayed completely untouched
        mockMvc.perform(get("/api/v1/profile")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenUserA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentDesignation").value("Alice Private Title"))
                .andExpect(jsonPath("$.skills[0].skillName").value("Kotlin"))
                .andExpect(jsonPath("$.educations[0].institution").value("ETH Zurich"))
                .andExpect(jsonPath("$.experiences[0].companyName").value("Booking.com"));
    }

    @Test
    void openApiSpecification_exposesProfileEndpointsCleanly() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/profile']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/profile/skills']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/profile/skills/{id}']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/profile/education']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/profile/education/{id}']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/profile/experience']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/profile/experience/{id}']").exists());
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
