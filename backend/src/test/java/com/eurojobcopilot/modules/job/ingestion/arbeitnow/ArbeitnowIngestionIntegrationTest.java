package com.eurojobcopilot.modules.job.ingestion.arbeitnow;

import com.eurojobcopilot.modules.job.entity.FetchStatus;
import com.eurojobcopilot.modules.job.entity.Job;
import com.eurojobcopilot.modules.job.entity.JobFetchLog;
import com.eurojobcopilot.modules.job.entity.JobSponsorStatus;
import com.eurojobcopilot.modules.job.ingestion.IngestionExecutionSummary;
import com.eurojobcopilot.modules.job.ingestion.IngestionQuery;
import com.eurojobcopilot.modules.job.ingestion.JobIngestionProperties;
import com.eurojobcopilot.modules.job.ingestion.JobIngestionServiceImpl;
import com.eurojobcopilot.modules.job.ingestion.JobProvider;
import com.eurojobcopilot.modules.job.normalization.DeduplicationService;
import com.eurojobcopilot.modules.job.normalization.JobNormalizer;
import com.eurojobcopilot.modules.job.repository.JobFetchLogRepository;
import com.eurojobcopilot.modules.job.repository.JobRepository;
import com.eurojobcopilot.modules.matching.repository.JobMatchScoreRepository;
import com.eurojobcopilot.modules.sponsorship.entity.Company;
import com.eurojobcopilot.modules.sponsorship.entity.SponsorConfidenceLevel;
import com.eurojobcopilot.modules.sponsorship.repository.CompanyRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@SpringBootTest
@ActiveProfiles("test")
class ArbeitnowIngestionIntegrationTest {

    @Autowired
    private List<JobProvider> registeredProviders;

    @Autowired
    private JobNormalizer jobNormalizer;

    @Autowired
    private DeduplicationService deduplicationService;

    @Autowired
    private JobMatchScoreRepository jobMatchScoreRepository;

    @Autowired
    private JobRepository jobRepository;

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private JobFetchLogRepository jobFetchLogRepository;

    @BeforeEach
    @AfterEach
    void cleanDatabase() {
        jobFetchLogRepository.deleteAll();
        jobMatchScoreRepository.deleteAllInBatch();
        jobRepository.deleteAll();
        companyRepository.deleteAll();
    }

    @Test
    void shouldRegisterArbeitnowBeanDisabledInTestProfile() {
        assertThat(registeredProviders)
                .anySatisfy(provider -> {
                    assertThat(provider).isInstanceOf(ArbeitnowJobProvider.class);
                    assertThat(provider.getProviderName()).isEqualTo("ARBEITNOW");
                    assertThat(provider.isEnabled()).isFalse();
                });
    }

