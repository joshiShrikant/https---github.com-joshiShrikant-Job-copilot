package com.eurojobcopilot.modules.job.ingestion.arbeitnow;

import com.eurojobcopilot.modules.job.ingestion.IngestionQuery;
import com.eurojobcopilot.modules.job.ingestion.JobIngestionProperties;
import com.eurojobcopilot.modules.job.ingestion.JobProviderException;
import com.eurojobcopilot.modules.job.ingestion.RawJobPosting;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class ArbeitnowJobProviderTest {

    private static final String BASE_URL = "https://www.arbeitnow.com/api/job-board-api";

    private JobIngestionProperties properties;
    private RestClient.Builder restClientBuilder;
    private MockRestServiceServer mockServer;
    private ArbeitnowJobProvider provider;

    @BeforeEach
    void setUp() {
        properties = new JobIngestionProperties();
        properties.setEnabled(true);
        properties.setMaxResultsPerProvider(50);
        properties.getArbeitnow().setEnabled(true);
        properties.getArbeitnow().setBaseUrl(BASE_URL);
        properties.getArbeitnow().setMaxPages(3);

        restClientBuilder = RestClient.builder();
        mockServer = MockRestServiceServer.bindTo(restClientBuilder).build();
        provider = new ArbeitnowJobProvider(restClientBuilder, properties);
    }

    @Test
    void shouldPaginateUntilNextLinkIsNullAndMapArbeitnowPayloads() {
        mockServer.expect(once(), requestTo(BASE_URL + "?page=1"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {
                          "data": [
                            {
                              "slug": "senior-java-berlin-101",
                              "company_name": "FinLeap GmbH",
                              "title": "Senior Java Engineer (m/f/d)",
                              "description": "<p>Build banking systems in Berlin with visa sponsorship.</p>",
                              "remote": false,
                              "url": "https://www.arbeitnow.com/jobs/finleap/senior-java",
                              "tags": ["Java", "Spring Boot", "Visa Sponsorship"],
                              "job_types": ["Full Time"],
                              "location": "Berlin",
                              "created_at": 1762600000
                            }
                          ],
                          "links": {
                            "next": "https://www.arbeitnow.com/api/job-board-api?page=2"
                          }
                        }
                        """, MediaType.APPLICATION_JSON));

        mockServer.expect(once(), requestTo(BASE_URL + "?page=2"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {
                          "data": [
                            {
                              "slug": "angular-amsterdam-102",
                              "company_name": "PayNL B.V.",
                              "title": "Frontend Angular Developer",
                              "description": "<p>Remote friendly EU TypeScript role.</p>",
                              "remote": true,
                              "url": "https://www.arbeitnow.com/jobs/paynl/angular",
                              "tags": ["Angular", "TypeScript"],
                              "job_types": ["Contract"],
                              "location": "Amsterdam, Netherlands",
                              "created_at": "2026-11-08T10:15:00Z"
                            }
                          ],
                          "links": {
                            "next": null
                          }
                        }
                        """, MediaType.APPLICATION_JSON));

        List<RawJobPosting> results = provider.fetchJobs(IngestionQuery.builder().maxPages(3).build());
        mockServer.verify();

        assertThat(results).hasSize(2);

        RawJobPosting first = results.get(0);
        assertThat(first.sourceProvider()).isEqualTo("ARBEITNOW");
        assertThat(first.sourceJobId()).isEqualTo("senior-java-berlin-101");
        assertThat(first.companyName()).isEqualTo("FinLeap GmbH");
        assertThat(first.remoteFlag()).isFalse();
        assertThat(first.visaSponsoredHint()).isTrue();
        assertThat(first.employmentTypeHint()).isEqualTo("Full Time");
        assertThat(first.postedAt()).isEqualTo(Instant.ofEpochSecond(1762600000L));

        RawJobPosting second = results.get(1);
        assertThat(second.remoteFlag()).isTrue();
        assertThat(second.workArrangementHint()).isEqualTo("REMOTE");
        assertThat(second.postedAt()).isEqualTo(Instant.parse("2026-11-08T10:15:00Z"));
    }

    @Test
    void shouldFilterRemoteOnlyAndHonorSinceWatermark() {
        mockServer.expect(once(), requestTo(BASE_URL + "?page=1"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {
                          "data": [
                            {
                              "slug": "remote-new-1",
                              "company_name": "RemoteCo",
                              "title": "Staff Java Dev",
                              "description": "Remote role",
                              "remote": true,
                              "url": "https://www.arbeitnow.com/jobs/1",
                              "tags": ["Java"],
                              "job_types": ["Full Time"],
                              "location": "Berlin, Germany",
                              "created_at": "2026-11-08T12:00:00Z"
                            },
                            {
                              "slug": "onsite-new-2",
                              "company_name": "OfficeCo",
                              "title": "Onsite Dev",
                              "description": "Office role",
                              "remote": false,
                              "url": "https://www.arbeitnow.com/jobs/2",
                              "tags": ["Java"],
                              "job_types": ["Full Time"],
                              "location": "Munich",
                              "created_at": "2026-11-08T11:00:00Z"
                            },
                            {
                              "slug": "stale-role-3",
                              "company_name": "OldCo",
                              "title": "Legacy Dev",
                              "description": "Old role",
                              "remote": true,
                              "url": "https://www.arbeitnow.com/jobs/3",
                              "tags": ["Java"],
                              "job_types": ["Full Time"],
                              "location": "Berlin",
                              "created_at": "2026-11-01T00:00:00Z"
                            }
                          ],
                          "links": {
                            "next": "https://www.arbeitnow.com/api/job-board-api?page=2"
                          }
                        }
                        """, MediaType.APPLICATION_JSON));

        IngestionQuery query = IngestionQuery.builder()
                .maxPages(3)
                .remoteOnly(true)
                .since(Instant.parse("2026-11-05T00:00:00Z"))
                .build();

        List<RawJobPosting> results = provider.fetchJobs(query);
        mockServer.verify();

        assertThat(results).hasSize(1);
        assertThat(results.getFirst().sourceJobId()).isEqualTo("remote-new-1");
    }

    @Test
    void shouldWrapUpstreamHttpFailuresAndReflectEnablementFlags() {
        assertThat(provider.isEnabled()).isTrue();
        properties.getArbeitnow().setEnabled(false);
        assertThat(provider.isEnabled()).isFalse();
        properties.getArbeitnow().setEnabled(true);
        properties.setEnabled(false);
        assertThat(provider.isEnabled()).isFalse();

        mockServer.expect(once(), requestTo(BASE_URL + "?page=1"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

        assertThatThrownBy(() -> provider.fetchJobs(IngestionQuery.builder().maxPages(1).build()))
                .isInstanceOf(JobProviderException.class)
                .hasMessageContaining("Arbeitnow API fetch failed on page 1");
        mockServer.verify();
    }
}

