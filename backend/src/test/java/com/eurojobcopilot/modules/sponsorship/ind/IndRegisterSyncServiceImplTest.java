package com.eurojobcopilot.modules.sponsorship.ind;

import com.eurojobcopilot.core.exception.ResourceConflictException;
import com.eurojobcopilot.modules.job.normalization.CanonicalHashGenerator;
import com.eurojobcopilot.modules.sponsorship.catalog.CountrySponsorshipCatalog;
import com.eurojobcopilot.modules.sponsorship.config.SponsorshipProperties;
import com.eurojobcopilot.modules.sponsorship.dto.IndSyncResultDto;
import com.eurojobcopilot.modules.sponsorship.entity.Company;
import com.eurojobcopilot.modules.sponsorship.entity.CompanySponsorshipRecord;
import com.eurojobcopilot.modules.sponsorship.entity.OfficialSponsorRegistryEntry;
import com.eurojobcopilot.modules.sponsorship.entity.RegistrySyncStatus;
import com.eurojobcopilot.modules.sponsorship.entity.SponsorConfidenceLevel;
import com.eurojobcopilot.modules.sponsorship.repository.CompanyRepository;
import com.eurojobcopilot.modules.sponsorship.repository.CompanySponsorshipRecordRepository;
import com.eurojobcopilot.modules.sponsorship.repository.OfficialSponsorRegistryEntryRepository;
import com.eurojobcopilot.modules.sponsorship.repository.SponsorshipRegistrySyncLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IndRegisterSyncServiceImplTest {

    @Mock
    private IndRecognizedSponsorClient indClient;

    @Mock
    private OfficialSponsorRegistryEntryRepository registryEntryRepository;

    @Mock
    private SponsorshipRegistrySyncLogRepository syncLogRepository;

    @Mock
    private CompanyRepository companyRepository;

    @Mock
    private CompanySponsorshipRecordRepository companySponsorshipRecordRepository;

    private IndRegisterSyncServiceImpl service;

    @BeforeEach
    void setUp() {
        SponsorshipProperties properties = new SponsorshipProperties();
        properties.getInd().setEnabled(true);
        service = new IndRegisterSyncServiceImpl(
                indClient,
                registryEntryRepository,
                syncLogRepository,
                companyRepository,
                companySponsorshipRecordRepository,
                new CanonicalHashGenerator(),
                properties
        );
    }

    @Test
    void shouldUpsertEntriesDeactivateMissingOnesAndCrossLinkCompanies() {
        OfficialSponsorRegistryEntry oldEntryToRemove = OfficialSponsorRegistryEntry.builder()
                .id(1L)
                .country("Netherlands")
                .registrySource(CountrySponsorshipCatalog.NL_IND_REGISTRY_SOURCE)
                .normalizedName("old sponsor")
                .officialName("Old Sponsor B.V.")
                .active(true)
                .build();
        OfficialSponsorRegistryEntry existingAdyen = OfficialSponsorRegistryEntry.builder()
                .id(2L)
                .country("Netherlands")
                .registrySource(CountrySponsorshipCatalog.NL_IND_REGISTRY_SOURCE)
                .normalizedName("adyen")
                .officialName("Adyen")
                .active(true)
                .build();

        Company adyenCompany = Company.builder()
                .id(42L)
                .name("Adyen N.V.")
                .normalizedName("adyen")
                .verifiedSponsor(false)
                .sponsorConfidenceLevel(SponsorConfidenceLevel.UNVERIFIED)
                .build();

        when(indClient.fetchPublicRegister()).thenReturn(new IndRecognizedSponsorClient.FetchOutcome(
                RegistrySyncStatus.SUCCESS,
                List.of(new IndRegisterParser.ParsedIndEntry("Adyen N.V.", "KVK-34259528")),
                "https://ind.nl/en/public-register-recognised-sponsors",
                "Parsed 1 entry"
        ));
        when(registryEntryRepository.findAllByRegistrySourceAndCountryIgnoreCase(
                CountrySponsorshipCatalog.NL_IND_REGISTRY_SOURCE, "Netherlands"))
                .thenReturn(List.of(oldEntryToRemove, existingAdyen));
        when(companyRepository.findAll()).thenReturn(List.of(adyenCompany));
        when(companySponsorshipRecordRepository.findByCompanyIdAndCountryIgnoreCaseAndRegistrySourceIgnoreCase(
                42L, "Netherlands", CountrySponsorshipCatalog.NL_IND_REGISTRY_SOURCE))
                .thenReturn(Optional.empty());
        when(syncLogRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        IndSyncResultDto result = service.syncIndRegister();

        assertThat(result.getStatus()).isEqualTo(RegistrySyncStatus.SUCCESS);
        assertThat(result.getEntriesParsed()).isEqualTo(1);
        assertThat(result.getEntriesUpserted()).isEqualTo(1);
        assertThat(result.getEntriesDeactivated()).isEqualTo(1);
        assertThat(result.getCompaniesLinked()).isEqualTo(1);

        assertThat(oldEntryToRemove.isActive()).isFalse();
        assertThat(existingAdyen.isActive()).isTrue();
        assertThat(existingAdyen.getOfficialName()).isEqualTo("Adyen N.V.");
        assertThat(adyenCompany.isVerifiedSponsor()).isTrue();
        assertThat(adyenCompany.getSponsorConfidenceLevel()).isEqualTo(SponsorConfidenceLevel.VERIFIED);

        ArgumentCaptor<CompanySponsorshipRecord> recordCaptor = ArgumentCaptor.forClass(CompanySponsorshipRecord.class);
        verify(companySponsorshipRecordRepository).save(recordCaptor.capture());
        assertThat(recordCaptor.getValue().getRegistryIdentifier()).isEqualTo("KVK-34259528");
    }

    @Test
    void shouldPreserveExistingEntriesWhenIndFetchIsBlockedAndThrow409OnConcurrentRun() throws Exception {
        when(indClient.fetchPublicRegister()).thenReturn(new IndRecognizedSponsorClient.FetchOutcome(
                RegistrySyncStatus.BLOCKED,
                List.of(),
                "https://ind.nl/en/public-register-recognised-sponsors",
                "HTTP 403"
        ));
        when(syncLogRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        IndSyncResultDto blockedResult = service.syncIndRegister();
        assertThat(blockedResult.getStatus()).isEqualTo(RegistrySyncStatus.BLOCKED);
        verify(registryEntryRepository, never()).saveAll(anyList());

        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        when(indClient.fetchPublicRegister()).thenAnswer(inv -> {
            entered.countDown();
            release.await(5, TimeUnit.SECONDS);
            return new IndRecognizedSponsorClient.FetchOutcome(
                    RegistrySyncStatus.UNAVAILABLE, List.of(), "https://ind.nl", "Unavailable"
            );
        });

        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            Future<IndSyncResultDto> first = pool.submit(() -> service.syncIndRegister());
            assertThat(entered.await(3, TimeUnit.SECONDS)).isTrue();

            assertThatThrownBy(() -> service.syncIndRegister())
                    .isInstanceOf(ResourceConflictException.class)
                    .hasMessageContaining("already in progress");

            release.countDown();
            assertThat(first.get(5, TimeUnit.SECONDS).getStatus()).isEqualTo(RegistrySyncStatus.UNAVAILABLE);
        } finally {
            release.countDown();
            pool.shutdownNow();
        }
    }
}
