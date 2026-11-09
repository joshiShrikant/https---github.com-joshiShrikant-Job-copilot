package com.eurojobcopilot.modules.job.normalization;

import com.eurojobcopilot.modules.job.entity.Job;
import com.eurojobcopilot.modules.job.entity.JobSponsorStatus;
import com.eurojobcopilot.modules.job.repository.JobRepository;
import com.eurojobcopilot.modules.sponsorship.entity.Company;
import com.eurojobcopilot.modules.sponsorship.entity.CompanySponsorshipRecord;
import com.eurojobcopilot.modules.sponsorship.entity.OfficialSponsorRegistryEntry;
import com.eurojobcopilot.modules.sponsorship.entity.SponsorConfidenceLevel;
import com.eurojobcopilot.modules.sponsorship.entity.SponsorshipRecordStatus;
import com.eurojobcopilot.modules.sponsorship.repository.CompanyRepository;
import com.eurojobcopilot.modules.sponsorship.repository.CompanySponsorshipRecordRepository;
import com.eurojobcopilot.modules.sponsorship.repository.OfficialSponsorRegistryEntryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Resolves company records and enforces deterministic 2-tier deduplication:
 * <ol>
 *     <li>Exact provider identity: {@code (sourceProvider, sourceJobId)}</li>
 *     <li>Cross-provider canonical SHA-256 identity: {@code canonicalHash}</li>
 * </ol>
 */
@Service
@RequiredArgsConstructor
public class DeduplicationServiceImpl implements DeduplicationService {

    private final JobRepository jobRepository;
    private final CompanyRepository companyRepository;
    private final CompanySponsorshipRecordRepository companySponsorshipRecordRepository;
    private final OfficialSponsorRegistryEntryRepository officialSponsorRegistryEntryRepository;

    @Override
    @Transactional
    public DeduplicationOutcome upsertNormalizedJob(NormalizedJobData data) {
        if (data == null) {
            return DeduplicationOutcome.SKIPPED_DUPLICATE;
        }

        Company company = resolveOrCreateCompany(data);
        JobSponsorStatus effectiveSponsorStatus = resolveSponsorStatus(data.sponsorStatus(), company);

        Optional<Job> existingByProvider = findExistingByProviderId(data);
        Optional<Job> existingOpt = existingByProvider.isPresent()
                ? existingByProvider
                : jobRepository.findByCanonicalHash(data.canonicalHash());

        if (existingOpt.isEmpty()) {
            Job newJob = Job.builder()
                    .canonicalHash(data.canonicalHash())
                    .company(company)
                    .companyName(data.companyName())
                    .title(data.title())
                    .normalizedTitle(data.normalizedTitle())
                    .location(data.location())
                    .city(data.city())
                    .country(data.country())
                    .workArrangement(data.workArrangement())
                    .employmentType(data.employmentType())
                    .experienceLevel(data.experienceLevel())
                    .salaryMin(data.salaryMin())
                    .salaryMax(data.salaryMax())
                    .salaryCurrency(data.salaryCurrency())
                    .salaryPeriod(data.salaryPeriod())
                    .description(data.description())
                    .descriptionSnippet(data.descriptionSnippet())
                    .skills(new ArrayList<>(data.skills()))
                    .sourceProvider(data.sourceProvider())
                    .sourceJobId(data.sourceJobId())
                    .originalUrl(data.originalUrl())
                    .applicationUrl(data.applicationUrl())
                    .sponsorStatus(effectiveSponsorStatus)
                    .postedAt(data.postedAt())
                    .expiresAt(data.expiresAt())
                    .active(true)
                    .build();
            jobRepository.save(newJob);
            return DeduplicationOutcome.INSERTED;
        }

        Job existing = existingOpt.get();
        boolean sameProvider = Objects.equals(existing.getSourceProvider(), data.sourceProvider());
        boolean changed = sameProvider
                ? applySameProviderUpdates(existing, data, company, effectiveSponsorStatus)
                : applyCrossProviderEnrichment(existing, data, company, effectiveSponsorStatus);

        if (!changed) {
            return DeduplicationOutcome.SKIPPED_DUPLICATE;
        }

        jobRepository.save(existing);
        return DeduplicationOutcome.UPDATED;
    }

    private Optional<Job> findExistingByProviderId(NormalizedJobData data) {
        if (data.sourceJobId() == null || data.sourceJobId().isBlank()) {
            return Optional.empty();
        }
        return jobRepository.findBySourceProviderAndSourceJobId(data.sourceProvider(), data.sourceJobId());
    }

