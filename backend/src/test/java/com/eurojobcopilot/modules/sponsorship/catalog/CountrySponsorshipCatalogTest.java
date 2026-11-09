package com.eurojobcopilot.modules.sponsorship.catalog;

import com.eurojobcopilot.core.exception.BusinessValidationException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CountrySponsorshipCatalogTest {

    private final CountrySponsorshipCatalog catalog = new CountrySponsorshipCatalog();

    @Test
    void shouldExposeExactly14InitialCountriesWithOfficialSourcesAndWarnings() {
        List<CountrySponsorshipProfile> countries = catalog.getAllCountries();

        assertThat(countries)
                .extracting(CountrySponsorshipProfile::country)
                .containsExactly(
                        "Germany",
                        "Netherlands",
                        "Luxembourg",
                        "Switzerland",
                        "Ireland",
                        "Austria",
                        "Belgium",
                        "Sweden",
                        "Denmark",
                        "Finland",
                        "France",
                        "Poland",
                        "Portugal",
                        "Spain"
                );

        for (CountrySponsorshipProfile profile : countries) {
            assertThat(profile.isoCode()).hasSize(2);
            assertThat(profile.officialSourceUrl()).startsWith("https://");
            assertThat(profile.publicRegisterUrl()).startsWith("https://");
            assertThat(profile.visaPathways()).isNotEmpty();
            assertThat(profile.guidanceSummary()).isNotBlank();
            assertThat(profile.vacancyRuleWarning()).isNotBlank();
        }
    }

    @Test
    void shouldResolveIsoCodesAndLocalizedAliasesCleanly() {
        assertThat(catalog.requireSupportedCountry("NL").country()).isEqualTo("Netherlands");
        assertThat(catalog.requireSupportedCountry("Nederland").country()).isEqualTo("Netherlands");
        assertThat(catalog.requireSupportedCountry("de").country()).isEqualTo("Germany");
        assertThat(catalog.requireSupportedCountry("Deutschland").country()).isEqualTo("Germany");
        assertThat(catalog.requireSupportedCountry("LU").country()).isEqualTo("Luxembourg");
        assertThat(catalog.requireSupportedCountry("Österreich").country()).isEqualTo("Austria");
        assertThat(catalog.requireSupportedCountry("España").country()).isEqualTo("Spain");
        assertThat(catalog.requireSupportedCountry("Schweiz").country()).isEqualTo("Switzerland");
    }

    @Test
    void shouldRejectUnsupportedOrBlankCountriesWithValidationException() {
        assertThatThrownBy(() -> catalog.requireSupportedCountry("United States"))
                .isInstanceOf(BusinessValidationException.class)
                .hasMessageContaining("Unsupported country");

        assertThatThrownBy(() -> catalog.requireSupportedCountry("   "))
                .isInstanceOf(BusinessValidationException.class)
                .hasMessageContaining("required");
    }
}

