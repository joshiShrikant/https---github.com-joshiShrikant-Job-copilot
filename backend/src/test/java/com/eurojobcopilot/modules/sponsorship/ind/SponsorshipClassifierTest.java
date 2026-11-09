package com.eurojobcopilot.modules.sponsorship.ind;

import com.eurojobcopilot.modules.job.entity.Job;
import com.eurojobcopilot.modules.job.entity.JobAiAnalysis;
import com.eurojobcopilot.modules.job.entity.JobSponsorStatus;
import com.eurojobcopilot.modules.sponsorship.catalog.CountrySponsorshipCatalog;
import com.eurojobcopilot.modules.sponsorship.catalog.CountrySponsorshipProfile;
import com.eurojobcopilot.modules.sponsorship.entity.Company;
import com.eurojobcopilot.modules.sponsorship.entity.OfficialSponsorRegistryEntry;
import com.eurojobcopilot.modules.sponsorship.entity.SponsorConfidenceLevel;
import com.eurojobcopilot.modules.sponsorship.entity.SponsorshipRecordStatus;
import com.eurojobcopilot.modules.sponsorship.entity.VisaSponsorshipStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SponsorshipClassifierTest {

    private final CountrySponsorshipCatalog catalog = new CountrySponsorshipCatalog();
    private final SponsorshipClassifier classifier = new SponsorshipClassifier();

    @Test
    void shouldClassifyVerifiedIndSponsorWithoutAssumingEveryVacancyOffersSponsorship() {
        CountrySponsorshipProfile nl = catalog.requireSupportedCountry("Netherlands");
        OfficialSponsorRegistryEntry indEntry = OfficialSponsorRegistryEntry.builder()
                .country("Netherlands")
                .registrySource(CountrySponsorshipCatalog.NL_IND_REGISTRY_SOURCE)
                .registryIdentifier("KVK-34259528")
                .officialName("Adyen N.V.")
                .normalizedName("adyen")
                .status(SponsorshipRecordStatus.RECOGNIZED_SPONSOR)
                .sourceUrl(nl.publicRegisterUrl())
                .active(true)
                .syncedAt(Instant.now())
                .build();

        SponsorshipClassifier.VacancyAssessment neutralVacancy = classifier.assessVacancy(
                null, null, "Lead Java payment architecture in Amsterdam."
        );

        SponsorshipClassifier.ClassificationResult result = classifier.classify(
                "Adyen N.V.", nl, null, List.of(), indEntry, neutralVacancy, true, null
        );

        assertThat(result.status()).isEqualTo(VisaSponsorshipStatus.VERIFIED_SPONSOR);
        assertThat(result.vacancyStatus()).isEqualTo(VisaSponsorshipStatus.NOT_MENTIONED);
        assertThat(result.companyRecognizedSponsor()).isTrue();
        assertThat(result.vacancySponsorshipConfirmed()).isFalse();
        assertThat(result.vacancySponsorshipAssumed()).isFalse();
        assertThat(result.confidenceLevel()).isEqualTo(SponsorConfidenceLevel.VERIFIED);
        assertThat(result.vacancyCaveat()).containsIgnoringCase("does NOT guarantee every vacancy");
    }

    @Test
    void shouldOverrideRecognizedSponsorToUnavailableWhenVacancyDeclinesSponsorship() {
        CountrySponsorshipProfile nl = catalog.requireSupportedCountry("Netherlands");
        OfficialSponsorRegistryEntry indEntry = OfficialSponsorRegistryEntry.builder()
                .country("Netherlands")
                .registrySource(CountrySponsorshipCatalog.NL_IND_REGISTRY_SOURCE)
                .registryIdentifier("KVK-34259528")
                .officialName("Adyen N.V.")
                .normalizedName("adyen")
                .status(SponsorshipRecordStatus.RECOGNIZED_SPONSOR)
                .sourceUrl(nl.publicRegisterUrl())
                .active(true)
                .syncedAt(Instant.now())
                .build();

        Job job = Job.builder()
                .title("Junior Operations Associate")
                .description("Local contract only. Unable to sponsor work visas; valid EU work permit required.")
                .sponsorStatus(JobSponsorStatus.VERIFIED_SPONSOR)
                .build();

        SponsorshipClassifier.VacancyAssessment rejectedVacancy = classifier.assessVacancy(job, null, null);
        SponsorshipClassifier.ClassificationResult result = classifier.classify(
                "Adyen N.V.", nl, null, List.of(), indEntry, rejectedVacancy, true, job
        );

        assertThat(result.status()).isEqualTo(VisaSponsorshipStatus.UNAVAILABLE);
        assertThat(result.vacancyStatus()).isEqualTo(VisaSponsorshipStatus.UNAVAILABLE);
        assertThat(result.companyRecognizedSponsor()).isTrue();
        assertThat(result.vacancySponsorshipConfirmed()).isFalse();
        assertThat(result.vacancySponsorshipAssumed()).isFalse();
        assertThat(result.confidenceLevel()).isEqualTo(SponsorConfidenceLevel.LOW);
        assertThat(result.vacancyCaveat()).containsIgnoringCase("never guarantees sponsorship for every opening");
    }

    @Test
    void shouldClassifyRemainingFiveStatusesAccurately() {
        CountrySponsorshipProfile de = catalog.requireSupportedCountry("Germany");

        // 1. Sponsorship explicitly mentioned
        SponsorshipClassifier.VacancyAssessment mentioned = classifier.assessVacancy(
                null,
                JobAiAnalysis.builder().sponsorshipOffered(true).build(),
                "EU Blue Card visa sponsorship provided."
        );
        SponsorshipClassifier.ClassificationResult mentionedResult = classifier.classify(
                "Berlin Cloud GmbH", de, null, List.of(), null, mentioned, false, null
        );
        assertThat(mentionedResult.status()).isEqualTo(VisaSponsorshipStatus.SPONSORSHIP_MENTIONED);
        assertThat(mentionedResult.vacancySponsorshipConfirmed()).isTrue();

        // 2. Relocation assistance mentioned only
        SponsorshipClassifier.VacancyAssessment relocation = classifier.assessVacancy(
                null, null, "We provide a generous relocation package to Munich."
        );
        SponsorshipClassifier.ClassificationResult relocationResult = classifier.classify(
                "Munich AI GmbH", de, null, List.of(), null, relocation, false, null
        );
        assertThat(relocationResult.status()).isEqualTo(VisaSponsorshipStatus.RELOCATION_ASSISTANCE_MENTIONED);
        assertThat(relocationResult.vacancySponsorshipConfirmed()).isFalse();

        // 3. Possible but unconfirmed (international / English-speaking clue or medium confidence employer)
        SponsorshipClassifier.VacancyAssessment possible = classifier.assessVacancy(
                null, null, "English-speaking international team building cloud APIs."
        );
        Company mediumCompany = Company.builder()
                .name("Munich Cloud")
                .sponsorConfidenceLevel(SponsorConfidenceLevel.MEDIUM)
                .build();
        SponsorshipClassifier.ClassificationResult possibleResult = classifier.classify(
                "Munich Cloud", de, mediumCompany, List.of(), null, possible, false, null
        );
        assertThat(possibleResult.status()).isEqualTo(VisaSponsorshipStatus.POSSIBLE_UNCONFIRMED);
        assertThat(possibleResult.confidenceLevel()).isEqualTo(SponsorConfidenceLevel.LOW);

        // 4. Not mentioned
        SponsorshipClassifier.VacancyAssessment notMentioned = classifier.assessVacancy(
                null, null, "Maintain Spring Boot microservices."
        );
        SponsorshipClassifier.ClassificationResult notMentionedResult = classifier.classify(
                "Local GmbH", de, null, List.of(), null, notMentioned, false, null
        );
        assertThat(notMentionedResult.status()).isEqualTo(VisaSponsorshipStatus.NOT_MENTIONED);
        assertThat(notMentionedResult.confidenceLevel()).isEqualTo(SponsorConfidenceLevel.UNVERIFIED);
    }
}

