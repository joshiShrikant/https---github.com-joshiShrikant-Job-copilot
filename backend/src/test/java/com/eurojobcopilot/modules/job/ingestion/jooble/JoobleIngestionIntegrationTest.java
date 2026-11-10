package com.eurojobcopilot.modules.job.ingestion.jooble;

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
class JoobleIngestionIntegrationTest {

    @Autowired
    private List<JobProvider> registeredProviders;

    @Autowired
    private JobIngestionProperties jobIngestionProperties;

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
    void shouldRegisterJoobleProviderDisabledInTestProfileWithoutApiKey() {
        assertThat(jobIngestionProperties.getJooble().getLocations()).isEmpty();
        assertThat(jobIngestionProperties.getJooble().getCountryCodes())
                .containsExactly("de", "nl", "gb", "ch");
        assertThat(registeredProviders)
                .anySatisfy(provider -> {
                    assertThat(provider).isInstanceOf(JoobleJobProvider.class);
                    assertThat(provider.getProviderName()).isEqualTo("JOOBLE");
                    assertThat(provider.isEnabled()).isFalse();
                });
    }

    @Test
    void shouldIngestNormalizeSwissAndGermanPostingsAndIsolateJoobleFailures() {
        JobIngestionProperties testProps = new JobIngestionProperties();
        testProps.setEnabled(true);
        testProps.getArbeitnow().setEnabled(true);
        testProps.getArbeitnow().setBaseUrl("https://www.arbeitnow.com/api/job-board-api");
        testProps.getArbeitnow().setMaxPages(1);
        testProps.getJooble().setEnabled(true);
        testProps.getJooble().setBaseUrl("https://jooble.org/api");
        testProps.getJooble().setApiKey("test-jooble-key");
        testProps.getJooble().setCountryCodes(List.of("ch"));
        testProps.getJooble().setLocations(List.of("Bern"));
        testProps.getJooble().setResultsPerPage(20);
        testProps.getJooble().setMaxPagesPerLocation(1);

        RestClient.Builder arbeitnowBuilder = RestClient.builder();
        MockRestServiceServer arbeitnowServer = MockRestServiceServer.bindTo(arbeitnowBuilder).build();

        RestClient.Builder joobleBuilder = RestClient.builder();
        MockRestServiceServer joobleServer = MockRestServiceServer.bindTo(joobleBuilder).build();

        arbeitnowServer.expect(once(), requestTo("https://www.arbeitnow.com/api/job-board-api?page=1"))
                .andRespond(withSuccess("""
                        {
                          "data": [
                            {
                              "slug": "swisscom-java-bern-1",
                              "company_name": "Swisscom AG",
                              "title": "Senior Java Engineer (m/f/d)",
                              "description": "<p>Core telecom APIs in Bern.</p>",
                              "remote": false,
                              "url": "https://www.arbeitnow.com/jobs/swisscom/java-1",
                              "tags": ["Java"],
                              "job_types": ["Full Time"],
                              "location": "Bern",
                              "created_at": 1762610000
                            }
                          ],
                          "links": { "next": null }
                        }
                        """, MediaType.APPLICATION_JSON));

        joobleServer.expect(once(), requestTo("https://jooble.org/api/test-jooble-key"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("""
                        {
                          "totalCount": 2,
                          "jobs": [
                            {
                              "id": -944391186811165858,
                              "title": "Senior Java Engineer",
                              "location": "Bern",
                              "snippet": "&nbsp;Core telecom APIs in Bern building <b>Spring Boot</b> and Kubernetes systems with EU Blue Card support.",
                              "salary": "CHF 115'000 - 135'000",
                              "type": "Full-time",
                              "link": "https://ch.jooble.org/jdp/-944391186811165858",
                              "company": "Swisscom AG",
                              "updated": "2026-11-08T12:00:00.0000000"
                            },
                            {
                              "id": 3852750075475181710,
                              "title": "Angular Frontend Lead",
                              "location": "Zurich, Switzerland",
                              "snippet": "TypeScript, Angular, RxJS architecture.",
                              "salary": "",
                              "type": "Part-time",
                              "link": "https://ch.jooble.org/jdp/3852750075475181710",
                              "company": "Helvetia Digital",
                              "updated": "2026-11-08T10:30:00.0000000"
                            }
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));

        ArbeitnowJobProvider arbeitnowProvider = new ArbeitnowJobProvider(arbeitnowBuilder, testProps);
        JoobleJobProvider joobleProvider = new JoobleJobProvider(joobleBuilder, testProps);

        JobIngestionServiceImpl orchestrator = new JobIngestionServiceImpl(
                List.of(arbeitnowProvider, joobleProvider),
                jobNormalizer,
                deduplicationService,
                jobFetchLogRepository,
                testProps
        );

        IngestionQuery query = IngestionQuery.builder()
                .countries(List.of("Switzerland"))
                .keywords(List.of("Java", "Angular"))
                .maxPages(1)
                .build();

        IngestionExecutionSummary summary = orchestrator.ingestAll(query);

        arbeitnowServer.verify();
        joobleServer.verify();

        // Bern job from Arbeitnow normalized to Switzerland and enriched with CHF salary + skills by Jooble
        assertThat(summary.providersExecuted()).isEqualTo(2);
        assertThat(summary.totalInserted()).isEqualTo(2);
        assertThat(summary.totalUpdated()).isEqualTo(1);
        assertThat(jobRepository.count()).isEqualTo(2);

        List<Job> jobs = jobRepository.findAll();
        assertThat(jobs)
                .anySatisfy(swisscom -> {
                    assertThat(swisscom.getCity()).isEqualTo("Bern");
                    assertThat(swisscom.getCountry()).isEqualTo("Switzerland");
                    assertThat(swisscom.getSalaryMin()).isEqualByComparingTo("115000");
                    assertThat(swisscom.getSalaryMax()).isEqualByComparingTo("135000");
                    assertThat(swisscom.getSalaryCurrency()).isEqualTo("CHF");
                    assertThat(swisscom.getSkills()).contains("Java", "Spring Boot", "Kubernetes");
                })
                .anySatisfy(helvetia -> {
                    assertThat(helvetia.getCity()).isEqualTo("Zurich");
                    assertThat(helvetia.getCountry()).isEqualTo("Switzerland");
                    assertThat(helvetia.getEmploymentType()).isEqualTo(EmploymentType.PART_TIME);
                    assertThat(helvetia.getSkills()).contains("Angular", "TypeScript", "RxJS");
                });

        // Verify fault isolation when Jooble fails
        arbeitnowServer.reset();
        joobleServer.reset();
        arbeitnowServer.expect(once(), requestTo("https://www.arbeitnow.com/api/job-board-api?page=1"))
                .andRespond(withSuccess("{\"data\":[]}", MediaType.APPLICATION_JSON));
        joobleServer.expect(once(), requestTo("https://jooble.org/api/test-jooble-key"))
                .andRespond(withServerError());

        IngestionExecutionSummary faultSummary = orchestrator.ingestAll(query);
        assertThat(faultSummary.providerResults())
                .anySatisfy(r -> {
                    assertThat(r.providerName()).isEqualTo("ARBEITNOW");
                    assertThat(r.status()).isEqualTo(FetchStatus.SUCCESS);
                })
                .anySatisfy(r -> {
                    assertThat(r.providerName()).isEqualTo("JOOBLE");
                    assertThat(r.status()).isEqualTo(FetchStatus.FAILED);
                    assertThat(r.errorMessage()).doesNotContain("test-jooble-key");
                });

        List<JobFetchLog> logs = jobFetchLogRepository.findAll();
        assertThat(logs).hasSize(4);
    }
}
