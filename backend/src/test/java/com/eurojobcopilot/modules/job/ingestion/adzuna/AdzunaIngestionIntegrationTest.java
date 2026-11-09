package com.eurojobcopilot.modules.job.ingestion.adzuna;

import com.eurojobcopilot.modules.job.entity.EmploymentType;
import com.eurojobcopilot.modules.job.entity.FetchStatus;
import com.eurojobcopilot.modules.job.entity.Job;
import com.eurojobcopilot.modules.job.entity.JobFetchLog;
import com.eurojobcopilot.modules.job.ingestion.IngestionExecutionSummary;
import com.eurojobcopilot.modules.job.ingestion.IngestionQuery;
import com.eurojobcopilot.modules.job.ingestion.JobIngestionProperties;
import com.eurojobcopilot.modules.job.ingestion.JobIngestionServiceImpl;
import com.eurojobcopilot.modules.job.ingestion.JobProvider;
import com.eurojobcopilot.modules.job.ingestion.arbeitnow.ArbeitnowJobProvider;
import com.eurojobcopilot.modules.job.normalization.DeduplicationService;
import com.eurojobcopilot.modules.job.normalization.JobNormalizer;
import com.eurojobcopilot.modules.job.repository.JobFetchLogRepository;
import com.eurojobcopilot.modules.job.repository.JobRepository;
import com.eurojobcopilot.modules.matching.repository.JobMatchScoreRepository;
import com.eurojobcopilot.modules.sponsorship.repository.CompanyRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@SpringBootTest
@ActiveProfiles("test")
class AdzunaIngestionIntegrationTest {

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
    void shouldRegisterAdzunaProviderDisabledInTestProfileWithoutCredentials() {
        assertThat(registeredProviders)
                .anySatisfy(provider -> {
                    assertThat(provider).isInstanceOf(AdzunaJobProvider.class);
                    assertThat(provider.getProviderName()).isEqualTo("ADZUNA");
                    assertThat(provider.isEnabled()).isFalse();
                });
    }

