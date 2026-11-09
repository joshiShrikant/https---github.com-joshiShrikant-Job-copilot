package com.eurojobcopilot.modules.analytics.controller;

import com.eurojobcopilot.core.enums.WorkArrangement;
import com.eurojobcopilot.modules.auth.dto.RegisterRequest;
import com.eurojobcopilot.modules.auth.repository.RefreshTokenRepository;
import com.eurojobcopilot.modules.auth.repository.UserRepository;
import com.eurojobcopilot.modules.job.entity.FetchStatus;
import com.eurojobcopilot.modules.job.entity.Job;
import com.eurojobcopilot.modules.job.entity.JobFetchLog;
import com.eurojobcopilot.modules.job.entity.JobSponsorStatus;
import com.eurojobcopilot.modules.job.entity.SalaryPeriod;
import com.eurojobcopilot.modules.job.repository.JobFetchLogRepository;
import com.eurojobcopilot.modules.job.repository.JobRepository;
import com.eurojobcopilot.modules.matching.repository.JobMatchScoreRepository;
import com.eurojobcopilot.modules.sponsorship.entity.Company;
import com.eurojobcopilot.modules.sponsorship.repository.CompanyRepository;
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

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MarketAnalyticsControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private JobFetchLogRepository jobFetchLogRepository;
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

    private String token;

    @BeforeEach
    void setUp() throws Exception {
        cleanDatabase();
        RegisterRequest reg = RegisterRequest.builder()
                .email("analytics.user@eurojob.eu")
                .password("SecureP@ssw0rd2026!")
                .firstName("Lukas")
                .lastName("Mueller")
                .build();
        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reg)))
                .andExpect(status().isCreated())
                .andReturn();
        token = objectMapper.readTree(result.getResponse().getContentAsString()).get("accessToken").asText();

        Company nlSponsor = companyRepository.saveAndFlush(
                TestEntityFactory.createCompany("Adyen Analytics NL", "Netherlands", true));
        Company deEmployer = companyRepository.saveAndFlush(
                TestEntityFactory.createCompany("Berlin Cloud GmbH", "Germany", false));

        Instant now = Instant.now();
        jobRepository.saveAllAndFlush(List.of(
                Job.builder()
                        .canonicalHash(TestEntityFactory.sha256Hex("analytics-nl-1"))
                        .company(nlSponsor)
                        .companyName(nlSponsor.getName())
                        .title("Senior Full-Stack Java & Angular Engineer")
                        .country("Netherlands")
                        .city("Amsterdam")
                        .location("Amsterdam, Netherlands")
                        .workArrangement(WorkArrangement.HYBRID)
                        .salaryMin(new BigDecimal("90000.00"))
                        .salaryMax(new BigDecimal("110000.00"))
                        .salaryCurrency("EUR")
                        .salaryPeriod(SalaryPeriod.YEARLY)
                        .skills(List.of("Java", "Spring Boot", "Angular", "TypeScript"))
                        .description("English speaking international team with visa sponsorship.")
                        .sponsorStatus(JobSponsorStatus.VERIFIED_SPONSOR)
                        .sourceProvider("ARBEITNOW")
                        .originalUrl("https://jobs.example.eu/a1")
                        .postedAt(now.minus(1, ChronoUnit.DAYS))
                        .active(true)
                        .build(),
                Job.builder()
                        .canonicalHash(TestEntityFactory.sha256Hex("analytics-nl-2"))
                        .company(nlSponsor)
                        .companyName(nlSponsor.getName())
                        .title("Staff Frontend Angular Architect")
                        .country("Netherlands")
                        .city("Amsterdam")
                        .location("Amsterdam, Netherlands")
                        .workArrangement(WorkArrangement.REMOTE)
                        .salaryMin(new BigDecimal("80000.00"))
                        .salaryMax(new BigDecimal("100000.00"))
                        .salaryCurrency("EUR")
                        .salaryPeriod(SalaryPeriod.YEARLY)
                        .skills(List.of("Angular", "RxJS", "TypeScript"))
                        .description("Remote EU team working language: English.")
                        .sponsorStatus(JobSponsorStatus.SPONSORSHIP_MENTIONED)
                        .sourceProvider("ADZUNA")
                        .originalUrl("https://jobs.example.eu/a2")
                        .postedAt(now.minus(2, ChronoUnit.DAYS))
                        .active(true)
                        .build(),
                Job.builder()
                        .canonicalHash(TestEntityFactory.sha256Hex("analytics-de-1"))
                        .company(deEmployer)
                        .companyName(deEmployer.getName())
                        .title("Java Backend Developer")
                        .country("Germany")
                        .city("Berlin")
                        .location("Berlin, Germany")
                        .workArrangement(WorkArrangement.ONSITE)
                        .salaryMin(new BigDecimal("70000.00"))
                        .salaryMax(new BigDecimal("80000.00"))
                        .salaryCurrency("EUR")
                        .salaryPeriod(SalaryPeriod.YEARLY)
                        .skills(List.of("Java", "Spring Boot", "PostgreSQL"))
                        .description("Fluent German required for banking compliance workflows.")
                        .sponsorStatus(JobSponsorStatus.UNCONFIRMED)
                        .sourceProvider("ARBEITNOW")
                        .originalUrl("https://jobs.example.eu/a3")
                        .postedAt(now.minus(3, ChronoUnit.DAYS))
                        .active(true)
                        .build(),
                Job.builder()
                        .canonicalHash(TestEntityFactory.sha256Hex("analytics-inactive"))
                        .company(deEmployer)
                        .companyName(deEmployer.getName())
                        .title("Expired Role")
                        .country("Germany")
                        .city("Munich")
                        .location("Munich, Germany")
                        .description("Expired Java engineering posting kept inactive.")
                        .sourceProvider("ARBEITNOW")
                        .originalUrl("https://jobs.example.eu/a4")
                        .postedAt(now.minus(5, ChronoUnit.DAYS))
                        .active(false)
                        .build()
        ));

        jobFetchLogRepository.saveAndFlush(JobFetchLog.builder()
                .providerName("ARBEITNOW")
                .status(FetchStatus.SUCCESS)
                .jobsFetched(2)
                .jobsInserted(2)
                .fetchedAt(now.minus(1, ChronoUnit.HOURS))
                .build());
    }

    @AfterEach
    void cleanDatabase() {
        jobFetchLogRepository.deleteAllInBatch();
        jobMatchScoreRepository.deleteAllInBatch();
        jobRepository.deleteAllInBatch();
        companyRepository.deleteAllInBatch();
        refreshTokenRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
    }

    @Test
    void marketInsights_requiresAuth_andValidatesDaysWindow() throws Exception {
        mockMvc.perform(get("/api/v1/analytics/market-insights"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/v1/analytics/market-insights?days=3")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void marketInsights_computesMultiCountryMetricsTrendsCoverageAndHonestCaveat() throws Exception {
        mockMvc.perform(get("/api/v1/analytics/market-insights?days=14")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.trendWindowDays").value(14))
                .andExpect(jsonPath("$.lastDataRefreshAt").isNotEmpty())
                .andExpect(jsonPath("$.dataCoverageDisclaimer", containsString("collected via EuroJob Copilot")))
                .andExpect(jsonPath("$.availableCountries", containsInAnyOrder("Netherlands", "Germany")))
                .andExpect(jsonPath("$.totals.totalActiveJobs").value(3))
                .andExpect(jsonPath("$.totals.angularJobs").value(2))
                .andExpect(jsonPath("$.totals.frontendJobs").value(2))
                .andExpect(jsonPath("$.totals.javaJobs").value(2))
                .andExpect(jsonPath("$.totals.fullStackJobs").value(1))
                .andExpect(jsonPath("$.totals.sponsorshipOpportunities").value(2))
                .andExpect(jsonPath("$.totals.verifiedSponsorJobs").value(1))
                .andExpect(jsonPath("$.totals.englishConfirmedJobs").value(2))
                .andExpect(jsonPath("$.totals.localLanguageRequiredJobs").value(1))
                .andExpect(jsonPath("$.discoveryTrends.length()").value(14))
                .andExpect(jsonPath("$.countryComparisons.length()").value(2))
                .andExpect(jsonPath("$.countryComparisons[0].country").value("Netherlands"))
                .andExpect(jsonPath("$.countryComparisons[0].salaryMedian").value(95000.0))
                .andExpect(jsonPath("$.sourceCoverage.length()").value(2));

        // Country comparison filter scopes totals & countryComparisons without hiding availableCountries
        mockMvc.perform(get("/api/v1/analytics/market-insights?countries=Netherlands")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.availableCountries.length()").value(2))
                .andExpect(jsonPath("$.selectedCountries.length()").value(1))
                .andExpect(jsonPath("$.totals.totalActiveJobs").value(2))
                .andExpect(jsonPath("$.countryComparisons[0].country").value("Netherlands"));
    }
}