    private Company resolveOrCreateCompany(NormalizedJobData data) {
        Optional<OfficialSponsorRegistryEntry> registryMatchOpt = (!isBlank(data.country()) && !isBlank(data.normalizedCompanyName()))
                ? officialSponsorRegistryEntryRepository
                        .findFirstByCountryIgnoreCaseAndNormalizedNameIgnoreCaseAndActiveTrueOrderBySyncedAtDesc(
                                data.country(), data.normalizedCompanyName())
                : Optional.empty();

        Company company = companyRepository.findByNormalizedName(data.normalizedCompanyName())
                .map(existing -> {
                    boolean dirty = false;
                    if (isBlank(existing.getHeadquartersCountry()) && !isBlank(data.country())) {
                        existing.setHeadquartersCountry(data.country());
                        dirty = true;
                    }
                    if (isBlank(existing.getHeadquartersCity()) && !isBlank(data.city())) {
                        existing.setHeadquartersCity(data.city());
                        dirty = true;
                    }
                    if (registryMatchOpt.isPresent()
                            && (!existing.isVerifiedSponsor()
                            || existing.getSponsorConfidenceLevel() != SponsorConfidenceLevel.VERIFIED)) {
                        existing.setVerifiedSponsor(true);
                        existing.setSponsorConfidenceLevel(SponsorConfidenceLevel.VERIFIED);
                        dirty = true;
                    }
                    return dirty ? companyRepository.save(existing) : existing;
                })
                .orElseGet(() -> companyRepository.save(Company.builder()
                        .name(data.companyName())
                        .normalizedName(data.normalizedCompanyName())
                        .headquartersCountry(data.country())
                        .headquartersCity(data.city())
                        .verifiedSponsor(registryMatchOpt.isPresent())
                        .sponsorConfidenceLevel(registryMatchOpt.isPresent()
                                ? SponsorConfidenceLevel.VERIFIED
                                : SponsorConfidenceLevel.UNVERIFIED)
                        .build()));

        if (registryMatchOpt.isPresent()) {
            OfficialSponsorRegistryEntry match = registryMatchOpt.get();
            CompanySponsorshipRecord record = companySponsorshipRecordRepository
                    .findByCompanyIdAndCountryIgnoreCaseAndRegistrySourceIgnoreCase(
                            company.getId(), match.getCountry(), match.getRegistrySource())
                    .orElseGet(() -> CompanySponsorshipRecord.builder()
                            .company(company)
                            .country(match.getCountry())
                            .registrySource(match.getRegistrySource())
                            .build());
            record.setRegistryIdentifier(match.getRegistryIdentifier());
            record.setOfficialRegisteredName(match.getOfficialName());
            record.setStatus(match.getStatus() != null ? match.getStatus() : SponsorshipRecordStatus.RECOGNIZED_SPONSOR);
            record.setConfidenceLevel(SponsorConfidenceLevel.VERIFIED);
            record.setEvidenceNotes(match.getEvidenceNotes());
            record.setSourceUrl(match.getSourceUrl());
            record.setVerifiedAt(match.getSyncedAt() != null ? match.getSyncedAt() : Instant.now());
            record.setLastCheckedAt(Instant.now());
            companySponsorshipRecordRepository.save(record);
        }

        return company;
    }

    private JobSponsorStatus resolveSponsorStatus(JobSponsorStatus rawStatus, Company company) {
        // Never allow company-level recognized sponsor status to override an explicit negative vacancy signal.
        if (rawStatus == JobSponsorStatus.NO_SPONSORSHIP) {
            return JobSponsorStatus.NO_SPONSORSHIP;
        }
        if (company != null && company.isVerifiedSponsor()) {
            return JobSponsorStatus.VERIFIED_SPONSOR;
        }
        return rawStatus != null ? rawStatus : JobSponsorStatus.UNCONFIRMED;
    }

