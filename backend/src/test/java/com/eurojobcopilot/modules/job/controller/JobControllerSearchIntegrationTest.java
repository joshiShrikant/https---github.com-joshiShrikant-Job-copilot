package com.eurojobcopilot.modules.job.controller;

import com.eurojobcopilot.core.enums.WorkArrangement;
import com.eurojobcopilot.modules.auth.dto.RegisterRequest;
import com.eurojobcopilot.modules.auth.entity.User;
import com.eurojobcopilot.modules.auth.repository.RefreshTokenRepository;
import com.eurojobcopilot.modules.auth.repository.UserRepository;
import com.eurojobcopilot.modules.job.entity.EmploymentType;
import com.eurojobcopilot.modules.job.entity.ExperienceLevel;
import com.eurojobcopilot.modules.job.entity.Job;
import com.eurojobcopilot.modules.job.entity.JobSponsorStatus;
import com.eurojobcopilot.modules.job.entity.SalaryPeriod;
import com.eurojobcopilot.modules.job.entity.UserJobInteraction;
import com.eurojobcopilot.modules.job.repository.JobRepository;
import com.eurojobcopilot.modules.job.repository.UserJobInteractionRepository;
import com.eurojobcopilot.modules.matching.entity.JobMatchScore;
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

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class JobControllerSearchIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JobMatchScoreRepository jobMatchScoreRepository;

    @Autowired
    private UserJobInteractionRepository userJobInteractionRepository;

    @Autowired
    private JobRepository jobRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private UserRepository userRepository;

    private String tokenA;
    private String tokenB;
    private User userA;

    private Job jobAdyenNetherlands;
    private Job jobPersonioGermany;
    private Job jobKlarnaSweden;
    private Job jobBerlinMinSalaryOnly;
    private Job jobHiddenByUserA;
    private Job jobInactive;
    private Job jobExpired;

    @BeforeEach
    void setUp() throws Exception {
        cleanDatabase();

        tokenA = registerUser("search.a@eurojob.eu", "Lukas", "Mueller");
        tokenB = registerUser("search.b@eurojob.eu", "Elena", "Weber");
        userA = userRepository.findByEmailIgnoreCase("search.a@eurojob.eu").orElseThrow();

        Instant now = Instant.now();

        jobAdyenNetherlands = jobRepository.saveAndFlush(Job.builder()
                .canonicalHash(TestEntityFactory.sha256Hex("adyen-nl-1"))
                .companyName("Adyen Engineering")
                .title("Senior Java & Spring Boot Engineer")
                .normalizedTitle("senior java spring boot engineer")
                .location("Amsterdam, Netherlands")
                .city("Amsterdam")
                .country("Netherlands")
                .workArrangement(WorkArrangement.HYBRID)
                .employmentType(EmploymentType.FULL_TIME)
                .experienceLevel(ExperienceLevel.SENIOR)
                .salaryMin(new BigDecimal("85000.00"))
                .salaryMax(new BigDecimal("110000.00"))
                .salaryCurrency("EUR")
                .salaryPeriod(SalaryPeriod.YEARLY)
                .description("Build 100% compliant payment microservices with IND visa sponsorship support.")
                .descriptionSnippet("Build 100% compliant payment microservices with IND visa sponsorship support.")
                .skills(List.of("Java", "Spring Boot", "Kafka"))
                .sourceProvider("ARBEITNOW")
                .originalUrl("https://jobs.example.eu/adyen-1")
                .sponsorStatus(JobSponsorStatus.VERIFIED_SPONSOR)
                .postedAt(now.minus(2, ChronoUnit.HOURS))
                .active(true)
                .build());

        jobPersonioGermany = jobRepository.saveAndFlush(Job.builder()
                .canonicalHash(TestEntityFactory.sha256Hex("personio-de-2"))
                .companyName("Personio Tech")
                .title("Fullstack Angular & Java Architect")
                .normalizedTitle("fullstack angular java architect")
                .location("Munich, Germany")
                .city("Munich")
                .country("Germany")
                .workArrangement(WorkArrangement.HYBRID)
                .employmentType(EmploymentType.FULL_TIME)
                .experienceLevel(ExperienceLevel.LEAD)
                .salaryMin(new BigDecimal("95000.00"))
                .salaryMax(new BigDecimal("130000.00"))
                .salaryCurrency("EUR")
                .salaryPeriod(SalaryPeriod.YEARLY)
                .description("Design modern HR platform workflows with EU Blue Card relocation package. "
                        + "x".repeat(600)
                        + " DeepArchiveKafkaMesh")
                .descriptionSnippet("Design modern HR platform workflows with EU Blue Card relocation package.")
                .skills(List.of("Angular", "TypeScript", "Java"))
                .sourceProvider("ADZUNA")
                .originalUrl("https://jobs.example.eu/personio-2")
                .sponsorStatus(JobSponsorStatus.RELOCATION_OFFERED)
                .postedAt(now.minus(6, ChronoUnit.HOURS))
                .active(true)
                .build());

        jobKlarnaSweden = jobRepository.saveAndFlush(Job.builder()
                .canonicalHash(TestEntityFactory.sha256Hex("klarna-se-3"))
                .companyName("Klarna Platform")
                .title("Kotlin Cloud Contractor")
                .normalizedTitle("kotlin cloud contractor")
                .location("Stockholm, Sweden")
                .city("Stockholm")
                .country("Sweden")
                .workArrangement(WorkArrangement.REMOTE)
                .employmentType(EmploymentType.CONTRACT)
                .experienceLevel(ExperienceLevel.MID)
                .salaryMin(null)
                .salaryMax(null)
                .salaryCurrency(null)
                .salaryPeriod(SalaryPeriod.YEARLY)
                .description("Scale distributed ledger pipelines with visa sponsorship available.")
                .descriptionSnippet("Scale distributed ledger pipelines with visa sponsorship available.")
                .skills(List.of("Kotlin", "AWS", "Terraform"))
                .sourceProvider("ARBEITNOW")
                .originalUrl("https://jobs.example.eu/klarna-3")
                .sponsorStatus(JobSponsorStatus.SPONSORSHIP_MENTIONED)
                .postedAt(now.minus(12, ChronoUnit.HOURS))
                .active(true)
                .build());

        jobBerlinMinSalaryOnly = jobRepository.saveAndFlush(Job.builder()
                .canonicalHash(TestEntityFactory.sha256Hex("berlin-de-4"))
                .companyName("FinTech Berlin GmbH")
                .title("Backend Go Developer")
                .normalizedTitle("backend go developer")
                .location("Berlin, Germany")
                .city("Berlin")
                .country("Germany")
                .workArrangement(WorkArrangement.ONSITE)
                .employmentType(EmploymentType.PART_TIME)
                .experienceLevel(ExperienceLevel.JUNIOR)
                .salaryMin(new BigDecimal("78000.00"))
                .salaryMax(null)
                .salaryCurrency("EUR")
                .salaryPeriod(SalaryPeriod.YEARLY)
                .description("Local Berlin product team building accounting integrations.")
                .descriptionSnippet("Local Berlin product team building accounting integrations.")
                .skills(List.of("Go", "PostgreSQL"))
                .sourceProvider("ADZUNA")
                .originalUrl("https://jobs.example.eu/berlin-4")
                .sponsorStatus(JobSponsorStatus.UNCONFIRMED)
                .postedAt(now.minus(1, ChronoUnit.DAYS))
                .active(true)
                .build());

        jobHiddenByUserA = jobRepository.saveAndFlush(Job.builder()
                .canonicalHash(TestEntityFactory.sha256Hex("hidden-nl-5"))
                .companyName("Booking.com")
                .title("Frontend Engineer Hidden For A")
                .normalizedTitle("frontend engineer hidden for a")
                .location("Amsterdam, Netherlands")
                .city("Amsterdam")
                .country("Netherlands")
                .workArrangement(WorkArrangement.HYBRID)
                .employmentType(EmploymentType.FULL_TIME)
                .experienceLevel(ExperienceLevel.MID)
                .salaryMin(new BigDecimal("70000.00"))
                .salaryMax(new BigDecimal("88000.00"))
                .salaryCurrency("EUR")
                .salaryPeriod(SalaryPeriod.YEARLY)
                .description("Travel platform engineering team in Amsterdam.")
                .descriptionSnippet("Travel platform engineering team in Amsterdam.")
                .skills(List.of("Angular", "TypeScript"))
                .sourceProvider("ARBEITNOW")
                .originalUrl("https://jobs.example.eu/hidden-5")
                .sponsorStatus(JobSponsorStatus.VERIFIED_SPONSOR)
                .postedAt(now.minus(30, ChronoUnit.MINUTES))
                .active(true)
                .build());

        jobInactive = jobRepository.saveAndFlush(Job.builder()
                .canonicalHash(TestEntityFactory.sha256Hex("inactive-6"))
                .companyName("Old Corp")
                .title("Archived Inactive Role")
                .normalizedTitle("archived inactive role")
                .country("Germany")
                .description("Should never appear in search results.")
                .sourceProvider("ARBEITNOW")
                .originalUrl("https://jobs.example.eu/inactive-6")
                .sponsorStatus(JobSponsorStatus.VERIFIED_SPONSOR)
                .postedAt(now.minus(10, ChronoUnit.MINUTES))
                .active(false)
                .build());

        jobExpired = jobRepository.saveAndFlush(Job.builder()
                .canonicalHash(TestEntityFactory.sha256Hex("expired-7"))
                .companyName("Expired Corp")
                .title("Expired Role")
                .normalizedTitle("expired role")
                .country("Germany")
                .description("Already expired listing.")
                .sourceProvider("ADZUNA")
                .originalUrl("https://jobs.example.eu/expired-7")
                .sponsorStatus(JobSponsorStatus.VERIFIED_SPONSOR)
                .postedAt(now.minus(5, ChronoUnit.DAYS))
                .expiresAt(now.minus(1, ChronoUnit.HOURS))
                .active(true)
                .build());

        userJobInteractionRepository.saveAndFlush(UserJobInteraction.builder()
                .user(userA)
                .job(jobAdyenNetherlands)
                .saved(true)
                .savedAt(now)
                .interested(false)
                .hidden(false)
                .build());

        userJobInteractionRepository.saveAndFlush(UserJobInteraction.builder()
                .user(userA)
                .job(jobPersonioGermany)
                .saved(false)
                .interested(true)
                .interestedAt(now)
                .hidden(false)
                .build());

        userJobInteractionRepository.saveAndFlush(UserJobInteraction.builder()
                .user(userA)
                .job(jobHiddenByUserA)
                .saved(false)
                .interested(false)
                .hidden(true)
                .hiddenAt(now)
                .build());

        jobMatchScoreRepository.saveAndFlush(JobMatchScore.builder()
                .user(userA)
                .job(jobKlarnaSweden)
                .finalScore(new BigDecimal("94.60"))
                .deterministicScore(new BigDecimal("94.60"))
                .build());

        jobMatchScoreRepository.saveAndFlush(JobMatchScore.builder()
                .user(userA)
                .job(jobAdyenNetherlands)
                .finalScore(new BigDecimal("81.40"))
                .deterministicScore(new BigDecimal("81.40"))
                .build());

        jobMatchScoreRepository.saveAndFlush(JobMatchScore.builder()
                .user(userA)
                .job(jobPersonioGermany)
                .finalScore(new BigDecimal("55.00"))
                .deterministicScore(new BigDecimal("55.00"))
                .build());
    }

    @AfterEach
    void cleanDatabase() {
        jobMatchScoreRepository.deleteAllInBatch();
        userJobInteractionRepository.deleteAllInBatch();
        jobRepository.deleteAllInBatch();
        refreshTokenRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
    }

    @Test
    void searchJobs_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/jobs"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void searchJobs_invalidQueryParameters_return400ApiErrorResponse() throws Exception {
        mockMvc.perform(get("/api/v1/jobs")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA)
                        .param("sort", "unknownField,desc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").exists());

        mockMvc.perform(get("/api/v1/jobs")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA)
                        .param("size", "150"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        mockMvc.perform(get("/api/v1/jobs")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA)
                        .param("minMatchScore", "105"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        mockMvc.perform(get("/api/v1/jobs")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA)
                        .param("workArrangement", "INVALID_MODE"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void searchJobs_excludesInactiveExpiredAndHiddenJobsWithStrictTenantIsolation() throws Exception {
        // User A: 4 visible jobs (hidden, inactive, and expired excluded); enriched with A's interactions and match scores
        MvcResult resultA = mockMvc.perform(get("/api/v1/jobs")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(4))
                .andExpect(jsonPath("$.isFirst").value(true))
                .andExpect(jsonPath("$.isLast").value(true))
                .andExpect(jsonPath("$.hasNext").value(false))
                .andExpect(jsonPath("$.hasPrevious").value(false))
                .andExpect(jsonPath("$.content[*].id", contains(
                        jobAdyenNetherlands.getId().intValue(),
                        jobPersonioGermany.getId().intValue(),
                        jobKlarnaSweden.getId().intValue(),
                        jobBerlinMinSalaryOnly.getId().intValue()
                )))
                .andExpect(jsonPath("$.content[0].matchPercentage").value(81))
                .andExpect(jsonPath("$.content[0].isSaved").value(true))
                .andExpect(jsonPath("$.content[0].isInterested").value(false))
                .andExpect(jsonPath("$.content[0].isHidden").value(false))
                .andExpect(jsonPath("$.content[1].matchPercentage").value(55))
                .andExpect(jsonPath("$.content[1].isSaved").value(false))
                .andExpect(jsonPath("$.content[1].isInterested").value(true))
                .andExpect(jsonPath("$.content[2].matchPercentage").value(95))
                .andExpect(jsonPath("$.content[3].matchPercentage").isEmpty())
                .andReturn();

        JsonNode jsonA = objectMapper.readTree(resultA.getResponse().getContentAsString());
        assertThat(jsonA.has("first")).isFalse();
        assertThat(jsonA.has("last")).isFalse();
        JsonNode firstJobJson = jsonA.path("content").get(0);
        assertThat(firstJobJson.has("saved")).isFalse();
        assertThat(firstJobJson.has("interested")).isFalse();
        assertThat(firstJobJson.has("hidden")).isFalse();
        assertThat(firstJobJson.has("isSaved")).isTrue();
        assertThat(firstJobJson.has("isInterested")).isTrue();
        assertThat(firstJobJson.has("isHidden")).isTrue();

        // When includeHidden=true, User A sees all 5 active non-expired jobs with jobHiddenByUserA flagged as isHidden=true
        mockMvc.perform(get("/api/v1/jobs")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA)
                        .param("includeHidden", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(5))
                .andExpect(jsonPath("$.content[0].id").value(jobHiddenByUserA.getId()))
                .andExpect(jsonPath("$.content[0].isHidden").value(true));

        // User B still sees jobHiddenByUserA because only User A hid it, and receives zero leaked interactions/scores
        mockMvc.perform(get("/api/v1/jobs")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(5))
                .andExpect(jsonPath("$.content[0].id").value(jobHiddenByUserA.getId()))
                .andExpect(jsonPath("$.content[0].isHidden").value(false))
                .andExpect(jsonPath("$.content[1].id").value(jobAdyenNetherlands.getId()))
                .andExpect(jsonPath("$.content[1].isSaved").value(false))
                .andExpect(jsonPath("$.content[1].matchPercentage").isEmpty());
    }

    @Test
    void searchJobs_multiFacetFiltersAndLiteralWildcardEscapingWorkTogether() throws Exception {
        // Deep body description match beyond 500-char snippet boundary
        mockMvc.perform(get("/api/v1/jobs")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA)
                        .param("query", "DeepArchiveKafkaMesh"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(jobPersonioGermany.getId()));

        // No results yields clean empty PageResponse metadata
        mockMvc.perform(get("/api/v1/jobs")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA)
                        .param("query", "NoSuchTechnologyXYZ"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.totalPages").value(0))
                .andExpect(jsonPath("$.content.length()").value(0))
                .andExpect(jsonPath("$.isFirst").value(true))
                .andExpect(jsonPath("$.isLast").value(true))
                .andExpect(jsonPath("$.hasNext").value(false))
                .andExpect(jsonPath("$.hasPrevious").value(false));

        // Multi-word query + literal '%' wildcard escaping across description
        mockMvc.perform(get("/api/v1/jobs")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA)
                        .param("query", "100% Amsterdam"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(jobAdyenNetherlands.getId()));

        // Country ISO alias ('DE' -> Germany) + workArrangement + sponsorStatus
        mockMvc.perform(get("/api/v1/jobs")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA)
                        .param("country", "DE")
                        .param("workArrangement", "HYBRID")
                        .param("sponsorStatus", "RELOCATION_OFFERED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(jobPersonioGermany.getId()));

        // City partial match + employmentType + minSalary fallback on salaryMin when salaryMax is null
        mockMvc.perform(get("/api/v1/jobs")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA)
                        .param("city", "berl")
                        .param("employmentType", "PART_TIME")
                        .param("experienceLevel", "JUNIOR")
                        .param("minSalary", "75000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(jobBerlinMinSalaryOnly.getId()));

        // minSalary excludes null-salary jobs (Klarna has null min/max, Berlin has 78k, Adyen 110k, Personio 130k)
        mockMvc.perform(get("/api/v1/jobs")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA)
                        .param("minSalary", "100000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));

        // minMatchScore=70 returns only Adyen (81.4) and Klarna (94.6) for User A
        mockMvc.perform(get("/api/v1/jobs")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA)
                        .param("minMatchScore", "70"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[*].id", contains(
                        jobAdyenNetherlands.getId().intValue(),
                        jobKlarnaSweden.getId().intValue()
                )));

        // workArrangement=ANY is treated as non-restrictive filter
        mockMvc.perform(get("/api/v1/jobs")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA)
                        .param("workArrangement", "ANY"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(4));

        // savedOnly and interestedOnly filter strictly per user interaction state
        mockMvc.perform(get("/api/v1/jobs")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA)
                        .param("savedOnly", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(jobAdyenNetherlands.getId()));

        mockMvc.perform(get("/api/v1/jobs")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA)
                        .param("interestedOnly", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(jobPersonioGermany.getId()));
    }

    @Test
    void searchJobs_sortingAndPaginationMetadataAreDeterministic() throws Exception {
        // matchScore,desc produces distinct order: Klarna (94.6) -> Adyen (81.4) -> Personio (55.0) -> Berlin (unscored)
        mockMvc.perform(get("/api/v1/jobs")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA)
                        .param("sort", "matchScore,desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].id", contains(
                        jobKlarnaSweden.getId().intValue(),
                        jobAdyenNetherlands.getId().intValue(),
                        jobPersonioGermany.getId().intValue(),
                        jobBerlinMinSalaryOnly.getId().intValue()
                )));

        // salaryMax,desc puts 130k (Personio), 110k (Adyen), then nulls ordered by postedAt desc (Klarna, Berlin)
        mockMvc.perform(get("/api/v1/jobs")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA)
                        .param("sort", "salaryMax,desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].id", contains(
                        jobPersonioGermany.getId().intValue(),
                        jobAdyenNetherlands.getId().intValue(),
                        jobKlarnaSweden.getId().intValue(),
                        jobBerlinMinSalaryOnly.getId().intValue()
                )));

        // postedAt,asc reverses chronological order
        mockMvc.perform(get("/api/v1/jobs")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA)
                        .param("sort", "postedAt,asc")
                        .param("page", "0")
                        .param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pageNumber").value(0))
                .andExpect(jsonPath("$.pageSize").value(2))
                .andExpect(jsonPath("$.totalElements").value(4))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.isFirst").value(true))
                .andExpect(jsonPath("$.isLast").value(false))
                .andExpect(jsonPath("$.hasNext").value(true))
                .andExpect(jsonPath("$.hasPrevious").value(false))
                .andExpect(jsonPath("$.content[*].id", contains(
                        jobBerlinMinSalaryOnly.getId().intValue(),
                        jobKlarnaSweden.getId().intValue()
                )));

        mockMvc.perform(get("/api/v1/jobs")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA)
                        .param("sort", "postedAt,asc")
                        .param("page", "1")
                        .param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pageNumber").value(1))
                .andExpect(jsonPath("$.pageSize").value(2))
                .andExpect(jsonPath("$.totalElements").value(4))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.isFirst").value(false))
                .andExpect(jsonPath("$.isLast").value(true))
                .andExpect(jsonPath("$.hasNext").value(false))
                .andExpect(jsonPath("$.hasPrevious").value(true))
                .andExpect(jsonPath("$.content[*].id", contains(
                        jobPersonioGermany.getId().intValue(),
                        jobAdyenNetherlands.getId().intValue()
                )));

        // Out-of-range page safely returns empty content with accurate totals
        mockMvc.perform(get("/api/v1/jobs")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA)
                        .param("page", "5")
                        .param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(4))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.content.length()").value(0))
                .andExpect(jsonPath("$.hasNext").value(false))
                .andExpect(jsonPath("$.hasPrevious").value(true));
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