    @Test
    void shouldIngestNormalizeDeduplicateAndAuditArbeitnowJobsAgainstDatabase() {
        companyRepository.save(Company.builder()
                .name("Booking.com B.V.")
                .normalizedName("booking com")
                .headquartersCountry("Netherlands")
                .verifiedSponsor(true)
                .sponsorConfidenceLevel(SponsorConfidenceLevel.VERIFIED)
                .build());

        JobIngestionProperties testProps = new JobIngestionProperties();
        testProps.setEnabled(true);
        testProps.getArbeitnow().setEnabled(true);
        testProps.getArbeitnow().setBaseUrl("https://www.arbeitnow.com/api/job-board-api");
        testProps.getArbeitnow().setMaxPages(2);

        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer mockServer = MockRestServiceServer.bindTo(builder).build();

        String pageOneJson = """
                {
                  "data": [
                    {
                      "slug": "berlin-java-201",
                      "company_name": "Trade Republic Bank GmbH",
                      "title": "Senior Java Backend Engineer (m/f/d)",
                      "description": "<p>Build core brokerage microservices with Java 21, Spring Boot, Kafka &amp; PostgreSQL.</p>",
                      "remote": false,
                      "url": "https://www.arbeitnow.com/jobs/trade-republic/java-201",
                      "tags": ["Java", "Kafka"],
                      "job_types": ["Full Time"],
                      "location": "Berlin",
                      "created_at": 1762605000
                    },
                    {
                      "slug": "amsterdam-angular-202",
                      "company_name": "Booking.com",
                      "title": "Staff Frontend Engineer",
                      "description": "<p>Hybrid Angular architecture role in Amsterdam.</p>",
                      "remote": false,
                      "url": "https://www.arbeitnow.com/jobs/booking/angular-202",
                      "tags": ["Angular", "TypeScript"],
                      "job_types": ["Full Time"],
                      "location": "Amsterdam, Netherlands",
                      "created_at": 1762606000
                    }
                  ],
                  "links": { "next": null }
                }
                """;

        mockServer.expect(once(), requestTo("https://www.arbeitnow.com/api/job-board-api?page=1"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(pageOneJson, MediaType.APPLICATION_JSON));

        // Second poll returns identical payload to verify idempotent duplicate skipping
        mockServer.expect(once(), requestTo("https://www.arbeitnow.com/api/job-board-api?page=1"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(pageOneJson, MediaType.APPLICATION_JSON));

        ArbeitnowJobProvider arbeitnowProvider = new ArbeitnowJobProvider(builder, testProps);
        JobIngestionServiceImpl ingestionService = new JobIngestionServiceImpl(
                List.of(arbeitnowProvider),
                jobNormalizer,
                deduplicationService,
                jobFetchLogRepository,
                testProps
        );

        IngestionQuery query = IngestionQuery.builder().maxPages(2).build();
        IngestionExecutionSummary firstSummary = ingestionService.ingestAll(query);
        assertThat(firstSummary.totalInserted()).isEqualTo(2);
        assertThat(firstSummary.totalSkippedDuplicates()).isZero();

        List<Job> stored = jobRepository.findAll();
        assertThat(stored).hasSize(2);
        assertThat(stored)
                .anySatisfy(job -> {
                    assertThat(job.getSourceProvider()).isEqualTo("ARBEITNOW");
                    assertThat(job.getCity()).isEqualTo("Berlin");
                    assertThat(job.getCountry()).isEqualTo("Germany");
                    assertThat(job.getSkills()).contains("Java", "Spring Boot", "Kafka", "PostgreSQL");
                })
                .anySatisfy(job -> {
                    assertThat(job.getCompanyName()).isEqualTo("Booking.com");
                    assertThat(job.getSponsorStatus()).isEqualTo(JobSponsorStatus.VERIFIED_SPONSOR);
                });

        IngestionExecutionSummary secondSummary = ingestionService.ingestAll(query);
        assertThat(secondSummary.totalInserted()).isZero();
        assertThat(secondSummary.totalUpdated()).isZero();
        assertThat(secondSummary.totalSkippedDuplicates()).isEqualTo(2);
        assertThat(jobRepository.count()).isEqualTo(2);

        // Reset mock server before exercising upstream failure path
        mockServer.verify();
        mockServer.reset();

        // Verify upstream 500 gets captured cleanly as FetchStatus.FAILED in JobFetchLog
        mockServer.expect(once(), requestTo("https://www.arbeitnow.com/api/job-board-api?page=1"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withServerError());

        IngestionExecutionSummary errorSummary = ingestionService.ingestAll(query);
        assertThat(errorSummary.providerResults()).hasSize(1);
        assertThat(errorSummary.providerResults().getFirst().status()).isEqualTo(FetchStatus.FAILED);

        List<JobFetchLog> logs = jobFetchLogRepository.findAll();
        assertThat(logs).hasSize(3);
        assertThat(logs.getLast().getStatus()).isEqualTo(FetchStatus.FAILED);
        assertThat(logs.getLast().getErrorMessage()).contains("Arbeitnow API fetch failed on page 1");

        mockServer.verify();
    }
}

