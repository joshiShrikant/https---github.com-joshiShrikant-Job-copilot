package com.eurojobcopilot.modules.sponsorship.service;

import com.eurojobcopilot.core.exception.BusinessValidationException;
import com.eurojobcopilot.core.exception.ResourceNotFoundException;
import com.eurojobcopilot.core.exception.UnauthorizedAccessException;
import com.eurojobcopilot.modules.auth.entity.User;
import com.eurojobcopilot.modules.auth.repository.UserRepository;
import com.eurojobcopilot.modules.auth.security.AuthenticatedUserPrincipal;
import com.eurojobcopilot.modules.job.entity.Job;
import com.eurojobcopilot.modules.job.entity.JobAiAnalysis;
import com.eurojobcopilot.modules.job.normalization.CanonicalHashGenerator;
import com.eurojobcopilot.modules.job.repository.JobAiAnalysisRepository;
import com.eurojobcopilot.modules.job.repository.JobRepository;
import com.eurojobcopilot.modules.sponsorship.catalog.CountrySponsorshipCatalog;
import com.eurojobcopilot.modules.sponsorship.catalog.CountrySponsorshipProfile;
import com.eurojobcopilot.modules.sponsorship.dto.CountrySponsorshipProfileDto;
import com.eurojobcopilot.modules.sponsorship.dto.OfficialSponsorRegistryEntryDto;
import com.eurojobcopilot.modules.sponsorship.dto.PagedIndRegistryResponse;
import com.eurojobcopilot.modules.sponsorship.dto.PagedSponsorshipHistoryResponse;
import com.eurojobcopilot.modules.sponsorship.dto.RegistrySyncStatusDto;
import com.eurojobcopilot.modules.sponsorship.dto.SponsorshipVerificationHistoryItemDto;
import com.eurojobcopilot.modules.sponsorship.dto.SponsorshipVerificationResponseDto;
import com.eurojobcopilot.modules.sponsorship.entity.Company;
import com.eurojobcopilot.modules.sponsorship.entity.CompanySponsorshipRecord;
import com.eurojobcopilot.modules.sponsorship.entity.OfficialSponsorRegistryEntry;
import com.eurojobcopilot.modules.sponsorship.entity.SponsorshipRegistrySyncLog;
import com.eurojobcopilot.modules.sponsorship.entity.SponsorshipVerificationHistory;
import com.eurojobcopilot.modules.sponsorship.ind.IndRegisterSyncService;
import com.eurojobcopilot.modules.sponsorship.ind.SponsorshipClassifier;
import com.eurojobcopilot.modules.sponsorship.repository.CompanyRepository;
import com.eurojobcopilot.modules.sponsorship.repository.CompanySponsorshipRecordRepository;
import com.eurojobcopilot.modules.sponsorship.repository.OfficialSponsorRegistryEntryRepository;
import com.eurojobcopilot.modules.sponsorship.repository.SponsorshipRegistrySyncLogRepository;
import com.eurojobcopilot.modules.sponsorship.repository.SponsorshipVerificationHistoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class SponsorshipIntelligenceServiceImpl implements SponsorshipIntelligenceService {

    private final UserRepository userRepository;
    private final CompanyRepository companyRepository;
    private final CompanySponsorshipRecordRepository companySponsorshipRecordRepository;
    private final OfficialSponsorRegistryEntryRepository registryEntryRepository;
    private final SponsorshipVerificationHistoryRepository verificationHistoryRepository;
    private final SponsorshipRegistrySyncLogRepository syncLogRepository;
    private final JobRepository jobRepository;
    private final JobAiAnalysisRepository jobAiAnalysisRepository;
    private final CountrySponsorshipCatalog countryCatalog;
    private final CanonicalHashGenerator canonicalHashGenerator;
    private final SponsorshipClassifier classifier;
    private final IndRegisterSyncService indSyncService;

    @Override
    @Transactional(readOnly = true)
    public List<CountrySponsorshipProfileDto> getCountryProfiles() {
        Optional<SponsorshipRegistrySyncLog> indLatestLog = syncLogRepository
                .findTopByRegistrySourceOrderBySyncedAtDescIdDesc(CountrySponsorshipCatalog.NL_IND_REGISTRY_SOURCE);

        return countryCatalog.getAllCountries().stream()
                .map(profile -> toCountryDto(profile, indLatestLog.orElse(null)))
                .toList();
    }

    @Override
    @Transactional
    public SponsorshipVerificationResponseDto verifyCompanySponsorship(
            AuthenticatedUserPrincipal currentUser,
            String companyName,
            String country,
            Long jobId,
            String postingText
    ) {
        User user = requireAuthenticatedUser(currentUser);
        String trimmedCompany = requireValidCompanyName(companyName);
        CountrySponsorshipProfile countryProfile = countryCatalog.requireSupportedCountry(country);

        Job linkedJob = null;
        JobAiAnalysis linkedAiAnalysis = null;
        if (jobId != null) {
            if (jobId <= 0) {
                throw new BusinessValidationException("Job ID must be a positive integer");
            }
            linkedJob = jobRepository.findByIdWithCompany(jobId)
                    .orElseThrow(() -> new ResourceNotFoundException("Job not found with id: " + jobId));
            linkedAiAnalysis = jobAiAnalysisRepository.findByJobId(jobId).orElse(null);
        }

        String normalizedCompanyKey = resolveNormalizedCompanyKey(trimmedCompany);
        String lowerTrimmed = trimmedCompany.toLowerCase(Locale.ROOT);

        Company company = resolveCompany(linkedJob, trimmedCompany, normalizedCompanyKey, lowerTrimmed);
        List<CompanySponsorshipRecord> countryRecords = company != null
                ? companySponsorshipRecordRepository.findAllByCompanyIdOrderByVerifiedAtDesc(company.getId())
                        .stream()
                        .filter(rec -> countryProfile.country().equalsIgnoreCase(rec.getCountry()))
                        .toList()
                : List.of();

        OfficialSponsorRegistryEntry registryEntry = resolveRegistryEntry(
                countryProfile.country(),
                normalizedCompanyKey,
                lowerTrimmed
        );

        boolean officialRegisterSynced = registryEntryRepository.countByCountryIgnoreCaseAndActiveTrue(
                countryProfile.country()) > 0;

        SponsorshipClassifier.VacancyAssessment vacancyAssessment =
                classifier.assessVacancy(linkedJob, linkedAiAnalysis, postingText);

        SponsorshipClassifier.ClassificationResult result = classifier.classify(
                trimmedCompany,
                countryProfile,
                company,
                countryRecords,
                registryEntry,
                vacancyAssessment,
                officialRegisterSynced,
                linkedJob
        );

        Instant verifiedAt = Instant.now();

        SponsorshipVerificationHistory historyEntry = verificationHistoryRepository.saveAndFlush(
                SponsorshipVerificationHistory.builder()
                        .company(company)
                        .job(linkedJob)
                        .triggeredByUser(user)
                        .companyName(truncate(trimmedCompany, 255))
                        .normalizedCompanyName(truncate(normalizedCompanyKey, 255))
                        .country(countryProfile.country())
                        .countryIsoCode(countryProfile.isoCode())
                        .status(result.status())
                        .vacancyStatus(result.vacancyStatus())
                        .vacancySponsorshipConfirmed(result.vacancySponsorshipConfirmed())
                        .vacancySponsorshipAssumed(result.vacancySponsorshipAssumed())
                        .confidenceLevel(result.confidenceLevel())
                        .registrySource(truncate(result.registrySource(), 100))
                        .registryIdentifier(truncate(result.registryIdentifier(), 150))
                        .officialRegisteredName(truncate(result.officialRegisteredName(), 255))
                        .evidenceSummary(result.evidenceSummary())
                        .evidenceSignals(result.evidenceSignals())
                        .sourceUrl(truncate(result.sourceUrl(), 500))
                        .vacancyCaveat(result.vacancyCaveat())
                        .verifiedAt(verifiedAt)
                        .build()
        );

        if (company != null) {
            for (CompanySponsorshipRecord rec : countryRecords) {
                rec.setLastCheckedAt(verifiedAt);
                if (rec.getConfidenceLevel() == null) {
                    rec.setConfidenceLevel(result.confidenceLevel());
                }
            }
            if (!countryRecords.isEmpty()) {
                companySponsorshipRecordRepository.saveAll(countryRecords);
            }
        }

        List<SponsorshipVerificationHistoryItemDto> recentHistory = verificationHistoryRepository
                .findTop5ByTriggeredByUserIdAndNormalizedCompanyNameAndCountryIgnoreCaseOrderByVerifiedAtDescIdDesc(
                        user.getId(),
                        normalizedCompanyKey,
                        countryProfile.country()
                )
                .stream()
                .map(this::toHistoryItemDto)
                .toList();

        Optional<SponsorshipRegistrySyncLog> indLatestLog =
                CountrySponsorshipCatalog.NL_IND_REGISTRY_SOURCE.equals(countryProfile.registrySource())
                        ? syncLogRepository.findTopByRegistrySourceOrderBySyncedAtDescIdDesc(
                                CountrySponsorshipCatalog.NL_IND_REGISTRY_SOURCE)
                        : Optional.empty();

        log.info("Sponsorship verification userUuid={} company={} country={} status={} vacancyConfirmed={}",
                user.getUuid(), normalizedCompanyKey, countryProfile.country(),
                result.status(), result.vacancySponsorshipConfirmed());

        return SponsorshipVerificationResponseDto.builder()
                .verificationId(historyEntry.getId())
                .companyId(company != null ? company.getId() : null)
                .jobId(linkedJob != null ? linkedJob.getId() : null)
                .companyName(trimmedCompany)
                .normalizedCompanyName(normalizedCompanyKey)
                .country(countryProfile.country())
                .countryIsoCode(countryProfile.isoCode())
                .status(result.status())
                .statusLabel(result.status().getLabel())
                .vacancyStatus(result.vacancyStatus())
                .vacancyStatusLabel(result.vacancyStatus().getLabel())
                .vacancySponsorshipConfirmed(result.vacancySponsorshipConfirmed())
                .vacancySponsorshipAssumed(result.vacancySponsorshipAssumed())
                .companyRecognizedSponsor(result.companyRecognizedSponsor())
                .confidenceLevel(result.confidenceLevel())
                .registrySource(result.registrySource())
                .registryIdentifier(result.registryIdentifier())
                .officialName(result.officialRegisteredName())
                .evidenceNotes(result.evidenceSummary())
                .evidenceSignals(result.evidenceSignals())
                .sourceUrl(result.sourceUrl())
                .vacancyCaveat(result.vacancyCaveat())
                .verifiedAt(verifiedAt)
                .countryProfile(toCountryDto(countryProfile, indLatestLog.orElse(null)))
                .recentHistory(recentHistory)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public PagedSponsorshipHistoryResponse getVerificationHistory(
            AuthenticatedUserPrincipal currentUser,
            String companyName,
            String country,
            int page,
            int size
    ) {
        User user = requireAuthenticatedUser(currentUser);
        String canonicalCountry = null;
        if (country != null && !country.isBlank()) {
            canonicalCountry = countryCatalog.requireSupportedCountry(country).country();
        }
        String companyFilter = (companyName != null && !companyName.isBlank()) ? companyName.trim() : null;

        int safePage = Math.max(0, page);
        int safeSize = Math.min(100, Math.max(1, size));

        Page<SponsorshipVerificationHistory> resultPage = verificationHistoryRepository.findUserHistoryFiltered(
                user.getId(),
                companyFilter,
                canonicalCountry,
                PageRequest.of(safePage, safeSize)
        );

        return PagedSponsorshipHistoryResponse.builder()
                .content(resultPage.getContent().stream().map(this::toHistoryItemDto).toList())
                .pageNumber(resultPage.getNumber())
                .pageSize(resultPage.getSize())
                .totalElements(resultPage.getTotalElements())
                .totalPages(resultPage.getTotalPages())
                .first(resultPage.isFirst())
                .last(resultPage.isLast())
                .hasNext(resultPage.hasNext())
                .hasPrevious(resultPage.hasPrevious())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public PagedIndRegistryResponse searchIndRegistry(
            AuthenticatedUserPrincipal currentUser,
            String query,
            int page,
            int size
    ) {
        requireAuthenticatedUser(currentUser);
        int safePage = Math.max(0, page);
        int safeSize = Math.min(100, Math.max(1, size));
        String cleanedQuery = (query != null && !query.isBlank()) ? query.trim() : null;

        Page<OfficialSponsorRegistryEntry> pageResult = registryEntryRepository.searchActiveByRegistrySource(
                CountrySponsorshipCatalog.NL_IND_REGISTRY_SOURCE,
                cleanedQuery,
                PageRequest.of(safePage, safeSize)
        );

        RegistrySyncStatusDto latestSync = indSyncService.getLatestSyncStatus();

        return PagedIndRegistryResponse.builder()
                .content(pageResult.getContent().stream().map(this::toRegistryEntryDto).toList())
                .lastSyncStatus(latestSync)
                .pageNumber(pageResult.getNumber())
                .pageSize(pageResult.getSize())
                .totalElements(pageResult.getTotalElements())
                .totalPages(pageResult.getTotalPages())
                .first(pageResult.isFirst())
                .last(pageResult.isLast())
                .hasNext(pageResult.hasNext())
                .hasPrevious(pageResult.hasPrevious())
                .build();
    }

    private User requireAuthenticatedUser(AuthenticatedUserPrincipal principal) {
        if (principal == null || principal.getId() == null) {
            throw new UnauthorizedAccessException("Authenticated user context is required");
        }
        return userRepository.findById(principal.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Authenticated user no longer exists"));
    }

    private String requireValidCompanyName(String companyName) {
        if (companyName == null || companyName.isBlank()) {
            throw new BusinessValidationException("Company name is required for visa sponsorship verification");
        }
        return truncate(companyName.trim().replaceAll("\\s+", " "), 255);
    }

    private String resolveNormalizedCompanyKey(String companyName) {
        String key = canonicalHashGenerator.normalizeCompanyKey(companyName);
        if (!key.isBlank()) {
            return key;
        }
        return companyName.trim().toLowerCase(Locale.ROOT);
    }

    private Company resolveCompany(
            Job linkedJob,
            String trimmedCompany,
            String normalizedCompanyKey,
            String lowerTrimmed
    ) {
        if (linkedJob != null && linkedJob.getCompany() != null) {
            Company linked = linkedJob.getCompany();
            String linkedKey = canonicalHashGenerator.normalizeCompanyKey(linked.getName());
            if (linkedKey.equals(normalizedCompanyKey) || linked.getName().equalsIgnoreCase(trimmedCompany)) {
                return linked;
            }
        }
        Optional<Company> byKey = companyRepository.findByNormalizedName(normalizedCompanyKey);
        if (byKey.isPresent()) {
            return byKey.get();
        }
        if (!lowerTrimmed.equals(normalizedCompanyKey)) {
            return companyRepository.findByNormalizedName(lowerTrimmed).orElse(null);
        }
        return null;
    }

    private OfficialSponsorRegistryEntry resolveRegistryEntry(
            String country,
            String normalizedCompanyKey,
            String lowerTrimmed
    ) {
        Optional<OfficialSponsorRegistryEntry> byCanonical = registryEntryRepository
                .findFirstByCountryIgnoreCaseAndNormalizedNameIgnoreCaseAndActiveTrueOrderBySyncedAtDesc(
                        country, normalizedCompanyKey);
        if (byCanonical.isPresent()) {
            return byCanonical.get();
        }
        if (!lowerTrimmed.equals(normalizedCompanyKey)) {
            return registryEntryRepository
                    .findFirstByCountryIgnoreCaseAndNormalizedNameIgnoreCaseAndActiveTrueOrderBySyncedAtDesc(
                            country, lowerTrimmed)
                    .orElse(null);
        }
        return null;
    }

    private CountrySponsorshipProfileDto toCountryDto(
            CountrySponsorshipProfile profile,
            SponsorshipRegistrySyncLog syncLog
    ) {
        long syncedCount = registryEntryRepository.countByCountryIgnoreCaseAndActiveTrue(profile.country());
        Instant lastSyncedAt = (syncLog != null && profile.country().equalsIgnoreCase(syncLog.getCountry()))
                ? syncLog.getSyncedAt()
                : null;
        return CountrySponsorshipProfileDto.builder()
                .country(profile.country())
                .isoCode(profile.isoCode())
                .officialRegisterAvailable(profile.officialRegisterAvailable())
                .registrySource(profile.registrySource())
                .registryName(profile.registryName())
                .primaryAuthority(profile.primaryAuthority())
                .officialSourceUrl(profile.officialSourceUrl())
                .publicRegisterUrl(profile.publicRegisterUrl())
                .verificationMethod(profile.verificationMethod())
                .visaPathways(profile.visaPathways())
                .guidanceSummary(profile.guidanceSummary())
                .vacancyRuleWarning(profile.vacancyRuleWarning())
                .syncedEntriesCount(syncedCount)
                .lastSyncedAt(lastSyncedAt)
                .build();
    }

    private SponsorshipVerificationHistoryItemDto toHistoryItemDto(SponsorshipVerificationHistory entity) {
        return SponsorshipVerificationHistoryItemDto.builder()
                .id(entity.getId())
                .companyId(entity.getCompany() != null ? entity.getCompany().getId() : null)
                .jobId(entity.getJob() != null ? entity.getJob().getId() : null)
                .companyName(entity.getCompanyName())
                .normalizedCompanyName(entity.getNormalizedCompanyName())
                .country(entity.getCountry())
                .countryIsoCode(entity.getCountryIsoCode())
                .status(entity.getStatus())
                .statusLabel(entity.getStatus() != null ? entity.getStatus().getLabel() : null)
                .vacancyStatus(entity.getVacancyStatus())
                .vacancySponsorshipConfirmed(entity.isVacancySponsorshipConfirmed())
                .vacancySponsorshipAssumed(entity.isVacancySponsorshipAssumed())
                .confidenceLevel(entity.getConfidenceLevel())
                .registrySource(entity.getRegistrySource())
                .registryIdentifier(entity.getRegistryIdentifier())
                .officialRegisteredName(entity.getOfficialRegisteredName())
                .evidenceSummary(entity.getEvidenceSummary())
                .evidenceSignals(entity.getEvidenceSignals() != null ? List.copyOf(entity.getEvidenceSignals()) : List.of())
                .sourceUrl(entity.getSourceUrl())
                .vacancyCaveat(entity.getVacancyCaveat())
                .verifiedAt(entity.getVerifiedAt())
                .build();
    }

    private OfficialSponsorRegistryEntryDto toRegistryEntryDto(OfficialSponsorRegistryEntry entry) {
        return OfficialSponsorRegistryEntryDto.builder()
                .id(entry.getId())
                .country(entry.getCountry())
                .registrySource(entry.getRegistrySource())
                .registryIdentifier(entry.getRegistryIdentifier())
                .officialName(entry.getOfficialName())
                .normalizedName(entry.getNormalizedName())
                .status(entry.getStatus())
                .sourceUrl(entry.getSourceUrl())
                .active(entry.isActive())
                .syncedAt(entry.getSyncedAt())
                .build();
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength).trim();
    }
}
