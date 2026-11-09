package com.eurojobcopilot.modules.sponsorship.ind;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class IndRegisterParserTest {

    private final IndRegisterParser parser = new IndRegisterParser();

    @Test
    void shouldParseHtmlSponsorTableExtractKvkAndDecodeEntities() {
        String html = """
                <html><body>
                <table>
                  <tr><th>Organisation</th><th>KvK number</th></tr>
                  <tr><td><a href="/org/1">Adyen N.V.</a></td><td>34259528</td></tr>
                  <tr><td>Booking.com &amp; Partners B.V.</td><td>KvK 60754980</td></tr>
                  <tr><td>Adyen N.V.</td><td>34259528</td></tr>
                  <tr><td>   </td><td>12345678</td></tr>
                </table>
                </body></html>
                """;

        IndRegisterParser.ParseResult result = parser.parse(html);

        assertThat(result.blockedOrCaptcha()).isFalse();
        assertThat(result.format()).isEqualTo("HTML_TABLE");
        assertThat(result.entries()).hasSize(2);
        assertThat(result.entries().get(0).officialName()).isEqualTo("Adyen N.V.");
        assertThat(result.entries().get(0).kvkNumber()).isEqualTo("KVK-34259528");
        assertThat(result.entries().get(1).officialName()).isEqualTo("Booking.com & Partners B.V.");
        assertThat(result.entries().get(1).kvkNumber()).isEqualTo("KVK-60754980");
    }

    @Test
    void shouldParseDelimitedCsvSponsorLinesRespectingQuotes() {
        String csv = """
                Organisation;KvK
                "Mollie B.V.";50205773
                "ASML Netherlands B.V.";"17085815"
                """;

        IndRegisterParser.ParseResult result = parser.parse(csv);

        assertThat(result.blockedOrCaptcha()).isFalse();
        assertThat(result.format()).isEqualTo("DELIMITED_TEXT");
        assertThat(result.entries())
                .extracting(IndRegisterParser.ParsedIndEntry::officialName, IndRegisterParser.ParsedIndEntry::kvkNumber)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("Mollie B.V.", "KVK-50205773"),
                        org.assertj.core.groups.Tuple.tuple("ASML Netherlands B.V.", "KVK-17085815")
                );
    }

    @Test
    void shouldDetectAntiBotOrCaptchaChallengesWithoutBypassingThem() {
        String cloudflareHtml = """
                <html><body>
                  <div class="cf-turnstile">Verify you are human</div>
                  <script src="/cdn-cgi/challenge-platform/h/b/orchestrate/chl_page/v1"></script>
                </body></html>
                """;

        IndRegisterParser.ParseResult result = parser.parse(cloudflareHtml);

        assertThat(result.blockedOrCaptcha()).isTrue();
        assertThat(result.format()).isEqualTo("BLOCKED");
        assertThat(result.entries()).isEmpty();
        assertThat(result.blockReason()).containsIgnoringCase("CAPTCHA");
    }
}

