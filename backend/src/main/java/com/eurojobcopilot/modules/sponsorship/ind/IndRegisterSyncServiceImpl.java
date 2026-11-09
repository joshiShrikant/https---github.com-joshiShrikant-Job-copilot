package com.eurojobcopilot.modules.sponsorship.ind;

import com.eurojobcopilot.core.exception.ResourceConflictException;
import com.eurojobcopilot.modules.job.normalization.CanonicalHashGenerator;
import com.eurojobcopilot.modules.sponsorship.catalog.CountrySponsorshipCatalog;
import com.eurojobcopilot.modules.sponsorship.config.SponsorshipProperties;
import com.eurojobcopilot.modules.sponsorship.dto.IndSyncResultDto;
import com.eurojobcopilot.modules.sponsorship.dto.RegistrySyncStatusDto;
import com.eurojobcopilot.modules.sponsorship.entity.Company;
import com.eurojobcopilot.modules.sponsorship.entity.CompanySponsorshipRecord;
import com.eurojobcopilot.modules.sponsorship.entity.OfficialSponsorRegistryEntry;
import com.eurojobcopilot.modules.sponsorship.entity.RegistrySyncStatus;
import com.eurojobcopilot.modules.sponsorship.entity.SponsorConfidenceLevel;
import com.eurojobcopilot.modules.sponsorship.entity.SponsorshipRecordStatus;
import com.eurojobcopilot.modules.sponsorship.entity.SponsorshipRegistrySyncLog;
import com.eurojobcopilot.modules.sponsorship.repository.CompanyRepository;
import com.eurojobcopilot.modules.sponsorship.repository.CompanySponsorshipRecordRepository;
import com.eurojobcopilot.modules.sponsorship.repository.OfficialSponsorRegistryEntryRepository;
import com.eurojobcopilot.modules.sponsorship.repository.SponsorshipRegistrySyncLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Service
@RequiredArgsConstructor
public class IndRegisterSyncServiceImpl implements IndRegisterSyncService {

    private static final String NL_COUNTRY = "Netherlands";
    private static final String NL_SOURCE = CountrySponsorshipCatalog.NL_IND_REGISTRY_SOURCE;

    private final IndRecognizedSponsorClient indClient;
    private final OfficialSponsorRegistryEntryRepository registryEntryRepository;
    private final SponsorshipRegistrySyncLogRepository syncLogRepository;
    private final CompanyRepository companyRepository;
    private final CompanySponsorshipRecordRepository companySponsorshipRecordRepository;
    private final CanonicalHashGenerator canonicalHashGenerator;
    private final SponsorshipProperties properties;

    private final AtomicBoolean syncInProgress = new AtomicBoolean(false);

    @Override
    @Transactional
    public IndSyncResultDto syncIndRegister() {
        return executeSync(indClient::fetchPublicRegister);
    }

    @Transactional
    public IndSyncResultDto applyFetchOutcome(IndRecognizedSponsorClient.FetchOutcome outcome) {
        return executeSync(() -> outcome);
    }

    @Override
    @Transactional(readOnly = true)
    public RegistrySyncStatusDto getLatestSyncStatus() {
        long activeCount = registryEntryRepository.countByRegistrySourceAndActiveTrue(NL_SOURCE);
        Optional<SponsorshipRegistrySyncLog> latestOpt =
                syncLogRepository.findTopByRegistrySourceOrderBySyncedAtDescIdDesc(NL_SOURCE);

        if (latestOpt.isEmpty()) {
            return RegistrySyncStatusDto.builder()
                    .registrySource(NL_SOURCE)
                    .country(NL_COUNTRY)
                    .status(properties.getInd().isEnabled() ? RegistrySyncStatus.UNAVAILABLE : RegistrySyncStatus.UNAVAILABLE)
                    .sourceUrl(properties.getInd().getRegisterUrl())
                    .activeEntriesCount(activeCount)
                    .entriesParsed(0)
                    .entriesUpserted(0)
                    .companiesLinked(0)
                    .errorMessage(properties.getInd().isEnabled()
                            ? "IND recognized sponsor register has not been synchronized yet."
                            : "IND public register live sync is currently disabled.")
                    .build();
        }

        SponsorshipRegistrySyncLog latest = latestOpt.get();
        return RegistrySyncStatusDto.builder()
                .registrySource(NL_SOURCE)
                .country(NL_COUNTRY)
                .status(latest.getStatus())
                .sourceUrl(latest.getSourceUrl())
                .activeEntriesCount(activeCount)
                .entriesParsed(latest.getEntriesParsed())
                .entriesUpserted(latest.getEntriesUpserted())
                .companiesLinked(latest.getCompaniesLinked())
                .errorMessage(latest.getErrorMessage())
                .syncedAt(latest.getSyncedAt())
                .build();
    }

