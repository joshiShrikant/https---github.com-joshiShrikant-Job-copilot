package com.eurojobcopilot.modules.sponsorship.ind;

import com.eurojobcopilot.modules.sponsorship.config.SponsorshipProperties;
import com.eurojobcopilot.modules.sponsorship.entity.RegistrySyncStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;

/**
 * Fetches the official Netherlands IND public register of recognized sponsors where permitted.
 * Respects HTTP access controls (403/429) and anti-bot challenges without bypassing them.
 */
@Slf4j
@Component
public class IndRecognizedSponsorClient {

    private final SponsorshipProperties properties;
    private final IndRegisterParser parser;
    private final RestClient.Builder restClientBuilder;

    public IndRecognizedSponsorClient(
            SponsorshipProperties properties,
            IndRegisterParser parser,
            @Qualifier("sponsorshipRestClientBuilder") RestClient.Builder restClientBuilder
    ) {
        this.properties = properties;
        this.parser = parser;
        this.restClientBuilder = restClientBuilder;
    }

    public record FetchOutcome(
            RegistrySyncStatus status,
            List<IndRegisterParser.ParsedIndEntry> entries,
            String sourceUrl,
            String message
    ) {
    }

    public FetchOutcome fetchPublicRegister() {
        return fetchPublicRegister(buildDefaultRestClient());
    }

    public FetchOutcome fetchPublicRegister(RestClient client) {
        SponsorshipProperties.IndProperties indProps = properties.getInd();
        String url = indProps.getRegisterUrl();
        if (!indProps.isEnabled()) {
            return new FetchOutcome(
                    RegistrySyncStatus.UNAVAILABLE,
                    List.of(),
                    url,
                    "Netherlands IND public register synchronization is disabled by configuration."
            );
        }

        try {
            String body = client.get()
                    .uri(url)
                    .header(HttpHeaders.USER_AGENT, indProps.getUserAgent())
                    .header(HttpHeaders.ACCEPT, "text/html,text/csv,application/xhtml+xml;q=0.9,*/*;q=0.8")
                    .retrieve()
                    .body(String.class);

            IndRegisterParser.ParseResult parsed = parser.parse(body);
            if (parsed.blockedOrCaptcha()) {
                log.warn("IND register fetch blocked or gated by challenge url={}", url);
                return new FetchOutcome(
                        RegistrySyncStatus.BLOCKED,
                        List.of(),
                        url,
                        parsed.blockReason()
                );
            }
            if (parsed.entries().isEmpty()) {
                return new FetchOutcome(
                        RegistrySyncStatus.UNAVAILABLE,
                        List.of(),
                        url,
                        "IND register response returned zero parseable sponsor entries (format=" + parsed.format() + ")."
                );
            }
            return new FetchOutcome(
                    RegistrySyncStatus.SUCCESS,
                    parsed.entries(),
                    url,
                    "Parsed " + parsed.entries().size() + " IND recognized sponsor entries (" + parsed.format() + ")."
            );
        } catch (HttpStatusCodeException ex) {
            HttpStatusCode code = ex.getStatusCode();
            RegistrySyncStatus syncStatus = (code.value() == 401 || code.value() == 403 || code.value() == 429)
                    ? RegistrySyncStatus.BLOCKED
                    : RegistrySyncStatus.UNAVAILABLE;
            log.warn("IND register HTTP {} for url={}: {}", code.value(), url, ex.getStatusText());
            return new FetchOutcome(
                    syncStatus,
                    List.of(),
                    url,
                    "IND register returned HTTP " + code.value() + " (" + ex.getStatusText() + "); access unavailable."
            );
        } catch (RestClientException ex) {
            log.warn("IND register network/client failure for url={}: {}", url, ex.getMessage());
            return new FetchOutcome(
                    RegistrySyncStatus.FAILED,
                    List.of(),
                    url,
                    "Failed to retrieve IND register: " + ex.getMessage()
            );
        }
    }

    private RestClient buildDefaultRestClient() {
        return restClientBuilder
                .clone()
                .build();
    }
}