    @Test
    void shouldDeduplicateBetweenArbeitnowAndAdzunaAndEnrichSalariesAndIsolateFailures() {
        JobIngestionProperties testProps = new JobIngestionProperties();
        testProps.setEnabled(true);
        testProps.getArbeitnow().setEnabled(true);
        testProps.getArbeitnow().setBaseUrl("https://www.arbeitnow.com/api/job-board-api");
        testProps.getArbeitnow().setMaxPages(1);
        testProps.getAdzuna().setEnabled(true);
        testProps.getAdzuna().setBaseUrl("https://api.adzuna.com/v1/api/jobs");
        testProps.getAdzuna().setAppId("demo-id");
        testProps.getAdzuna().setAppKey("demo-key");
        testProps.getAdzuna().setCountryCodes(List.of("de"));
        testProps.getAdzuna().setResultsPerPage(20);
        testProps.getAdzuna().setMaxPagesPerCountry(1);

        RestClient.Builder arbeitnowBuilder = RestClient.builder();
        MockRestServiceServer arbeitnowServer = MockRestServiceServer.bindTo(arbeitnowBuilder).build();

        RestClient.Builder adzunaBuilder = RestClient.builder();
        MockRestServiceServer adzunaServer = MockRestServiceServer.bindTo(adzunaBuilder).build();

        arbeitnowServer.expect(once(), requestTo("https://www.arbeitnow.com/api/job-board-api?page=1"))
                .andRespond(withSuccess("""
                        {
                          "data": [
                            {
                              "slug": "sap-java-berlin-1",
                              "company_name": "SAP SE",
                              "title": "Senior Java Engineer (m/f/d)",
                              "description": "<p>Cloud ERP backend role.</p>",
                              "remote": false,
                              "url": "https://www.arbeitnow.com/jobs/sap/java-1",
                              "tags": ["Java", "Spring Boot"],
                              "job_types": ["Full Time"],
                              "location": "Berlin",
                              "created_at": 1762610000
                            }
                          ],
                          "links": { "next": null }
                        }
                        """, MediaType.APPLICATION_JSON));

        adzunaServer.expect(once(), requestTo(containsString("https://api.adzuna.com/v1/api/jobs/de/search/1")))
                .andRespond(withSuccess("""
                        {
                          "count": 2,
                          "results": [
                            {
                              "id": "adz-sap-1",
                              "title": "Senior Java Engineer",
                              "description": "Cloud ERP backend role in Berlin building scalable Spring Boot microservices with EU Blue Card visa sponsorship support and Kubernetes.",
                              "created": "2026-11-08T14:00:00Z",
                              "redirect_url": "https://www.adzuna.de/land/ad/adz-sap-1",
                              "salary_min": 85000,
                              "salary_max": 105000,
                              "contract_type": "permanent",
                              "contract_time": "full_time",
                              "company": { "display_name": "SAP" },
                              "location": {
                                "display_name": "Berlin, Germany",
                                "area": ["Deutschland", "Berlin"]
                              }
                            },
                            {
                              "id": "adz-parttime-2",
                              "title": "Frontend Angular Engineer",
                              "description": "Part-time frontend TypeScript support in Munich.",
                              "created": "2026-11-08T13:00:00Z",
                              "redirect_url": "https://www.adzuna.de/land/ad/adz-parttime-2",
                              "contract_time": "part_time",
                              "company": { "display_name": "Munich Labs GmbH" },
                              "location": {
                                "display_name": "Munich, Germany",
                                "area": ["Deutschland", "Bayern", "Munich"]
                              }
                            }
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));

        ArbeitnowJobProvider arbeitnowProvider = new ArbeitnowJobProvider(arbeitnowBuilder, testProps);
        AdzunaJobProvider adzunaProvider = new AdzunaJobProvider(adzunaBuilder, testProps);

        JobIngestionServiceImpl orchestrator = new JobIngestionServiceImpl(
                List.of(arbeitnowProvider, adzunaProvider),
                jobNormalizer,
                deduplicationService,
                jobFetchLogRepository,
                testProps
        );

        IngestionQuery query = IngestionQuery.builder()
                .countries(List.of("Germany"))
                .keywords(List.of("Java"))
                .maxPages(1)
                .build();

        IngestionExecutionSummary summary = orchestrator.ingestAll(query);

        arbeitnowServer.verify();
        adzunaServer.verify();

        // SAP inserted by Arbeitnow, enriched by Adzuna (updated=1); Munich Labs inserted new (total 2 jobs)
        assertThat(summary.providersExecuted()).isEqualTo(2);
        assertThat(summary.totalInserted()).isEqualTo(2);
        assertThat(summary.totalUpdated()).isEqualTo(1);
        assertThat(jobRepository.count()).isEqualTo(2);

        List<Job> jobs = jobRepository.findAll();
        assertThat(jobs)
                .anySatisfy(sap -> {
                    assertThat(sap.getCompanyName()).contains("SAP");
                    assertThat(sap.getSalaryMin()).isEqualByComparingTo("85000");
                    assertThat(sap.getSalaryMax()).isEqualByComparingTo("105000");
                    assertThat(sap.getSalaryCurrency()).isEqualTo("EUR");
                    assertThat(sap.getSkills()).contains("Java", "Spring Boot", "Kubernetes");
                })
                .anySatisfy(munich -> {
                    assertThat(munich.getCity()).isEqualTo("Munich");
                    assertThat(munich.getEmploymentType()).isEqualTo(EmploymentType.PART_TIME);
                });

        // Verify fault isolation if Adzuna errors out on next poll
        arbeitnowServer.reset();
        adzunaServer.reset();
        arbeitnowServer.expect(once(), requestTo("https://www.arbeitnow.com/api/job-board-api?page=1"))
                .andRespond(withSuccess("{\"data\":[]}", MediaType.APPLICATION_JSON));
        adzunaServer.expect(once(), requestTo(containsString("https://api.adzuna.com/v1/api/jobs/de/search/1")))
                .andRespond(withServerError());

        IngestionExecutionSummary faultSummary = orchestrator.ingestAll(query);
        assertThat(faultSummary.providerResults())
                .anySatisfy(r -> {
                    assertThat(r.providerName()).isEqualTo("ARBEITNOW");
                    assertThat(r.status()).isEqualTo(FetchStatus.SUCCESS);
                })
                .anySatisfy(r -> {
                    assertThat(r.providerName()).isEqualTo("ADZUNA");
                    assertThat(r.status()).isEqualTo(FetchStatus.FAILED);
                });

        List<JobFetchLog> logs = jobFetchLogRepository.findAll();
        assertThat(logs).hasSize(4);
    }
}

