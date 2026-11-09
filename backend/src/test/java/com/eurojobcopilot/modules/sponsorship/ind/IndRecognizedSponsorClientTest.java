package com.eurojobcopilot.modules.sponsorship.ind;

import com.eurojobcopilot.modules.sponsorship.config.SponsorshipProperties;
import com.eurojobcopilot.modules.sponsorship.entity.RegistrySyncStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class IndRecognizedSponsorClientTest {

    private SponsorshipProperties properties;
    private RestClient.Builder restClientBuilder;
    private MockRestServiceServer mockServer;
    private IndRecognizedSponsorClient client;

    @BeforeEach
    void setUp() {
        properties = new SponsorshipProperties();
        properties.getInd().setEnabled(true);
        properties.getInd().setRegisterUrl("https://ind.nl/en/public-register-recognised-sponsors");

        restClientBuilder = RestClient.builder();
        mockServer = MockRestServiceServer.bindTo(restClientBuilder).build();
        client = new IndRecognizedSponsorClient(properties, new IndRegisterParser(), restClientBuilder);
    }

    @Test
    void shouldFetchAndParseHtmlTableWhenIndRegisterIsAccessible() {
        String html = """
                <table>
                  <tr><th>Organisation</th><th>KvK</th></tr>
                  <tr><td>Adyen N.V.</td><td>34259528</td></tr>
                  <tr><td>ASML Netherlands B.V.</td><td>17085815</td></tr>
                </table>
                """;
        mockServer.expect(requestTo("https://ind.nl/en/public-register-recognised-sponsors"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(html, MediaType.TEXT_HTML));

        IndRecognizedSponsorClient.FetchOutcome outcome = client.fetchPublicRegister();

        assertThat(outcome.status()).isEqualTo(RegistrySyncStatus.SUCCESS);
        assertThat(outcome.entries()).hasSize(2);
        assertThat(outcome.entries().getFirst().officialName()).isEqualTo("Adyen N.V.");
        mockServer.verify();
    }

    @Test
    void shouldReturnBlockedWhenHttp403OrCaptchaChallengeReturned() {
        mockServer.expect(requestTo("https://ind.nl/en/public-register-recognised-sponsors"))
                .andRespond(withStatus(HttpStatus.FORBIDDEN));

        IndRecognizedSponsorClient.FetchOutcome forbiddenOutcome = client.fetchPublicRegister();
        assertThat(forbiddenOutcome.status()).isEqualTo(RegistrySyncStatus.BLOCKED);
        assertThat(forbiddenOutcome.entries()).isEmpty();
        assertThat(forbiddenOutcome.message()).contains("403");
        mockServer.verify();

        mockServer.reset();
        mockServer.expect(requestTo("https://ind.nl/en/public-register-recognised-sponsors"))
                .andRespond(withSuccess("<div class=\"g-recaptcha\">Verify you are human</div>", MediaType.TEXT_HTML));

        IndRecognizedSponsorClient.FetchOutcome captchaOutcome = client.fetchPublicRegister();
        assertThat(captchaOutcome.status()).isEqualTo(RegistrySyncStatus.BLOCKED);
        assertThat(captchaOutcome.entries()).isEmpty();
        mockServer.verify();
    }

    @Test
    void shouldReturnUnavailableWhenDisabledOrUpstreamServerFails() {
        mockServer.expect(requestTo("https://ind.nl/en/public-register-recognised-sponsors"))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        IndRecognizedSponsorClient.FetchOutcome serverDownOutcome = client.fetchPublicRegister();
        assertThat(serverDownOutcome.status()).isEqualTo(RegistrySyncStatus.UNAVAILABLE);
        assertThat(serverDownOutcome.message()).contains("503");
        mockServer.verify();

        properties.getInd().setEnabled(false);
        IndRecognizedSponsorClient.FetchOutcome disabledOutcome = client.fetchPublicRegister();
        assertThat(disabledOutcome.status()).isEqualTo(RegistrySyncStatus.UNAVAILABLE);
        assertThat(disabledOutcome.message()).contains("disabled");
    }
}

