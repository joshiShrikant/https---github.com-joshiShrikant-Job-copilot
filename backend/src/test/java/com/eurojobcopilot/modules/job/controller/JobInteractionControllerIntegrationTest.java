package com.eurojobcopilot.modules.job.controller;

import com.eurojobcopilot.modules.auth.dto.RegisterRequest;
import com.eurojobcopilot.modules.auth.entity.User;
import com.eurojobcopilot.modules.auth.repository.RefreshTokenRepository;
import com.eurojobcopilot.modules.auth.repository.UserRepository;
import com.eurojobcopilot.modules.job.dto.ApplyRedirectRequest;
import com.eurojobcopilot.modules.job.entity.Job;
import com.eurojobcopilot.modules.job.entity.JobSponsorStatus;
import com.eurojobcopilot.modules.job.entity.UserJobInteraction;
import com.eurojobcopilot.modules.job.repository.JobRepository;
import com.eurojobcopilot.modules.job.repository.UserJobInteractionRepository;
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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class JobInteractionControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserJobInteractionRepository userJobInteractionRepository;

    @Autowired
    private JobMatchScoreRepository jobMatchScoreRepository;

    @Autowired
    private JobRepository jobRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private UserRepository userRepository;

    private String tokenA;
    private String tokenB;
    private User userA;

    private Job jobAdyen;
    private Job jobFallbackOriginalUrl;
    private Job jobUnsafeUrl;

    @BeforeEach
    void setUp() throws Exception {
        cleanDatabase();

        tokenA = registerUser("interact.a@eurojob.eu", "Lukas", "Mueller");
        tokenB = registerUser("interact.b@eurojob.eu", "Elena", "Weber");
        userA = userRepository.findByEmailIgnoreCase("interact.a@eurojob.eu").orElseThrow();

        Instant now = Instant.now();

        jobAdyen = jobRepository.saveAndFlush(Job.builder()
                .canonicalHash(TestEntityFactory.sha256Hex("interact-adyen-1"))
                .companyName("Adyen N.V.")
                .title("Senior Java Engineer")
                .normalizedTitle("senior java engineer")
                .location("Amsterdam, Netherlands")
                .city("Amsterdam")
                .country("Netherlands")
                .description("Build scalable global payment systems with visa sponsorship.")
                .skills(List.of("Java 21", "Spring Boot"))
                .sourceProvider("ARBEITNOW")
                .originalUrl("https://careers.adyen.com/jobs/1001")
                .applicationUrl("https://careers.adyen.com/jobs/1001/apply")
                .sponsorStatus(JobSponsorStatus.VERIFIED_SPONSOR)
                .postedAt(now.minus(1, ChronoUnit.HOURS))
                .active(true)
                .build());

        jobFallbackOriginalUrl = jobRepository.saveAndFlush(Job.builder()
                .canonicalHash(TestEntityFactory.sha256Hex("interact-fallback-2"))
                .companyName("Personio SE")
                .title("Backend Kotlin Engineer")
                .normalizedTitle("backend kotlin engineer")
                .location("Munich, Germany")
                .city("Munich")
                .country("Germany")
                .description("HR SaaS engineering role.")
                .sourceProvider("ADZUNA")
                .originalUrl("https://careers.personio.de/jobs/2002")
                .applicationUrl(null)
                .sponsorStatus(JobSponsorStatus.SPONSORSHIP_MENTIONED)
                .postedAt(now.minus(2, ChronoUnit.HOURS))
                .active(true)
                .build());

        jobUnsafeUrl = jobRepository.saveAndFlush(Job.builder()
                .canonicalHash(TestEntityFactory.sha256Hex("interact-unsafe-3"))
                .companyName("Broken Corp")
                .title("Test Job")
                .normalizedTitle("test job")
                .country("Germany")
                .description("Job with non-HTTP link.")
                .sourceProvider("ADZUNA")
                .originalUrl("javascript:alert(1)")
                .applicationUrl("javascript:alert(1)")
                .sponsorStatus(JobSponsorStatus.UNCONFIRMED)
                .postedAt(now.minus(3, ChronoUnit.HOURS))
                .active(true)
                .build());
    }

    @AfterEach
    void cleanDatabase() {
        userJobInteractionRepository.deleteAllInBatch();
        jobMatchScoreRepository.deleteAllInBatch();
        jobRepository.deleteAllInBatch();
        refreshTokenRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
    }

    @Test
    void interactionEndpoints_requireJwtValidateIdsAndCheckExistence() throws Exception {
        mockMvc.perform(post("/api/v1/jobs/" + jobAdyen.getId() + "/save"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
        mockMvc.perform(delete("/api/v1/jobs/" + jobAdyen.getId() + "/save"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/jobs/" + jobAdyen.getId() + "/interested"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/api/v1/jobs/" + jobAdyen.getId() + "/interested"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/jobs/" + jobAdyen.getId() + "/hide"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/api/v1/jobs/" + jobAdyen.getId() + "/hide"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/jobs/" + jobAdyen.getId() + "/apply-redirect"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/v1/jobs/0/save")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        mockMvc.perform(post("/api/v1/jobs/not-numeric/hide")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        mockMvc.perform(post("/api/v1/jobs/999999/save")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));

        mockMvc.perform(delete("/api/v1/jobs/999999/hide")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));

        mockMvc.perform(post("/api/v1/jobs/999999/apply-redirect")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void saveAndInterestedRoundtrip_integratesWithSearchDetailAndDashboard() throws Exception {
        // Delete when no interaction exists yet does not create spurious DB rows
        mockMvc.perform(delete("/api/v1/jobs/" + jobAdyen.getId() + "/save")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSaved").value(false));
        assertThat(userJobInteractionRepository.count()).isZero();

        // Save job -> verifies explicit is* JSON naming and savedAt persistence
        MvcResult firstSave = mockMvc.perform(post("/api/v1/jobs/" + jobAdyen.getId() + "/save")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobId").value(jobAdyen.getId()))
                .andExpect(jsonPath("$.isSaved").value(true))
                .andExpect(jsonPath("$.savedAt").isNotEmpty())
                .andExpect(jsonPath("$.isInterested").value(false))
                .andExpect(jsonPath("$.isHidden").value(false))
                .andExpect(jsonPath("$.hasApplied").value(false))
                .andReturn();

        JsonNode saveJson = objectMapper.readTree(firstSave.getResponse().getContentAsString());
        assertThat(saveJson.has("saved")).isFalse();
        assertThat(saveJson.has("interested")).isFalse();
        assertThat(saveJson.has("hidden")).isFalse();
        String originalSavedAt = saveJson.get("savedAt").asText();

        // Repeat POST /save is idempotent and keeps original savedAt
        mockMvc.perform(post("/api/v1/jobs/" + jobAdyen.getId() + "/save")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSaved").value(true))
                .andExpect(jsonPath("$.savedAt").value(originalSavedAt));

        // Mark interested on jobAdyen as well -> verifies saved + interested coexist independently
        mockMvc.perform(post("/api/v1/jobs/" + jobAdyen.getId() + "/interested")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSaved").value(true))
                .andExpect(jsonPath("$.isInterested").value(true))
                .andExpect(jsonPath("$.interestedAt").isNotEmpty());

        // Search filters, job detail, and dashboard KPIs reflect the saved + interested state
        mockMvc.perform(get("/api/v1/jobs")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA)
                        .param("savedOnly", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(jobAdyen.getId()));

        mockMvc.perform(get("/api/v1/jobs")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA)
                        .param("interestedOnly", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(jobAdyen.getId()));

        mockMvc.perform(get("/api/v1/jobs/" + jobAdyen.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSaved").value(true))
                .andExpect(jsonPath("$.isInterested").value(true));

        mockMvc.perform(get("/api/v1/dashboard/summary")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.savedJobsCount").value(1))
                .andExpect(jsonPath("$.interestedJobsCount").value(1));

        // User B remains completely unaffected
        mockMvc.perform(get("/api/v1/jobs")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenB)
                        .param("savedOnly", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));

        // Clear interested + unsave restores counts cleanly
        mockMvc.perform(delete("/api/v1/jobs/" + jobAdyen.getId() + "/interested")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSaved").value(true))
                .andExpect(jsonPath("$.isInterested").value(false))
                .andExpect(jsonPath("$.interestedAt").isEmpty());

        mockMvc.perform(delete("/api/v1/jobs/" + jobAdyen.getId() + "/save")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSaved").value(false))
                .andExpect(jsonPath("$.savedAt").isEmpty());

        mockMvc.perform(get("/api/v1/dashboard/summary")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.savedJobsCount").value(0))
                .andExpect(jsonPath("$.interestedJobsCount").value(0));
    }

    @Test
    void hideAndUnhideRoundtrip_filtersDefaultSearchStrictlyPerUserAndPreservesOtherFlags() throws Exception {
        // Save jobAdyen first, then hide it
        mockMvc.perform(post("/api/v1/jobs/" + jobAdyen.getId() + "/save")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/jobs/" + jobAdyen.getId() + "/hide")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isHidden").value(true))
                .andExpect(jsonPath("$.hiddenAt").isNotEmpty())
                .andExpect(jsonPath("$.isSaved").value(true));

        // Default search for User A excludes hidden jobAdyen (2 remaining out of 3), includeHidden=true includes it
        mockMvc.perform(get("/api/v1/jobs")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));

        mockMvc.perform(get("/api/v1/jobs")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA)
                        .param("includeHidden", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.content[0].id").value(jobAdyen.getId()))
                .andExpect(jsonPath("$.content[0].isHidden").value(true));

        // User B still sees all 3 jobs
        mockMvc.perform(get("/api/v1/jobs")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3));

        // User A unhides -> jobAdyen immediately reappears and retains isSaved=true
        mockMvc.perform(delete("/api/v1/jobs/" + jobAdyen.getId() + "/hide")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isHidden").value(false))
                .andExpect(jsonPath("$.hiddenAt").isEmpty())
                .andExpect(jsonPath("$.isSaved").value(true));

        mockMvc.perform(get("/api/v1/jobs")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.content[0].id").value(jobAdyen.getId()))
                .andExpect(jsonPath("$.content[0].isHidden").value(false))
                .andExpect(jsonPath("$.content[0].isSaved").value(true));
    }

    @Test
    void applyRedirect_tracksTimestampsValidatesExternalUrlsAndOptionalMarkApplied() throws Exception {
        // Empty body tracks redirectClickedAt without setting hasApplied
        mockMvc.perform(post("/api/v1/jobs/" + jobAdyen.getId() + "/apply-redirect")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobId").value(jobAdyen.getId()))
                .andExpect(jsonPath("$.targetUrl").value("https://careers.adyen.com/jobs/1001/apply"))
                .andExpect(jsonPath("$.redirectClickedAt").isNotEmpty())
                .andExpect(jsonPath("$.hasApplied").value(false))
                .andExpect(jsonPath("$.appliedAt").isEmpty())
                .andExpect(jsonPath("$.interaction.hasApplied").value(false))
                .andExpect(jsonPath("$.interaction.redirectClickedAt").isNotEmpty());

        // Fallback to originalUrl when applicationUrl is null
        mockMvc.perform(post("/api/v1/jobs/" + jobFallbackOriginalUrl.getId() + "/apply-redirect")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                ApplyRedirectRequest.builder().markApplied(false).build())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.targetUrl").value("https://careers.personio.de/jobs/2002"))
                .andExpect(jsonPath("$.hasApplied").value(false));

        // markApplied=true sets hasApplied=true and appliedAt and reflects in job detail for User A only
        mockMvc.perform(post("/api/v1/jobs/" + jobAdyen.getId() + "/apply-redirect")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                ApplyRedirectRequest.builder().markApplied(true).build())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasApplied").value(true))
                .andExpect(jsonPath("$.appliedAt").isNotEmpty())
                .andExpect(jsonPath("$.interaction.hasApplied").value(true));

        mockMvc.perform(get("/api/v1/jobs/" + jobAdyen.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasApplied").value(true));

        mockMvc.perform(get("/api/v1/jobs/" + jobAdyen.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasApplied").value(false));

        UserJobInteraction persisted = userJobInteractionRepository
                .findByUserIdAndJobId(userA.getId(), jobAdyen.getId())
                .orElseThrow();
        assertThat(persisted.isHasApplied()).isTrue();
        assertThat(persisted.getAppliedAt()).isNotNull();
        assertThat(persisted.getRedirectClickedAt()).isNotNull();

        // Rejects javascript:/non-HTTP links cleanly with 400 and no DB write
        mockMvc.perform(post("/api/v1/jobs/" + jobUnsafeUrl.getId() + "/apply-redirect")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                ApplyRedirectRequest.builder().markApplied(true).build())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        assertThat(userJobInteractionRepository.findByUserIdAndJobId(userA.getId(), jobUnsafeUrl.getId()))
                .isEmpty();
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

