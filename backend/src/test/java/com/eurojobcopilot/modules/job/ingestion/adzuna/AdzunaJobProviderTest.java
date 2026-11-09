package com.eurojobcopilot.modules.job.ingestion.adzuna;

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
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class AdzunaJobProviderTest {

    private static final String BASE_URL = "https://api.adzuna.com/v1/api/jobs";

    private JobIngestionProperties properties;
    private RestClient.Builder restClientBuilder;
    private MockRestServiceServer mockServer;
    private AdzunaJobProvider provider;

    @BeforeEach
    void setUp() {
        properties = new JobIngestionProperties();
        properties.setEnabled(true);
        properties.setMaxResultsPerProvider(100);
        properties.getAdzuna().setEnabled(true);
        properties.getAdzuna().setBaseUrl(BASE_URL);
        properties.getAdzuna().setAppId("test-app-id");
        properties.getAdzuna().setAppKey("test-app-key");
        properties.getAdzuna().setCountryCodes(List.of("de", "gb", "ch"));
        properties.getAdzuna().setResultsPerPage(2);
        properties.getAdzuna().setMaxPagesPerCountry(2);

        restClientBuilder = RestClient.builder();
        mockServer = MockRestServiceServer.bindTo(restClientBuilder).build();
        provider = new AdzunaJobProvider(restClientBuilder, properties);
    }

    @Test
    void shouldPollMultipleCountriesPaginateAndMapSalariesAndCities() {
        mockServer.expect(once(), requestTo(containsString(BASE_URL + "/de/search/1")))
                .andExpect(method(HttpMethod.GET))
                .andExpect(queryParam("app_id", "test-app-id"))
                .andExpect(queryParam("app_key", "test-app-key"))
                .andExpect(queryParam("results_per_page", "2"))
                .andExpect(queryParam("what_or", "Java%20Spring"))
                .andRespond(withSuccess("""
                        {
                          "count": 3,
                          "results": [
                            {
                              "id": "de-1",
                              "title": "Senior Java Developer",
                              "description": "Hybrid enterprise Java & Spring Boot role with EU Blue Card support.",
                              "created": "2026-11-08T10:00:00Z",
                              "redirect_url": "https://www.adzuna.de/land/ad/de-1",
                              "salary_min": 80000,
                              "salary_max": 100000,
                              "contract_type": "permanent",
                              "contract_time": "full_time",
                              "company": { "display_name": "SAP SE" },
                              "location": {
                                "display_name": "Berlin, Germany",
                                "area": ["Deutschland", "Berlin"]
                              }
                            },
                            {
                              "id": "de-2",
                              "title": "Frontend Angular Consultant",
                              "description": "Part time frontend role in Munich.",
                              "created": "2026-11-08T09:00:00Z",
                              "redirect_url": "https://www.adzuna.de/land/ad/de-2",
                              "salary_min": 65000,
                              "salary_max": 75000,
                              "contract_type": "contract",
                              "contract_time": "part_time",
                              "company": { "display_name": "Allianz Tech" },
                              "location": {
                                "display_name": "Munich, Bayern",
                                "area": ["Deutschland", "Bayern", "Munich"]
                              }
                            }
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));

        // Page 2 for de returns < perPage items so pagination ends cleanly after page 2
        mockServer.expect(once(), requestTo(containsString(BASE_URL + "/de/search/2")))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {
                          "count": 2,
                          "results": []
                        }
                        """, MediaType.APPLICATION_JSON));

        mockServer.expect(once(), requestTo(containsString(BASE_URL + "/gb/search/1")))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {
                          "count": 1,
                          "results": [
                            {
                              "id": "gb-1",
                              "title": "Remote Cloud Engineer",
                              "description": "Remote UK engineering role.",
                              "created": "2026-11-08T08:00:00Z",
                              "redirect_url": "https://www.adzuna.co.uk/land/ad/gb-1",
                              "salary_min": 70000,
                              "salary_max": 85000,
                              "company": { "display_name": "Monzo Bank" },
                              "location": { "display_name": "London, UK" }
                            }
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));

        IngestionQuery query = IngestionQuery.builder()
                .countries(List.of("Germany", "UK"))
                .keywords(List.of("Java", "Spring"))
                .maxPages(2)
                .build();

        List<RawJobPosting> items = provider.fetchJobs(query);
        mockServer.verify();

        assertThat(items).hasSize(3);
        RawJobPosting deFirst = items.get(0);
        assertThat(deFirst.sourceProvider()).isEqualTo("ADZUNA");
        assertThat(deFirst.country()).isEqualTo("Germany");
        assertThat(deFirst.city()).isEqualTo("Berlin");
        assertThat(deFirst.salaryCurrency()).isEqualTo("EUR");
        assertThat(deFirst.salaryMin()).isEqualByComparingTo("80000");

        RawJobPosting deSecond = items.get(1);
        assertThat(deSecond.city()).isEqualTo("Munich");
        assertThat(deSecond.employmentTypeHint()).isEqualTo("contract, part_time");

        RawJobPosting gbFirst = items.get(2);
        assertThat(gbFirst.country()).isEqualTo("United Kingdom");
        assertThat(gbFirst.salaryCurrency()).isEqualTo("GBP");
        assertThat(gbFirst.city()).isEqualTo("London");
        assertThat(gbFirst.workArrangementHint()).isEqualTo("REMOTE");
    }

    @Test
    void shouldRequireCredentialsCheckSupportsCountryAndWrapHttpErrors() {
        assertThat(provider.isEnabled()).isTrue();
        assertThat(provider.supportsCountry("Germany")).isTrue();
        assertThat(provider.supportsCountry("UK")).isTrue();
        assertThat(provider.supportsCountry("Netherlands")).isFalse();

        properties.getAdzuna().setAppKey("");
        assertThat(provider.isEnabled()).isFalse();
        properties.getAdzuna().setAppKey("test-app-key");

        mockServer.expect(once(), requestTo(containsString(BASE_URL + "/ch/search/1")))
                .andExpect(queryParam("max_days_old", "2"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        IngestionQuery query = IngestionQuery.builder()
                .countries(List.of("Switzerland"))
                .since(Instant.now().minus(30, ChronoUnit.HOURS))
                .maxPages(1)
                .build();

        assertThatThrownBy(() -> provider.fetchJobs(query))
                .isInstanceOf(JobProviderException.class)
                .hasMessageContaining("Adzuna API fetch failed for country=ch page=1");
        mockServer.verify();
    }

    @Test
    void shouldFilterRemoteOnlyRespectSinceCutoffAndSkipUnconfiguredCountries() {
        Instant cutoff = Instant.parse("2026-11-08T09:00:00Z");

        mockServer.expect(once(), requestTo(containsString(BASE_URL + "/de/search/1")))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {
                          "count": 3,
                          "results": [
                            {
                              "id": "de-remote-new",
                              "title": "Remote Staff Java Engineer",
                              "description": "100% remote across Germany.",
                              "created": "2026-11-08T12:00:00Z",
                              "redirect_url": "https://www.adzuna.de/land/ad/de-remote-new",
                              "company": { "display_name": "RemoteCo" },
                              "location": { "display_name": "Germany", "area": ["Deutschland"] }
                            },
                            {
                              "id": "de-onsite-new",
                              "title": "Onsite Java Engineer",
                              "description": "Office role in Berlin.",
                              "created": "2026-11-08T11:00:00Z",
                              "redirect_url": "https://www.adzuna.de/land/ad/de-onsite-new",
                              "company": { "display_name": "BerlinCo" },
                              "location": { "display_name": "Berlin, Germany" }
                            },
                            {
                              "id": "de-remote-stale",
                              "title": "Remote Old Java Engineer",
                              "description": "Older than watermark.",
                              "created": "2026-11-07T08:00:00Z",
                              "redirect_url": "https://www.adzuna.de/land/ad/de-remote-stale",
                              "company": { "display_name": "OldCo" },
                              "location": { "display_name": "Berlin, Germany" }
                            }
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));

        // Netherlands is not in configured countryCodes ("de", "gb", "ch"), and page 2 for "de" must be skipped once stale items are reached
        IngestionQuery query = IngestionQuery.builder()
                .countries(List.of("Netherlands", "Germany"))
                .remoteOnly(true)
                .since(cutoff)
                .maxPages(2)
                .build();

        List<RawJobPosting> items = provider.fetchJobs(query);
        mockServer.verify();

        assertThat(items).hasSize(1);
        assertThat(items.getFirst().sourceJobId()).isEqualTo("de-remote-new");
        assertThat(items.getFirst().city()).isNull();
        assertThat(items.getFirst().country()).isEqualTo("Germany");
    }
}