    private IndSyncResultDto executeSync(java.util.function.Supplier<IndRecognizedSponsorClient.FetchOutcome> fetcher) {
        if (!syncInProgress.compareAndSet(false, true)) {
            throw new ResourceConflictException("Netherlands IND recognized sponsor sync is already in progress.");
        }

        Instant startedAt = Instant.now();
        try {
            IndRecognizedSponsorClient.FetchOutcome outcome = fetcher.get();
            Instant finishedAt = Instant.now();
            long durationMs = Math.max(0L, Duration.between(startedAt, finishedAt).toMillis());

            if (outcome.status() != RegistrySyncStatus.SUCCESS) {
                SponsorshipRegistrySyncLog failedLog = syncLogRepository.save(SponsorshipRegistrySyncLog.builder()
                        .registrySource(NL_SOURCE)
                        .country(NL_COUNTRY)
                        .status(outcome.status())
                        .sourceUrl(outcome.sourceUrl())
                        .entriesParsed(0)
                        .entriesUpserted(0)
                        .entriesDeactivated(0)
                        .companiesLinked(0)
                        .errorMessage(outcome.message())
                        .executionDurationMs(durationMs)
                        .syncedAt(finishedAt)
                        .build());

                return IndSyncResultDto.builder()
                        .registrySource(NL_SOURCE)
                        .country(NL_COUNTRY)
                        .status(failedLog.getStatus())
                        .sourceUrl(failedLog.getSourceUrl())
                        .entriesParsed(0)
                        .entriesUpserted(0)
                        .entriesDeactivated(0)
                        .companiesLinked(0)
                        .executionDurationMs(durationMs)
                        .message(failedLog.getErrorMessage())
                        .syncedAt(finishedAt)
                        .build();
            }

            List<OfficialSponsorRegistryEntry> existingEntries =
                    registryEntryRepository.findAllByRegistrySourceAndCountryIgnoreCase(NL_SOURCE, NL_COUNTRY);
            Map<String, OfficialSponsorRegistryEntry> existingByNormalized = new HashMap<>();
            for (OfficialSponsorRegistryEntry existing : existingEntries) {
                existingByNormalized.put(existing.getNormalizedName(), existing);
            }

            Set<String> seenNormalizedNames = new HashSet<>();
            List<OfficialSponsorRegistryEntry> toSave = new ArrayList<>();
            int upsertedCount = 0;

            for (IndRegisterParser.ParsedIndEntry parsedEntry : outcome.entries()) {
                String normalizedKey = resolveLookupKey(parsedEntry.officialName());
                if (normalizedKey.isBlank() || !seenNormalizedNames.add(normalizedKey)) {
                    continue;
                }

                OfficialSponsorRegistryEntry target = existingByNormalized.get(normalizedKey);
                if (target == null) {
                    target = OfficialSponsorRegistryEntry.builder()
                            .country(NL_COUNTRY)
                            .registrySource(NL_SOURCE)
                            .normalizedName(normalizedKey)
                            .build();
                }

                target.setOfficialName(truncate(parsedEntry.officialName(), 255));
                target.setRegistryIdentifier(truncate(parsedEntry.kvkNumber(), 150));
                target.setStatus(SponsorshipRecordStatus.RECOGNIZED_SPONSOR);
                target.setSourceUrl(truncate(outcome.sourceUrl(), 500));
                target.setEvidenceNotes("Listed on Netherlands IND Public Register of Recognised Sponsors (Regular Labour and Highly Skilled Migrants).");
                target.setActive(true);
                target.setSyncedAt(finishedAt);
                toSave.add(target);
                upsertedCount++;
            }

            int deactivatedCount = 0;
            for (OfficialSponsorRegistryEntry existing : existingEntries) {
                if (existing.isActive() && !seenNormalizedNames.contains(existing.getNormalizedName())) {
                    existing.setActive(false);
                    existing.setSyncedAt(finishedAt);
                    toSave.add(existing);
                    deactivatedCount++;
                }
            }

            registryEntryRepository.saveAll(toSave);

            int companiesLinked = crossLinkMatchingCompanies(seenNormalizedNames, existingByNormalized, toSave, finishedAt);
            durationMs = Math.max(0L, Duration.between(startedAt, Instant.now()).toMillis());

            SponsorshipRegistrySyncLog successLog = syncLogRepository.save(SponsorshipRegistrySyncLog.builder()
                    .registrySource(NL_SOURCE)
                    .country(NL_COUNTRY)
                    .status(RegistrySyncStatus.SUCCESS)
                    .sourceUrl(truncate(outcome.sourceUrl(), 500))
                    .entriesParsed(outcome.entries().size())
                    .entriesUpserted(upsertedCount)
                    .entriesDeactivated(deactivatedCount)
                    .companiesLinked(companiesLinked)
                    .executionDurationMs(durationMs)
                    .syncedAt(finishedAt)
                    .build());

            log.info("NL IND sync succeeded parsed={} upserted={} deactivated={} companiesLinked={}",
                    successLog.getEntriesParsed(), upsertedCount, deactivatedCount, companiesLinked);

            return IndSyncResultDto.builder()
                    .registrySource(NL_SOURCE)
                    .country(NL_COUNTRY)
                    .status(RegistrySyncStatus.SUCCESS)
                    .sourceUrl(successLog.getSourceUrl())
                    .entriesParsed(successLog.getEntriesParsed())
                    .entriesUpserted(upsertedCount)
                    .entriesDeactivated(deactivatedCount)
                    .companiesLinked(companiesLinked)
                    .executionDurationMs(durationMs)
                    .message(outcome.message())
                    .syncedAt(finishedAt)
                    .build();
        } finally {
            syncInProgress.set(false);
        }
    }

