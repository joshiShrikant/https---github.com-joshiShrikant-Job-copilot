package com.eurojobcopilot.modules.sponsorship.repository;

import com.eurojobcopilot.config.JpaAuditingConfig;
import com.eurojobcopilot.modules.auth.entity.User;
import com.eurojobcopilot.modules.auth.repository.UserRepository;
import com.eurojobcopilot.modules.sponsorship.catalog.CountrySponsorshipCatalog;
import com.eurojobcopilot.modules.sponsorship.entity.OfficialSponsorRegistryEntry;
import com.eurojobcopilot.modules.sponsorship.entity.RegistrySyncStatus;
import com.eurojobcopilot.modules.sponsorship.entity.SponsorConfidenceLevel;
import com.eurojobcopilot.modules.sponsorship.entity.SponsorshipRecordStatus;
import com.eurojobcopilot.modules.sponsorship.entity.SponsorshipRegistrySyncLog;
import com.eurojobcopilot.modules.sponsorship.entity.SponsorshipVerificationHistory;
import com.eurojobcopilot.modules.sponsorship.entity.VisaSponsorshipStatus;
import com.eurojobcopilot.support.TestEntityFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(JpaAuditingConfig.class)
class SponsorshipIntelligenceRepositoryTest {

    @Autowired
    private OfficialSponsorRegistryEntryRepository registryEntryRepository;

    @Autowired
    private SponsorshipVerificationHistoryRepository historyRepository;

    @Autowired
    private SponsorshipRegistrySyncLogRepository syncLogRepository;

    @Autowired
    private UserRepository userRepository;

    @Test
    void shouldPersistAndQueryIndRegistryEntriesVerificationHistoryAndSyncLogs() {
        User userA = userRepository.saveAndFlush(TestEntityFactory.createUser("verify.a@eurojob.eu"));
        User userB = userRepository.saveAndFlush(TestEntityFactory.createUser("verify.b@eurojob.eu"));

        registryEntryRepository.saveAndFlush(OfficialSponsorRegistryEntry.builder()
                .country("Netherlands")
                .registrySource(CountrySponsorshipCatalog.NL_IND_REGISTRY_SOURCE)
                .registryIdentifier("KVK-34259528")
                .officialName("Adyen N.V.")
                .normalizedName("adyen")
                .status(SponsorshipRecordStatus.RECOGNIZED_SPONSOR)
                .sourceUrl("https://ind.nl/en/public-register-recognised-sponsors")
                .active(true)
                .syncedAt(Instant.now())
                .build());

        registryEntryRepository.saveAndFlush(OfficialSponsorRegistryEntry.builder()
                .country("Netherlands")
                .registrySource(CountrySponsorshipCatalog.NL_IND_REGISTRY_SOURCE)
                .registryIdentifier("KVK-99999999")
                .officialName("Revoked Legacy B.V.")
                .normalizedName("revoked legacy")
                .status(SponsorshipRecordStatus.RECOGNIZED_SPONSOR)
                .active(false)
                .syncedAt(Instant.now())
                .build());

        syncLogRepository.saveAndFlush(SponsorshipRegistrySyncLog.builder()
                .registrySource(CountrySponsorshipCatalog.NL_IND_REGISTRY_SOURCE)
                .country("Netherlands")
                .status(RegistrySyncStatus.SUCCESS)
                .sourceUrl("https://ind.nl/en/public-register-recognised-sponsors")
                .entriesParsed(2)
                .entriesUpserted(2)
                .entriesDeactivated(0)
                .companiesLinked(1)
                .executionDurationMs(125L)
                .syncedAt(Instant.now())
                .build());

        historyRepository.saveAndFlush(SponsorshipVerificationHistory.builder()
                .triggeredByUser(userA)
                .companyName("Adyen N.V.")
                .normalizedCompanyName("adyen")
                .country("Netherlands")
                .countryIsoCode("NL")
                .status(VisaSponsorshipStatus.VERIFIED_SPONSOR)
                .vacancyStatus(VisaSponsorshipStatus.NOT_MENTIONED)
                .vacancySponsorshipConfirmed(false)
                .vacancySponsorshipAssumed(false)
                .confidenceLevel(SponsorConfidenceLevel.VERIFIED)
                .registrySource(CountrySponsorshipCatalog.NL_IND_REGISTRY_SOURCE)
                .registryIdentifier("KVK-34259528")
                .officialRegisteredName("Adyen N.V.")
                .evidenceSummary("Matched IND register")
                .evidenceSignals(List.of("Matched IND register entry"))
                .sourceUrl("https://ind.nl/en/public-register-recognised-sponsors")
                .vacancyCaveat(CountrySponsorshipCatalog.DEFAULT_VACANCY_CAVEAT)
                .verifiedAt(Instant.now())
                .build());

        historyRepository.saveAndFlush(SponsorshipVerificationHistory.builder()
                .triggeredByUser(userB)
                .companyName("Other GmbH")
                .normalizedCompanyName("other")
                .country("Germany")
                .countryIsoCode("DE")
                .status(VisaSponsorshipStatus.NOT_MENTIONED)
                .vacancyStatus(VisaSponsorshipStatus.NOT_MENTIONED)
                .vacancySponsorshipConfirmed(false)
                .vacancySponsorshipAssumed(false)
                .confidenceLevel(SponsorConfidenceLevel.UNVERIFIED)
                .verifiedAt(Instant.now())
                .build());

        Page<OfficialSponsorRegistryEntry> activePage = registryEntryRepository.searchActiveByRegistrySource(
                CountrySponsorshipCatalog.NL_IND_REGISTRY_SOURCE, "3425", PageRequest.of(0, 10)
        );
        assertThat(activePage.getTotalElements()).isEqualTo(1);
        assertThat(activePage.getContent().getFirst().getOfficialName()).isEqualTo("Adyen N.V.");

        assertThat(registryEntryRepository.countByCountryIgnoreCaseAndActiveTrue("Netherlands")).isEqualTo(1);
        assertThat(syncLogRepository.findTopByRegistrySourceOrderBySyncedAtDescIdDesc(
                CountrySponsorshipCatalog.NL_IND_REGISTRY_SOURCE
        )).isPresent();

        Page<SponsorshipVerificationHistory> userAHistory = historyRepository.findUserHistoryFiltered(
                userA.getId(), "adyen", "Netherlands", PageRequest.of(0, 10)
        );
        assertThat(userAHistory.getTotalElements()).isEqualTo(1);
        assertThat(userAHistory.getContent().getFirst().getEvidenceSignals())
                .containsExactly("Matched IND register entry");
    }
}

