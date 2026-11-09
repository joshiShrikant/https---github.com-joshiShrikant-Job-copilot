package com.eurojobcopilot.modules.sponsorship.controller;

import com.eurojobcopilot.core.enums.WorkArrangement;
import com.eurojobcopilot.modules.auth.dto.LoginRequest;
import com.eurojobcopilot.modules.auth.dto.RegisterRequest;
import com.eurojobcopilot.modules.auth.entity.AccountStatus;
import com.eurojobcopilot.modules.auth.entity.User;
import com.eurojobcopilot.modules.auth.entity.UserRole;
import com.eurojobcopilot.modules.auth.repository.RefreshTokenRepository;
import com.eurojobcopilot.modules.auth.repository.UserRepository;
import com.eurojobcopilot.modules.job.entity.EmploymentType;
import com.eurojobcopilot.modules.job.entity.Job;
import com.eurojobcopilot.modules.job.entity.JobAiAnalysis;
import com.eurojobcopilot.modules.job.entity.JobSponsorStatus;
import com.eurojobcopilot.modules.job.normalization.CanonicalHashGenerator;
import com.eurojobcopilot.modules.job.normalization.DeduplicationOutcome;
import com.eurojobcopilot.modules.job.normalization.DeduplicationService;
import com.eurojobcopilot.modules.job.normalization.JobNormalizer;
import com.eurojobcopilot.modules.job.ingestion.RawJobPosting;
import com.eurojobcopilot.modules.job.repository.JobAiAnalysisRepository;
import com.eurojobcopilot.modules.job.repository.JobRepository;
import com.eurojobcopilot.modules.matching.repository.JobMatchScoreRepository;
import com.eurojobcopilot.modules.sponsorship.catalog.CountrySponsorshipCatalog;
import com.eurojobcopilot.modules.sponsorship.entity.Company;
import com.eurojobcopilot.modules.sponsorship.entity.RegistrySyncStatus;
import com.eurojobcopilot.modules.sponsorship.entity.SponsorConfidenceLevel;
import com.eurojobcopilot.modules.sponsorship.ind.IndRecognizedSponsorClient;
import com.eurojobcopilot.modules.sponsorship.ind.IndRegisterParser;
import com.eurojobcopilot.modules.sponsorship.repository.CompanyRepository;
import com.eurojobcopilot.modules.sponsorship.repository.CompanySponsorshipRecordRepository;
import com.eurojobcopilot.modules.sponsorship.repository.OfficialSponsorRegistryEntryRepository;
import com.eurojobcopilot.modules.sponsorship.repository.SponsorshipRegistrySyncLogRepository;
import com.eurojobcopilot.modules.sponsorship.repository.SponsorshipVerificationHistoryRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SponsorshipControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private CompanySponsorshipRecordRepository companySponsorshipRecordRepository;

    @Autowired
    private OfficialSponsorRegistryEntryRepository officialRegistryRepository;

    @Autowired
    private SponsorshipVerificationHistoryRepository historyRepository;

    @Autowired
    private SponsorshipRegistrySyncLogRepository syncLogRepository;

    @Autowired
    private JobAiAnalysisRepository jobAiAnalysisRepository;

    @Autowired
    private JobMatchScoreRepository jobMatchScoreRepository;

    @Autowired
    private JobRepository jobRepository;

    @Autowired
    private CanonicalHashGenerator hashGenerator;

    @Autowired
    private DeduplicationService deduplicationService;

    @Autowired
    private JobNormalizer jobNormalizer;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @MockitoBean
    private IndRecognizedSponsorClient indClient;

    @BeforeEach
    @AfterEach
    void cleanDatabase() {
        historyRepository.deleteAllInBatch();
        jobMatchScoreRepository.deleteAllInBatch();
        jobAiAnalysisRepository.deleteAllInBatch();
        jobRepository.deleteAllInBatch();
        companySponsorshipRecordRepository.deleteAllInBatch();
        companyRepository.deleteAllInBatch();
        officialRegistryRepository.deleteAllInBatch();
        syncLogRepository.deleteAllInBatch();
        refreshTokenRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
    }

    @Test
    void shouldEnforceAuthenticationAndAdminRbacAcrossSponsorshipEndpoints() throws Exception {
        mockMvc.perform(get("/api/v1/sponsorship/countries"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/sponsorship/verify")
                        .queryParam("companyName", "Adyen")
                        .queryParam("country", "Netherlands"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/sponsorship/history"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/sponsorship/registry/ind"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/admin/sponsorship/ind/sync"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/admin/sponsorship/ind/status"))
                .andExpect(status().isUnauthorized());

        String userToken = registerStandardUser("rbac.user@eurojob.eu");

        mockMvc.perform(post("/api/v1/admin/sponsorship/ind/sync")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));

        mockMvc.perform(get("/api/v1/admin/sponsorship/ind/status")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldReturnAll14CountriesAndSyncIndRegisterCrossLinkingCompaniesAndJobs() throws Exception {
        String adminToken = createAndLoginAdmin("admin.ind@eurojob.eu");
        String userToken = registerStandardUser("candidate.ind@eurojob.eu");

        // Pre-seed unverified company record that matches an IND recognized sponsor by normalized name
        companyRepository.saveAndFlush(Company.builder()
                .name("Adyen N.V.")
                .normalizedName("adyen")
                .headquartersCountry("Netherlands")
                .verifiedSponsor(false)
                .sponsorConfidenceLevel(SponsorConfidenceLevel.UNVERIFIED)
                .build());

        when(indClient.fetchPublicRegister()).thenReturn(new IndRecognizedSponsorClient.FetchOutcome(
                RegistrySyncStatus.SUCCESS,
                List.of(
                        new IndRegisterParser.ParsedIndEntry("Adyen N.V.", "KVK-34259528"),
                        new IndRegisterParser.ParsedIndEntry("ASML Netherlands B.V.", "KVK-17085815")
                ),
                "https://ind.nl/en/public-register-recognised-sponsors",
                "Parsed 2 IND entries"
        ));

        mockMvc.perform(post("/api/v1/admin/sponsorship/ind/sync")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.entriesParsed").value(2))
                .andExpect(jsonPath("$.entriesUpserted").value(2))
                .andExpect(jsonPath("$.companiesLinked").value(1));

        mockMvc.perform(get("/api/v1/admin/sponsorship/ind/status")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.activeEntriesCount").value(2))
                .andExpect(jsonPath("$.companiesLinked").value(1));

        // Verify all 14 PROMPT 33 countries returned with official URLs and live IND count
        mockMvc.perform(get("/api/v1/sponsorship/countries")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(14))
                .andExpect(jsonPath("$[*].country", containsInAnyOrder(
                        "Germany", "Netherlands", "Luxembourg", "Switzerland",
                        "Ireland", "Austria", "Belgium", "Sweden",
                        "Denmark", "Finland", "France", "Poland",
                        "Portugal", "Spain"
                )))
                .andExpect(jsonPath("$[?(@.country=='Netherlands')].syncedEntriesCount", hasItem(2)))
                .andExpect(jsonPath("$[?(@.country=='Luxembourg')].isoCode", hasItem("LU")));

        // Verify IND searchable registry endpoint by company name and KvK ID
        mockMvc.perform(get("/api/v1/sponsorship/registry/ind")
                        .queryParam("query", "17085815")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].officialName").value("ASML Netherlands B.V."))
                .andExpect(jsonPath("$.content[0].registryIdentifier").value("KVK-17085815"))
                .andExpect(jsonPath("$.lastSyncStatus.status").value("SUCCESS"));

        // Verify job ingested via DeduplicationService auto-enriches against synced IND entry
        DeduplicationOutcome outcome = deduplicationService.upsertNormalizedJob(jobNormalizer.normalize(
                RawJobPosting.builder()
                        .sourceProvider("ARBEITNOW")
                        .sourceJobId("asml-nl-1")
                        .companyName("ASML Netherlands B.V.")
                        .title("Senior Embedded C++ Engineer")
                        .city("Veldhoven")
                        .country("Netherlands")
                        .workArrangementHint("hybrid")
                        .employmentTypeHint("full-time")
                        .rawDescription("Join EUV lithography platform software team in Veldhoven.")
                        .originalUrl("https://careers.asml.com/jobs/asml-nl-1")
                        .postedAt(Instant.now())
                        .build()
        ).orElseThrow());
        assertThat(outcome).isEqualTo(DeduplicationOutcome.INSERTED);

        Job ingestedJob = jobRepository.findBySourceProviderAndSourceJobId("ARBEITNOW", "asml-nl-1").orElseThrow();
        assertThat(ingestedJob.getSponsorStatus()).isEqualTo(JobSponsorStatus.VERIFIED_SPONSOR);

        // Verify /api/v1/jobs/{id} surfaces PROMPT 33 sponsorshipBreakdown fields cleanly
        mockMvc.perform(get("/api/v1/jobs/" + ingestedJob.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sponsorshipBreakdown.detailedStatus").value("VERIFIED_SPONSOR"))
                .andExpect(jsonPath("$.sponsorshipBreakdown.vacancyStatus").value("NOT_MENTIONED"))
                .andExpect(jsonPath("$.sponsorshipBreakdown.vacancySponsorshipConfirmed").value(false))
                .andExpect(jsonPath("$.sponsorshipBreakdown.vacancySponsorshipAssumed").value(false))
                .andExpect(jsonPath("$.sponsorshipBreakdown.vacancyCaveat").value(containsString("does NOT guarantee")))
                .andExpect(jsonPath("$.sponsorshipBreakdown.registryRecords[0].registrySource")
                        .value(CountrySponsorshipCatalog.NL_IND_REGISTRY_SOURCE));

        // Verify subsequent blocked IND sync logs BLOCKED status cleanly
        when(indClient.fetchPublicRegister()).thenReturn(new IndRecognizedSponsorClient.FetchOutcome(
                RegistrySyncStatus.BLOCKED,
                List.of(),
                "https://ind.nl/en/public-register-recognised-sponsors",
                "IND register returned HTTP 403; access unavailable."
        ));

        mockMvc.perform(post("/api/v1/admin/sponsorship/ind/sync")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("BLOCKED"))
                .andExpect(jsonPath("$.entriesParsed").value(0));
    }

    @Test
    void shouldVerifyAllSixStatusesEnforceVacancyNonAssumptionAndIsolateUserHistory() throws Exception {
        String adminToken = createAndLoginAdmin("admin.verify@eurojob.eu");
        String userAToken = registerStandardUser("alice.verify@eurojob.eu");
        String userBToken = registerStandardUser("bob.verify@eurojob.eu");

        when(indClient.fetchPublicRegister()).thenReturn(new IndRecognizedSponsorClient.FetchOutcome(
                RegistrySyncStatus.SUCCESS,
                List.of(new IndRegisterParser.ParsedIndEntry("Booking.com B.V.", "KVK-60754980")),
                "https://ind.nl/en/public-register-recognised-sponsors",
                "Parsed 1 entry"
        ));
        mockMvc.perform(post("/api/v1/admin/sponsorship/ind/sync")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk());

        // Seed a Booking.com vacancy where employer holds IND status, yet posting explicitly declines visa sponsorship
        Job decliningJob = jobRepository.saveAndFlush(Job.builder()
                .canonicalHash(hashGenerator.computeCanonicalHash("Booking.com", "Local Support Role", "Netherlands", "Amsterdam", WorkArrangement.ONSITE))
                .companyName("Booking.com B.V.")
                .title("Local Customer Operations Specialist")
                .normalizedTitle("local customer operations specialist")
                .country("Netherlands")
                .city("Amsterdam")
                .location("Amsterdam, Netherlands")
                .workArrangement(WorkArrangement.ONSITE)
                .employmentType(EmploymentType.FULL_TIME)
                .description("Local hire only. No visa sponsorship offered; valid EU work permit required.")
                .sourceProvider("ARBEITNOW")
                .sourceJobId("booking-declining-1")
                .originalUrl("https://jobs.booking.com/1")
                .sponsorStatus(JobSponsorStatus.VERIFIED_SPONSOR)
                .postedAt(Instant.now())
                .active(true)
                .build());

        jobAiAnalysisRepository.saveAndFlush(JobAiAnalysis.builder()
                .job(decliningJob)
                .requiredSkills(List.of("Operations"))
                .niceToHaveSkills(List.of())
                .responsibilities(List.of("Support local operations"))
                .benefits(List.of())
                .languageRequirements(List.of("English", "Dutch"))
                .sponsorshipOffered(false)
                .visaSponsorshipAnalysis("Existing EU permit explicitly required.")
                .aiModelUsed("gemini-2.5-flash")
                .build());

        // 1. VERIFIED_SPONSOR (IND match + neutral vacancy => non-assumption enforced!)
        mockMvc.perform(get("/api/v1/sponsorship/verify")
                        .queryParam("companyName", "Booking.com")
                        .queryParam("country", "NL")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("VERIFIED_SPONSOR"))
                .andExpect(jsonPath("$.vacancyStatus").value("NOT_MENTIONED"))
                .andExpect(jsonPath("$.vacancySponsorshipConfirmed").value(false))
                .andExpect(jsonPath("$.vacancySponsorshipAssumed").value(false))
                .andExpect(jsonPath("$.companyRecognizedSponsor").value(true))
                .andExpect(jsonPath("$.confidenceLevel").value("VERIFIED"))
                .andExpect(jsonPath("$.registryIdentifier").value("KVK-60754980"));

        // 2. UNAVAILABLE override on the IND sponsor's own job that declines sponsorship!
        mockMvc.perform(get("/api/v1/sponsorship/verify")
                        .queryParam("companyName", "Booking.com B.V.")
                        .queryParam("country", "Netherlands")
                        .queryParam("jobId", String.valueOf(decliningJob.getId()))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UNAVAILABLE"))
                .andExpect(jsonPath("$.vacancyStatus").value("UNAVAILABLE"))
                .andExpect(jsonPath("$.companyRecognizedSponsor").value(true))
                .andExpect(jsonPath("$.vacancySponsorshipConfirmed").value(false))
                .andExpect(jsonPath("$.vacancySponsorshipAssumed").value(false))
                .andExpect(jsonPath("$.confidenceLevel").value("LOW"))
                .andExpect(jsonPath("$.vacancyCaveat").value(containsString("never guarantees")));

        // 3. SPONSORSHIP_MENTIONED
        mockMvc.perform(get("/api/v1/sponsorship/verify")
                        .queryParam("companyName", "LuxTech S.A.R.L.")
                        .queryParam("country", "LU")
                        .queryParam("postingText", "Full EU Blue Card visa sponsorship provided for senior backend hires.")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.country").value("Luxembourg"))
                .andExpect(jsonPath("$.status").value("SPONSORSHIP_MENTIONED"))
                .andExpect(jsonPath("$.vacancySponsorshipConfirmed").value(true));

        // 4. RELOCATION_ASSISTANCE_MENTIONED
        mockMvc.perform(get("/api/v1/sponsorship/verify")
                        .queryParam("companyName", "Vienna Systems GmbH")
                        .queryParam("country", "Austria")
                        .queryParam("postingText", "Relocation allowance and housing support available for Vienna engineers.")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RELOCATION_ASSISTANCE_MENTIONED"))
                .andExpect(jsonPath("$.vacancySponsorshipConfirmed").value(false));

        // 5. POSSIBLE_UNCONFIRMED
        mockMvc.perform(get("/api/v1/sponsorship/verify")
                        .queryParam("companyName", "Zurich Cloud AG")
                        .queryParam("country", "Switzerland")
                        .queryParam("postingText", "English-speaking international team scaling cloud payments.")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("POSSIBLE_UNCONFIRMED"));

        // 6. NOT_MENTIONED (checked by User B for strict tenant isolation check)
        mockMvc.perform(get("/api/v1/sponsorship/verify")
                        .queryParam("companyName", "Lisbon Retail Lda")
                        .queryParam("country", "Portugal")
                        .queryParam("postingText", "Frontend React engineering role in Lisbon.")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userBToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("NOT_MENTIONED"));

        // Validation: unsupported country -> 400; missing jobId -> 404
        mockMvc.perform(get("/api/v1/sponsorship/verify")
                        .queryParam("companyName", "Acme Corp")
                        .queryParam("country", "United States")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userAToken))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/v1/sponsorship/verify")
                        .queryParam("companyName", "Booking.com")
                        .queryParam("country", "Netherlands")
                        .queryParam("jobId", "999999")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userAToken))
                .andExpect(status().isNotFound());

        // History isolation & filtering: User A sees only their 5 queries; User B sees only their 1 query
        mockMvc.perform(get("/api/v1/sponsorship/history")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(5));

        mockMvc.perform(get("/api/v1/sponsorship/history")
                        .queryParam("country", "NL")
                        .queryParam("companyName", "booking")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].status").value("UNAVAILABLE"))
                .andExpect(jsonPath("$.content[1].status").value("VERIFIED_SPONSOR"));

        mockMvc.perform(get("/api/v1/sponsorship/history")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userBToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].companyName").value("Lisbon Retail Lda"));
    }

    private String registerStandardUser(String email) throws Exception {
        RegisterRequest register = RegisterRequest.builder()
                .email(email)
                .password("SecureP@ssw0rd2026!")
                .firstName("Test")
                .lastName("Candidate")
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