    private int crossLinkMatchingCompanies(
            Set<String> activeKeys,
            Map<String, OfficialSponsorRegistryEntry> existingByNormalized,
            List<OfficialSponsorRegistryEntry> savedEntries,
            Instant syncedAt
    ) {
        Map<String, OfficialSponsorRegistryEntry> activeMap = new HashMap<>(existingByNormalized);
        for (OfficialSponsorRegistryEntry entry : savedEntries) {
            if (entry.isActive()) {
                activeMap.put(entry.getNormalizedName(), entry);
            }
        }

        List<Company> companies = companyRepository.findAll();
        int linkedCount = 0;

        for (Company company : companies) {
            String key = canonicalHashGenerator.normalizeCompanyKey(company.getName());
            OfficialSponsorRegistryEntry match = activeMap.get(key);
            if (match == null && company.getNormalizedName() != null) {
                match = activeMap.get(company.getNormalizedName().toLowerCase(Locale.ROOT));
            }
            if (match == null || !activeKeys.contains(match.getNormalizedName())) {
                continue;
            }

            if (!company.isVerifiedSponsor() || company.getSponsorConfidenceLevel() != SponsorConfidenceLevel.VERIFIED) {
                company.setVerifiedSponsor(true);
                company.setSponsorConfidenceLevel(SponsorConfidenceLevel.VERIFIED);
                companyRepository.save(company);
            }

            CompanySponsorshipRecord record = companySponsorshipRecordRepository
                    .findByCompanyIdAndCountryIgnoreCaseAndRegistrySourceIgnoreCase(company.getId(), NL_COUNTRY, NL_SOURCE)
                    .orElseGet(() -> CompanySponsorshipRecord.builder()
                            .company(company)
                            .country(NL_COUNTRY)
                            .registrySource(NL_SOURCE)
                            .build());

            record.setRegistryIdentifier(match.getRegistryIdentifier());
            record.setOfficialRegisteredName(match.getOfficialName());
            record.setStatus(SponsorshipRecordStatus.RECOGNIZED_SPONSOR);
            record.setConfidenceLevel(SponsorConfidenceLevel.VERIFIED);
            record.setEvidenceNotes(match.getEvidenceNotes());
            record.setSourceUrl(match.getSourceUrl());
            record.setVerifiedAt(syncedAt);
            record.setLastCheckedAt(syncedAt);
            companySponsorshipRecordRepository.save(record);
            linkedCount++;
        }
        return linkedCount;
    }

    private String resolveLookupKey(String officialName) {
        String key = canonicalHashGenerator.normalizeCompanyKey(officialName);
        if (!key.isBlank()) {
            return key;
        }
        return officialName == null ? "" : officialName.trim().toLowerCase(Locale.ROOT);
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength).trim();
    }
}

