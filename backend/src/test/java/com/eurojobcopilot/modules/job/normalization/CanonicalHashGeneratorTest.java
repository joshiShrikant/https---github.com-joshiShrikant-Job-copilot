package com.eurojobcopilot.modules.job.normalization;

import com.eurojobcopilot.core.enums.WorkArrangement;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CanonicalHashGeneratorTest {

    private final CanonicalHashGenerator generator = new CanonicalHashGenerator();

    @Test
    void shouldProduceIdentical64CharSha256DespiteLegalSuffixesGenderMarkersAndDiacritics() {
        String hashA = generator.computeCanonicalHash(
                "Adyen B.V.",
                "Senior Java Engineer (m/f/d)",
                "Netherlands",
                "Amsterdam",
                WorkArrangement.HYBRID
        );

        String hashB = generator.computeCanonicalHash(
                "Adyen",
                "Senior Java Engineer",
                "Netherlands",
                "Amsterdam",
                WorkArrangement.HYBRID
        );

        assertThat(hashA).hasSize(64).matches("^[0-9a-f]{64}$");
        assertThat(hashA).isEqualTo(hashB);

        String munichHashA = generator.computeCanonicalHash(
                "Personio GmbH",
                "Backend Engineer (w/m/d)",
                "Germany",
                "München",
                WorkArrangement.ONSITE
        );
        String munichHashB = generator.computeCanonicalHash(
                "Personio",
                "Backend Engineer",
                "Germany",
                "Munchen",
                WorkArrangement.ONSITE
        );
        assertThat(munichHashA).isEqualTo(munichHashB);
    }

    @Test
    void shouldDifferentiateDistinctLocationsOrRolesAndHandleRemoteCityFallback() {
        String amsterdamHash = generator.computeCanonicalHash(
                "Klarna AB",
                "Platform Engineer",
                "Netherlands",
                "Amsterdam",
                WorkArrangement.REMOTE
        );
        String remoteGenericHashA = generator.computeCanonicalHash(
                "Klarna AB",
                "Platform Engineer",
                "Sweden",
                null,
                WorkArrangement.REMOTE
        );
        String remoteGenericHashB = generator.computeCanonicalHash(
                "Klarna",
                "Platform Engineer (all genders)",
                "Sweden",
                "",
                WorkArrangement.REMOTE
        );

        assertThat(remoteGenericHashA).isEqualTo(remoteGenericHashB);
        assertThat(amsterdamHash).isNotEqualTo(remoteGenericHashA);
    }
}