    private boolean applySameProviderUpdates(
            Job existing,
            NormalizedJobData data,
            Company company,
            JobSponsorStatus effectiveSponsorStatus
    ) {
        boolean changed = false;

        if (!Objects.equals(existing.getCompany(), company)) {
            existing.setCompany(company);
            changed = true;
        }
        if (!Objects.equals(existing.getTitle(), data.title())) {
            existing.setTitle(data.title());
            existing.setNormalizedTitle(data.normalizedTitle());
            changed = true;
        }
        if (!Objects.equals(existing.getLocation(), data.location())) {
            existing.setLocation(data.location());
            existing.setCity(data.city());
            existing.setCountry(data.country());
            changed = true;
        }
        if (existing.getWorkArrangement() != data.workArrangement()) {
            existing.setWorkArrangement(data.workArrangement());
            changed = true;
        }
        if (existing.getEmploymentType() != data.employmentType()) {
            existing.setEmploymentType(data.employmentType());
            changed = true;
        }
        if (existing.getExperienceLevel() != data.experienceLevel()) {
            existing.setExperienceLevel(data.experienceLevel());
            changed = true;
        }
        if (!Objects.equals(existing.getDescription(), data.description())) {
            existing.setDescription(data.description());
            existing.setDescriptionSnippet(data.descriptionSnippet());
            changed = true;
        }
        if (!Objects.equals(existing.getSalaryMin(), data.salaryMin())
                || !Objects.equals(existing.getSalaryMax(), data.salaryMax())
                || !Objects.equals(existing.getSalaryCurrency(), data.salaryCurrency())
                || existing.getSalaryPeriod() != data.salaryPeriod()) {
            existing.setSalaryMin(data.salaryMin());
            existing.setSalaryMax(data.salaryMax());
            existing.setSalaryCurrency(data.salaryCurrency());
            existing.setSalaryPeriod(data.salaryPeriod());
            changed = true;
        }
        if (mergeSkillsInto(existing, data.skills())) {
            changed = true;
        }
        if (!Objects.equals(existing.getApplicationUrl(), data.applicationUrl()) && !isBlank(data.applicationUrl())) {
            existing.setApplicationUrl(data.applicationUrl());
            changed = true;
        }
        if (existing.getSponsorStatus() != effectiveSponsorStatus) {
            existing.setSponsorStatus(effectiveSponsorStatus);
            changed = true;
        }
        if (!existing.isActive()) {
            existing.setActive(true);
            changed = true;
        }
        if (data.expiresAt() != null && !Objects.equals(existing.getExpiresAt(), data.expiresAt())) {
            existing.setExpiresAt(data.expiresAt());
            changed = true;
        }
        if (!Objects.equals(existing.getCanonicalHash(), data.canonicalHash())
                && !jobRepository.existsByCanonicalHash(data.canonicalHash())) {
            existing.setCanonicalHash(data.canonicalHash());
            changed = true;
        }

        return changed;
    }

    private boolean applyCrossProviderEnrichment(
            Job existing,
            NormalizedJobData data,
            Company company,
            JobSponsorStatus effectiveSponsorStatus
    ) {
        boolean enriched = false;

        if (existing.getCompany() == null && company != null) {
            existing.setCompany(company);
            enriched = true;
        }
        if (existing.getSalaryMin() == null && existing.getSalaryMax() == null
                && (data.salaryMin() != null || data.salaryMax() != null)) {
            existing.setSalaryMin(data.salaryMin());
            existing.setSalaryMax(data.salaryMax());
            existing.setSalaryCurrency(data.salaryCurrency());
            existing.setSalaryPeriod(data.salaryPeriod());
            enriched = true;
        }
        if (data.description().length() > existing.getDescription().length() + 40) {
            existing.setDescription(data.description());
            existing.setDescriptionSnippet(data.descriptionSnippet());
            enriched = true;
        }
        if (mergeSkillsInto(existing, data.skills())) {
            enriched = true;
        }
        if (sponsorRank(effectiveSponsorStatus) > sponsorRank(existing.getSponsorStatus())) {
            existing.setSponsorStatus(effectiveSponsorStatus);
            enriched = true;
        }
        boolean existingOnlyFallbackApply = isBlank(existing.getApplicationUrl())
                || Objects.equals(existing.getApplicationUrl(), existing.getOriginalUrl());
        if (existingOnlyFallbackApply
                && !isBlank(data.applicationUrl())
                && !Objects.equals(data.applicationUrl(), existing.getOriginalUrl())) {
            existing.setApplicationUrl(data.applicationUrl());
            enriched = true;
        }
        if (!existing.isActive()) {
            existing.setActive(true);
            enriched = true;
        }

        return enriched;
    }

    private boolean mergeSkillsInto(Job existing, List<String> incomingSkills) {
        if (incomingSkills == null || incomingSkills.isEmpty()) {
            return false;
        }
        List<String> current = existing.getSkills() == null ? List.of() : existing.getSkills();
        Set<String> merged = new LinkedHashSet<>(current);
        int initialSize = merged.size();
        for (String skill : incomingSkills) {
            if (!isBlank(skill) && merged.stream().noneMatch(s -> s.equalsIgnoreCase(skill))) {
                merged.add(skill);
            }
        }
        if (merged.size() > initialSize) {
            existing.setSkills(new ArrayList<>(merged));
            return true;
        }
        return false;
    }

    private int sponsorRank(JobSponsorStatus status) {
        if (status == null) {
            return 0;
        }
        return switch (status) {
            case NO_SPONSORSHIP, UNCONFIRMED -> 0;
            case RELOCATION_OFFERED -> 1;
            case SPONSORSHIP_MENTIONED -> 2;
            case VERIFIED_SPONSOR -> 3;
        };
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}

