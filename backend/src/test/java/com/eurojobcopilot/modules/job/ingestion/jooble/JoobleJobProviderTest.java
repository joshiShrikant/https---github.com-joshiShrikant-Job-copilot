package com.eurojobcopilot.modules.job.ingestion.jooble;

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
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class JoobleJobProviderTest {

    private static final String BASE_URL = "https://jooble.org/api/";
    private static final String TEST_KEY = "test-jooble-secret-key";

    private JobIngestionProperties properties;
    private MockRestServiceServer mockServer;
    private JoobleJobProvider provider;

    @BeforeEach
    void setUp() {
        properties = new JobIngestionProperties();
        properties.setEnabled(true);
        properties.setMaxResultsPerProvider(100);
        properties.getJooble().setEnabled(true);
        properties.getJooble().setBaseUrl(BASE_URL);
        properties.getJooble().setApiKey(TEST_KEY);
        properties.getJooble().setCountryCodes(List.of("de", "ch"));
        properties.getJooble().setLocations(List.of());
        properties.getJooble().setResultsPerPage(2);
        properties.getJooble().setMaxPagesPerLocation(2);

        RestClient.Builder restClientBuilder = RestClient.builder();
        mockServer = MockRestServiceServer.bindTo(restClientBuilder).build();
        provider = new JoobleJobProvider(restClientBuilder, properties);
    }

    @Test
    void shouldPostSearchRequestsPaginateAndParseIdsSalariesLocationsAndTimestamps() {
        mockServer.expect(once(), requestTo("https://jooble.org/api/" + TEST_KEY))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.keywords").value("Java OR \"Spring Boot\""))
                .andExpect(jsonPath("$.location").value("Germany"))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.ResultOnPage").value(2))
                .andRespond(withSuccess("""
                        {
                          "totalCount": 3,
                          "jobs": [
                            {
                              "id": -944391186811165858,
                              "title": "Senior Java Engineer",
                              "location": "Berlin, Germany",
                              "snippet": "Hybrid <b>Java</b> &amp; Spring Boot role in Berlin.",
                              "salary": "80.000 - 95.000 €",
                              "source": "example.de",
                              "type": "Full-time",
                              "link": "https://jooble.org/jdp/-944391186811165858",
                              "company": "SAP SE",
                              "updated": "2026-10-28T10:53:23.1800000"
                            },
                            {
                              "id": 7442403187016420224,
                              "title": "Remote Backend Developer",
                              "location": "Germany",
                              "snippet": "100% remote backend microservices.",
                              "salary": "6,500 EUR / month",
                              "type": "Contract",
                              "link": "https://jooble.org/jdp/7442403187016420224",
                              "company": "2four7va",
                              "updated": "2026-10-30T08:30:56Z"
                            }
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));

        mockServer.expect(once(), requestTo("https://jooble.org/api/" + TEST_KEY))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.location").value("Germany"))
                .andExpect(jsonPath("$.page").value(2))
                .andRespond(withSuccess("""
                        {
                          "totalCount": 3,
                          "jobs": [
                            {
                              "id": "str-de-3",
                              "title": "Cloud Architect",
                              "location": "Munich",
                              "snippet": "Cloud role in Munich.",
                              "salary": "Negotiable",
                              "link": "https://jooble.org/jdp/str-de-3",
                              "company": "Munich Cloud GmbH",
                              "updated": "2026-11-01"
                            }
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));

        mockServer.expect(once(), requestTo("https://jooble.org/api/" + TEST_KEY))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.location").value("Switzerland"))
                .andExpect(jsonPath("$.page").value(1))
                .andRespond(withSuccess("""
                        {
                          "totalCount": 1,
                          "jobs": [
                            {
                              "id": 100200300,
                              "title": "Platform Engineer",
                              "location": "Bern",
                              "snippet": "Swiss banking platform role.",
                              "salary": "CHF 110'000 - 130'000",
                              "type": "Permanent",
                              "link": "https://ch.jooble.org/jdp/100200300",
                              "company": "Swisscom AG",
                              "updated": "2026-11-08T14:00:00+01:00"
                            }
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));

        IngestionQuery query = IngestionQuery.builder()
                .countries(List.of("Germany", "Switzerland"))
                .keywords(List.of("Java", "Spring Boot"))
                .maxPages(2)
                .build();

        List<RawJobPosting> items = provider.fetchJobs(query);
        mockServer.verify();

        assertThat(items).hasSize(4);

        RawJobPosting first = items.get(0);
        assertThat(first.sourceProvider()).isEqualTo("JOOBLE");
        assertThat(first.sourceJobId()).isEqualTo("-944391186811165858");
        assertThat(first.country()).isEqualTo("Germany");
        assertThat(first.city()).isEqualTo("Berlin");
        assertThat(first.workArrangementHint()).isEqualTo("HYBRID");
        assertThat(first.salaryMin()).isEqualByComparingTo("80000");
        assertThat(first.salaryMax()).isEqualByComparingTo("95000");
        assertThat(first.salaryCurrency()).isEqualTo("EUR");
        assertThat(first.salaryPeriodHint()).isEqualTo("YEARLY");
        assertThat(first.postedAt()).isEqualTo(Instant.parse("2026-10-28T10:53:23.180Z"));

        RawJobPosting second = items.get(1);
        assertThat(second.city()).isNull();
        assertThat(second.country()).isEqualTo("Germany");
        assertThat(second.workArrangementHint()).isEqualTo("REMOTE");
        assertThat(second.salaryMin()).isEqualByComparingTo("6500");
        assertThat(second.salaryMax()).isNull();
        assertThat(second.salaryCurrency()).isEqualTo("EUR");
        assertThat(second.salaryPeriodHint()).isEqualTo("MONTHLY");

        RawJobPosting third = items.get(2);
        assertThat(third.sourceJobId()).isEqualTo("str-de-3");
        assertThat(third.salaryMin()).isNull();
        assertThat(third.salaryCurrency()).isNull();
        assertThat(third.postedAt()).isEqualTo(Instant.parse("2026-11-01T00:00:00Z"));

        RawJobPosting fourth = items.get(3);
        assertThat(fourth.country()).isEqualTo("Switzerland");
        assertThat(fourth.city()).isEqualTo("Bern");
        assertThat(fourth.salaryMin()).isEqualByComparingTo("110000");
        assertThat(fourth.salaryMax()).isEqualByComparingTo("130000");
        assertThat(fourth.salaryCurrency()).isEqualTo("CHF");
        assertThat(fourth.postedAt()).isEqualTo(Instant.parse("2026-11-08T13:00:00Z"));
    }

    @Test
    void shouldRequireApiKeySupportExplicitLocationsAndRedactSecretOnHttpFailure() {
        assertThat(provider.isEnabled()).isTrue();
        assertThat(provider.supportsCountry("Germany")).isTrue();
        assertThat(provider.supportsCountry("Switzerland")).isTrue();
        assertThat(provider.supportsCountry("Spain")).isFalse();

        properties.getJooble().setApiKey("");
        assertThat(provider.isEnabled()).isFalse();
        properties.getJooble().setApiKey(TEST_KEY);
        properties.getJooble().setLocations(List.of("Bern"));
        properties.getJooble().setDefaultKeywords("it");

        mockServer.expect(once(), requestTo("https://jooble.org/api/" + TEST_KEY))
                .andExpect(jsonPath("$.keywords").value("it"))
                .andExpect(jsonPath("$.location").value("Bern"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED)
                        .body("Unauthorized call to https://jooble.org/api/" + TEST_KEY));

        IngestionQuery query = IngestionQuery.builder()
                .countries(List.of("Switzerland"))
                .maxPages(1)
                .build();

        assertThatThrownBy(() -> provider.fetchJobs(query))
                .isInstanceOf(JobProviderException.class)
                .hasMessageContaining("Jooble API fetch failed for location=Bern page=1")
                .hasMessageNotContaining(TEST_KEY);
        mockServer.verify();
    }

    @Test
    void shouldFilterSinceWithoutAbortingUnsortedResultsRespectRemoteOnlyAndStopOnTotalCount() {
        Instant cutoff = Instant.parse("2026-11-05T00:00:00Z");

        mockServer.expect(once(), requestTo("https://jooble.org/api/" + TEST_KEY))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("""
                        {
                          "totalCount": 2,
                          "jobs": [
                            {
                              "id": "old-1",
                              "title": "Remote Old Java Role",
                              "location": "Berlin, Germany",
                              "snippet": "Older posting before watermark.",
                              "link": "https://jooble.org/jdp/old-1",
                              "company": "OldCo",
                              "updated": "2026-10-15T10:00:00.0000000"
                            },
                            {
                              "id": "new-remote-1",
                              "title": "Remote Recent Angular Role",
                              "location": "Germany",
                              "salary": "$55 - $70 / hour",
                              "snippet": "Remote work across Germany.",
                              "link": "https://jooble.org/jdp/new-remote-1",
                              "company": "NewRemoteCo",
                              "updated": "2026-11-07T12:00:00.0000000"
                            }
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));

        IngestionQuery query = IngestionQuery.builder()
                .countries(List.of("Netherlands", "Germany"))
                .remoteOnly(true)
                .since(cutoff)
                .maxPages(2)
                .build();

        List<RawJobPosting> items = provider.fetchJobs(query);
        mockServer.verify();

        assertThat(items).hasSize(1);
        RawJobPosting item = items.getFirst();
        assertThat(item.sourceJobId()).isEqualTo("new-remote-1");
        assertThat(item.salaryCurrency()).isEqualTo("USD");
        assertThat(item.salaryPeriodHint()).isEqualTo("HOURLY");
        assertThat(item.salaryMin()).isEqualByComparingTo("55");
        assertThat(item.salaryMax()).isEqualByComparingTo("70");
    }
}
