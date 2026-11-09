package com.eurojobcopilot.modules.job.ingestion.controller;

import com.eurojobcopilot.modules.auth.dto.LoginRequest;
import com.eurojobcopilot.modules.auth.dto.RegisterRequest;
import com.eurojobcopilot.modules.auth.entity.AccountStatus;
import com.eurojobcopilot.modules.auth.entity.User;
import com.eurojobcopilot.modules.auth.entity.UserRole;
import com.eurojobcopilot.modules.auth.repository.RefreshTokenRepository;
import com.eurojobcopilot.modules.auth.repository.UserRepository;
import com.eurojobcopilot.modules.job.entity.FetchStatus;
import com.eurojobcopilot.modules.job.entity.Job;
import com.eurojobcopilot.modules.job.entity.JobFetchLog;
import com.eurojobcopilot.modules.job.ingestion.IngestionQuery;
import com.eurojobcopilot.modules.job.ingestion.JobProvider;
import com.eurojobcopilot.modules.job.ingestion.RawJobPosting;
import com.eurojobcopilot.modules.job.ingestion.dto.TriggerIngestionRequest;
import com.eurojobcopilot.modules.job.repository.JobFetchLogRepository;
import com.eurojobcopilot.modules.job.repository.JobRepository;
import com.eurojobcopilot.modules.matching.repository.JobMatchScoreRepository;
import com.eurojobcopilot.modules.sponsorship.repository.CompanyRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminIngestionControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private JobMatchScoreRepository jobMatchScoreRepository;

    @Autowired
    private JobRepository jobRepository;

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private JobFetchLogRepository jobFetchLogRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ControllableTestProvider controllableTestProvider;

    @TestConfiguration(proxyBeanMethods = false)
    static class TestProviderConfig {

        @Bean
        ControllableTestProvider controllableTestProvider() {
            return new ControllableTestProvider();
        }
    }

    static class ControllableTestProvider implements JobProvider {

        private final AtomicBoolean enabled = new AtomicBoolean(false);
        private final AtomicReference<CountDownLatch> enteredLatch = new AtomicReference<>();
        private final AtomicReference<CountDownLatch> releaseLatch = new AtomicReference<>();

        void setEnabled(boolean value) {
            enabled.set(value);
        }

        void armBlockingLatches(CountDownLatch entered, CountDownLatch release) {
            enteredLatch.set(entered);
            releaseLatch.set(release);
        }

        void reset() {
            enabled.set(false);
            enteredLatch.set(null);
            releaseLatch.set(null);
        }

        @Override
        public String getProviderName() {
            return "TEST_BOARD";
        }

        @Override
        public boolean isEnabled() {
            return enabled.get();
        }

        @Override
        public List<RawJobPosting> fetchJobs(IngestionQuery query) {
            CountDownLatch entered = enteredLatch.get();
            CountDownLatch release = releaseLatch.get();
            if (entered != null) {
                entered.countDown();
            }
            if (release != null) {
                try {
                    release.await(5, TimeUnit.SECONDS);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
            }
            return List.of(RawJobPosting.builder()
                    .sourceProvider("TEST_BOARD")
                    .sourceJobId("tb-1")
                    .title("Senior Platform Engineer")
                    .companyName("EuroCloud GmbH")
                    .city("Berlin")
                    .country("Germany")
                    .rawDescription("<p>Spring Boot and Kubernetes platform engineering with EU Blue Card support.</p>")
                    .originalUrl("https://testboard.eu/jobs/tb-1")
                    .postedAt(Instant.now())
                    .build());
        }
    }

    @BeforeEach
    @AfterEach
    void resetState() {
        controllableTestProvider.reset();
        jobFetchLogRepository.deleteAll();
        jobMatchScoreRepository.deleteAllInBatch();
        jobRepository.deleteAll();
        companyRepository.deleteAll();
        refreshTokenRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
    }

    @Test
    void shouldEnforceAdminRoleAcrossAllIngestionAdminEndpoints() throws Exception {
        String userToken = registerStandardUser("user.ingestion@eurojob.eu");

        mockMvc.perform(get("/api/v1/admin/ingestion/status"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/admin/ingestion/logs"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/admin/ingestion/trigger"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/admin/ingestion/cleanup-stale"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/v1/admin/ingestion/status")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));

        mockMvc.perform(post("/api/v1/admin/ingestion/trigger")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/admin/ingestion/cleanup-stale")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldTriggerManualIngestionReturnStatusPaginatedLogsAndCleanupStaleJobs() throws Exception {
        String adminToken = createAndLoginAdmin("admin.ingestion@eurojob.eu");

        // 1. Unknown provider -> 404; disabled provider -> 400
        mockMvc.perform(post("/api/v1/admin/ingestion/trigger")
                        .queryParam("provider", "UNKNOWN_BOARD")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/api/v1/admin/ingestion/trigger")
                        .queryParam("provider", "ADZUNA")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("disabled")));

        // 2. Enable synthetic TEST_BOARD provider and trigger manual ingestion via JSON payload
        controllableTestProvider.setEnabled(true);
        TriggerIngestionRequest triggerReq = TriggerIngestionRequest.builder()
                .provider("TEST_BOARD")
                .countries(List.of("Germany"))
                .keywords(List.of("Spring Boot"))
                .maxPages(1)
                .build();

        mockMvc.perform(post("/api/v1/admin/ingestion/trigger")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(triggerReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scope").value("TEST_BOARD"))
                .andExpect(jsonPath("$.providersExecuted").value(1))
                .andExpect(jsonPath("$.totalInserted").value(1))
                .andExpect(jsonPath("$.providerResults[0].providerName").value("TEST_BOARD"))
                .andExpect(jsonPath("$.providerResults[0].status").value("SUCCESS"));

        assertThat(jobRepository.count()).isEqualTo(1);

        // 3. Seed an extra FAILED log from ADZUNA and stale job records to test status, log filtering, and cleanup
        jobFetchLogRepository.save(JobFetchLog.builder()
                .providerName("ADZUNA")
                .status(FetchStatus.FAILED)
                .errorMessage("Simulated upstream 401")
                .executionDurationMs(45L)
                .fetchedAt(Instant.now().minus(1, ChronoUnit.HOURS))
                .build());

        jobRepository.save(Job.builder()
                .canonicalHash("stale-admin-hash-1")
                .companyName("Legacy GmbH")
                .title("Old Java Role")
                .normalizedTitle("old java role")
                .country("Germany")
                .description("Aged posting")
                .sourceProvider("ARBEITNOW")
                .originalUrl("https://example.com/old")
                .postedAt(Instant.now().minus(40, ChronoUnit.DAYS))
                .active(true)
                .build());

        mockMvc.perform(get("/api/v1/admin/ingestion/status")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.running").value(false))
                .andExpect(jsonPath("$.activeJobsCount").value(2))
                .andExpect(jsonPath("$.providers[*].providerName", hasItem("TEST_BOARD")))
                .andExpect(jsonPath("$.providers[*].providerName", hasItem("ARBEITNOW")))
                .andExpect(jsonPath("$.providers[*].providerName", hasItem("ADZUNA")));

        mockMvc.perform(get("/api/v1/admin/ingestion/logs")
                        .queryParam("provider", "TEST_BOARD")
                        .queryParam("status", "SUCCESS")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].providerName").value("TEST_BOARD"))
                .andExpect(jsonPath("$.items[0].jobsInserted").value(1));

        mockMvc.perform(post("/api/v1/admin/ingestion/cleanup-stale")
                        .queryParam("staleDays", "30")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deactivatedCount").value(1))
                .andExpect(jsonPath("$.staleExpirationDays").value(30));

        assertThat(jobRepository.countByActiveTrue()).isEqualTo(1);
    }

    @Test
    void shouldReturn409ConflictWhenTriggeringWhileAnotherIngestionIsInFlight() throws Exception {
        String adminToken = createAndLoginAdmin("admin.conflict@eurojob.eu");
        controllableTestProvider.setEnabled(true);

        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        controllableTestProvider.armBlockingLatches(entered, release);

        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<MvcResult> firstRequest = executor.submit(() -> mockMvc.perform(
                    post("/api/v1/admin/ingestion/trigger")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                    .andReturn());

            assertThat(entered.await(3, TimeUnit.SECONDS)).isTrue();

            mockMvc.perform(post("/api/v1/admin/ingestion/trigger")
                            .queryParam("provider", "TEST_BOARD")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.status").value(409))
                    .andExpect(jsonPath("$.message").value(containsString("already in progress")));

            release.countDown();
            MvcResult completed = firstRequest.get(5, TimeUnit.SECONDS);
            assertThat(completed.getResponse().getStatus()).isEqualTo(200);
        } finally {
            release.countDown();
            executor.shutdownNow();
        }
    }

    private String registerStandardUser(String email) throws Exception {
        RegisterRequest register = RegisterRequest.builder()
                .email(email)
                .password("SecureP@ssw0rd2026!")
                .firstName("Standard")
                .lastName("User")
                .build();

        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(register)))
                .andExpect(status().isCreated())
                .andReturn();

        return objectMapper.readTree(result.getResponse().getContentAsString())
                .get("accessToken").asText();
    }

    private String createAndLoginAdmin(String email) throws Exception {
        userRepository.saveAndFlush(User.builder()
                .email(email)
                .passwordHash(passwordEncoder.encode("AdminP@ssw0rd2026!"))
                .firstName("System")
                .lastName("Admin")
                .role(UserRole.ROLE_ADMIN)
                .accountStatus(AccountStatus.ACTIVE)
                .emailVerified(true)
                .build());

        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new LoginRequest(email, "AdminP@ssw0rd2026!"))))
                .andExpect(status().isOk())
                .andReturn();

        return objectMapper.readTree(result.getResponse().getContentAsString())
                .get("accessToken").asText();
    }
}

