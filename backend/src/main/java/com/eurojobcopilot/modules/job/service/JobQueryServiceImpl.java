package com.eurojobcopilot.modules.job.service;

import com.eurojobcopilot.core.exception.BusinessValidationException;
import com.eurojobcopilot.core.exception.ResourceNotFoundException;
import com.eurojobcopilot.core.exception.UnauthorizedAccessException;
import com.eurojobcopilot.modules.application.entity.ApplicationStatus;
import com.eurojobcopilot.modules.application.entity.JobApplication;
import com.eurojobcopilot.modules.application.repository.JobApplicationRepository;
import com.eurojobcopilot.modules.auth.security.AuthenticatedUserPrincipal;
import com.eurojobcopilot.modules.job.dto.CompanySponsorshipSummaryDto;
import com.eurojobcopilot.modules.job.dto.JobAiAnalysisDto;
import com.eurojobcopilot.modules.job.dto.JobApplicationSummaryDto;
import com.eurojobcopilot.modules.job.dto.JobDetailDto;
import com.eurojobcopilot.modules.job.dto.JobMatchBreakdownDto;
import com.eurojobcopilot.modules.job.dto.JobSearchCriteria;
import com.eurojobcopilot.modules.job.dto.JobSortOption;
import com.eurojobcopilot.modules.job.dto.JobSummaryDto;
import com.eurojobcopilot.modules.job.dto.PagedJobsResponse;
import com.eurojobcopilot.modules.job.dto.SponsorshipBreakdownDto;
import com.eurojobcopilot.modules.job.dto.SponsorshipRegistryRecordDto;
import com.eurojobcopilot.modules.job.entity.Job;
import com.eurojobcopilot.modules.job.entity.JobAiAnalysis;
import com.eurojobcopilot.modules.job.entity.JobSponsorStatus;
import com.eurojobcopilot.modules.job.entity.UserJobInteraction;
import com.eurojobcopilot.modules.job.normalization.CanonicalHashGenerator;
import com.eurojobcopilot.modules.job.normalization.JobNormalizer;
import com.eurojobcopilot.modules.job.repository.JobAiAnalysisRepository;
import com.eurojobcopilot.modules.job.repository.JobRepository;
import com.eurojobcopilot.modules.job.repository.UserJobInteractionRepository;
import com.eurojobcopilot.modules.matching.entity.JobMatchScore;
import com.eurojobcopilot.modules.matching.mapper.JobMatchBreakdownMapper;
import com.eurojobcopilot.modules.matching.repository.JobMatchScoreRepository;
import com.eurojobcopilot.modules.matching.service.JobMatchingService;
import com.eurojobcopilot.modules.sponsorship.catalog.CountrySponsorshipCatalog;
import com.eurojobcopilot.modules.sponsorship.catalog.CountrySponsorshipProfile;
import com.eurojobcopilot.modules.sponsorship.entity.Company;
import com.eurojobcopilot.modules.sponsorship.entity.CompanySponsorshipRecord;
import com.eurojobcopilot.modules.sponsorship.entity.OfficialSponsorRegistryEntry;
import com.eurojobcopilot.modules.sponsorship.entity.SponsorConfidenceLevel;
import com.eurojobcopilot.modules.sponsorship.entity.SponsorshipRecordStatus;
import com.eurojobcopilot.modules.sponsorship.entity.VisaSponsorshipStatus;
import com.eurojobcopilot.modules.sponsorship.ind.SponsorshipClassifier;
import com.eurojobcopilot.modules.sponsorship.repository.CompanyRepository;
import com.eurojobcopilot.modules.sponsorship.repository.CompanySponsorshipRecordRepository;
import com.eurojobcopilot.modules.sponsorship.repository.OfficialSponsorRegistryEntryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class JobQueryServiceImpl implements JobQueryService {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;
    private static final int SNIPPET_MAX_LENGTH = 500;

    private final JobRepository jobRepository;
    private final UserJobInteractionRepository userJobInteractionRepository;
    private final JobMatchScoreRepository jobMatchScoreRepository;
    private final JobAiAnalysisRepository jobAiAnalysisRepository;
    private final CompanyRepository companyRepository;
    private final CompanySponsorshipRecordRepository companySponsorshipRecordRepository;
    private final OfficialSponsorRegistryEntryRepository officialSponsorRegistryEntryRepository;
    private final JobApplicationRepository jobApplicationRepository;
    private final JobNormalizer jobNormalizer;
    private final CanonicalHashGenerator canonicalHashGenerator;
    private final CountrySponsorshipCatalog countrySponsorshipCatalog;
    private final SponsorshipClassifier sponsorshipClassifier;
    private final JobMatchingService jobMatchingService;

    @Override
    @Transactional
    public PagedJobsResponse searchJobs(AuthenticatedUserPrincipal currentUser, JobSearchCriteria criteria) {
        Long userId = requireUserId(currentUser);

        JobSearchCriteria effectiveCriteria = criteria != null
                ? criteria
                : JobSearchCriteria.builder()
                        .page(0)
                        .size(DEFAULT_PAGE_SIZE)
                        .sort(JobSortOption.POSTED_AT_DESC.getParamValue())
                        .build();

        JobSortOption sortOption = JobSortOption.fromParam(effectiveCriteria.sort());
        int page = Math.max(0, effectiveCriteria.page());
        int requestedSize = effectiveCriteria.size() > 0 ? effectiveCriteria.size() : DEFAULT_PAGE_SIZE;
        int size = Math.min(MAX_PAGE_SIZE, requestedSize);

        jobMatchingService.ensureActiveMatchesUpToDate(userId);

        String normalizedCountry = (effectiveCriteria.country() != null && !effectiveCriteria.country().isBlank())
                ? jobNormalizer.normalizeCountry(effectiveCriteria.country())
                : null;

        Page<Job> jobPage = jobRepository.searchActiveJobs(
                userId,
                effectiveCriteria,
                normalizedCountry,
                PageRequest.of(page, size),
                sortOption,
                Instant.now()
        );

        return buildPagedJobsResponse(userId, jobPage);
    }

    @Override
    @Transactional
    public PagedJobsResponse getCompanyActiveJobs(
            AuthenticatedUserPrincipal currentUser,
            Long companyId,
            String companyName,
            boolean includeHidden,
            int page,
            int size,
            String sort
    ) {
        Long userId = requireUserId(currentUser);
        if (companyId == null || companyId <= 0) {
            throw new BusinessValidationException("Company ID must be a positive integer");
        }
        int safePage = Math.max(0, page);
        int requestedSize = size > 0 ? size : DEFAULT_PAGE_SIZE;
        int safeSize = Math.min(MAX_PAGE_SIZE, requestedSize);
        JobSortOption sortOption = JobSortOption.fromParam(sort);

        jobMatchingService.ensureActiveMatchesUpToDate(userId);

        Page<Job> jobPage = jobRepository.searchCompanyActiveJobs(
                userId,
                companyId,
                companyName,
                includeHidden,
                PageRequest.of(safePage, safeSize),
                sortOption,
                Instant.now()
        );
        return buildPagedJobsResponse(userId, jobPage);
    }

    private PagedJobsResponse buildPagedJobsResponse(Long userId, Page<Job> jobPage) {
        List<Job> jobs = jobPage.getContent();
        Map<Long, UserJobInteraction> interactionsByJobId;
        Map<Long, JobMatchScore> matchScoresByJobId;

        if (jobs.isEmpty()) {
            interactionsByJobId = Collections.emptyMap();
            matchScoresByJobId = Collections.emptyMap();
        } else {
            List<Long> jobIds = jobs.stream().map(Job::getId).toList();
            interactionsByJobId = userJobInteractionRepository
                    .findAllByUserIdAndJobIdIn(userId, jobIds)
                    .stream()
                    .collect(Collectors.toMap(
                            interaction -> interaction.getJob().getId(),
                            Function.identity(),
                            (first, second) -> first
                    ));
            matchScoresByJobId = jobMatchScoreRepository
                    .findAllByUserIdAndJobIdIn(userId, jobIds)
                    .stream()
                    .collect(Collectors.toMap(
                            score -> score.getJob().getId(),
                            Function.identity(),
                            (first, second) -> first
                    ));
        }

        List<JobSummaryDto> summaries = jobs.stream()
                .map(job -> toSummaryDto(
                        job,
                        interactionsByJobId.get(job.getId()),
                        matchScoresByJobId.get(job.getId())
                ))
                .toList();

        return PagedJobsResponse.builder()
                .content(summaries)
                .pageNumber(jobPage.getNumber())
                .pageSize(jobPage.getSize())
                .totalElements(jobPage.getTotalElements())
                .totalPages(jobPage.getTotalPages())
                .first(jobPage.isFirst())
                .last(jobPage.isLast())
                .hasNext(jobPage.hasNext())
                .hasPrevious(jobPage.hasPrevious())
                .build();
    }

    @Override
    @Transactional
    public JobDetailDto getJobDetail(AuthenticatedUserPrincipal currentUser, Long jobId) {
        Long userId = requireUserId(currentUser);
        if (jobId == null || jobId <= 0) {
            throw new BusinessValidationException("Job ID must be a positive integer");
        }

        Job job = jobRepository.findByIdWithCompany(jobId)
                .orElseThrow(() -> new ResourceNotFoundException("Job not found with id: " + jobId));

        if (job.isActive() && (job.getExpiresAt() == null || !job.getExpiresAt().isBefore(Instant.now()))) {
            jobMatchingService.ensureActiveMatchesUpToDate(userId);
        }

        Company resolvedCompany = resolveCompany(job);
        List<CompanySponsorshipRecord> registryRecords = resolvedCompany != null
                ? safeList(companySponsorshipRecordRepository.findAllByCompanyIdOrderByVerifiedAtDesc(resolvedCompany.getId()))
                : List.of();

        UserJobInteraction interaction = userJobInteractionRepository.findByUserIdAndJobId(userId, jobId).orElse(null);
        JobMatchScore matchScore = jobMatchScoreRepository.findByUserIdAndJobId(userId, jobId).orElse(null);
        JobAiAnalysis aiAnalysis = jobAiAnalysisRepository.findByJobId(jobId).orElse(null);
        JobApplication application = jobApplicationRepository.findByUserIdAndJobId(userId, jobId).orElse(null);

        boolean hasAppliedFlag = (interaction != null && interaction.isHasApplied())
                || (application != null
                && application.getStatus() != ApplicationStatus.INTERESTED
                && application.getStatus() != ApplicationStatus.SAVED);
        Instant effectiveAppliedAt = interaction != null && interaction.getAppliedAt() != null
                ? interaction.getAppliedAt()
                : (application != null ? application.getAppliedDate() : null);

        log.debug("Loaded job detail jobId={} userId={} companyId={} hasMatch={} hasAiAnalysis={}",
                jobId, userId, resolvedCompany != null ? resolvedCompany.getId() : null,
                matchScore != null, aiAnalysis != null);

        return JobDetailDto.builder()
                .id(job.getId())
                .title(job.getTitle())
                .companyName(job.getCompanyName())
                .country(job.getCountry())
                .city(job.getCity())
                .location(job.getLocation())
                .workArrangement(job.getWorkArrangement())
                .employmentType(job.getEmploymentType())
                .experienceLevel(job.getExperienceLevel())
                .minExperienceYears(job.getMinExperienceYears())
                .salaryMin(job.getSalaryMin())
                .salaryMax(job.getSalaryMax())
                .salaryCurrency(job.getSalaryCurrency())
                .salaryPeriod(job.getSalaryPeriod())
                .description(job.getDescription())
                .descriptionSnippet(resolveSnippet(job))
                .skills(safeCopy(job.getSkills()))
                .sponsorStatus(job.getSponsorStatus())
                .matchPercentage(toMatchPercentage(matchScore))
                .sourceProvider(job.getSourceProvider())
                .sourceJobId(job.getSourceJobId())
                .originalUrl(job.getOriginalUrl())
                .applicationUrl(job.getApplicationUrl())
                .postedAt(job.getPostedAt())
                .expiresAt(job.getExpiresAt())
                .active(job.isActive())
                .saved(interaction != null && interaction.isSaved())
                .savedAt(interaction != null ? interaction.getSavedAt() : null)
                .interested(interaction != null && interaction.isInterested())
                .interestedAt(interaction != null ? interaction.getInterestedAt() : null)
                .hidden(interaction != null && interaction.isHidden())
                .hiddenAt(interaction != null ? interaction.getHiddenAt() : null)
                .hasApplied(hasAppliedFlag)
                .appliedAt(effectiveAppliedAt)
                .redirectClickedAt(interaction != null ? interaction.getRedirectClickedAt() : null)
                .company(toCompanyDto(resolvedCompany))
                .sponsorshipBreakdown(buildSponsorshipBreakdown(job, resolvedCompany, registryRecords, aiAnalysis))
                .matchBreakdown(toMatchBreakdownDto(matchScore))
                .aiAnalysis(toAiAnalysisDto(aiAnalysis))
                .application(toApplicationSummaryDto(application))
                .build();
    }

    private Long requireUserId(AuthenticatedUserPrincipal currentUser) {
        if (currentUser == null || currentUser.getId() == null) {
            throw new UnauthorizedAccessException("Authenticated user context is required");
        }
        return currentUser.getId();
    }

    private Company resolveCompany(Job job) {
        if (job.getCompany() != null) {
            return job.getCompany();
        }
        String rawName = job.getCompanyName();
        if (rawName == null || rawName.isBlank()) {
            return null;
        }
        String canonicalKey = canonicalHashGenerator.normalizeCompanyKey(rawName);
        if (!canonicalKey.isBlank()) {
            Optional<Company> byCanonical = companyRepository.findByNormalizedName(canonicalKey);
            if (byCanonical.isPresent()) {
                return byCanonical.get();
            }
        }
        String lowerTrimmed = rawName.trim().toLowerCase(Locale.ROOT);
        if (!lowerTrimmed.equals(canonicalKey)) {
            return companyRepository.findByNormalizedName(lowerTrimmed).orElse(null);
        }
        return null;
    }

    private CompanySponsorshipSummaryDto toCompanyDto(Company company) {
        if (company == null) {
            return null;
        }
        return CompanySponsorshipSummaryDto.builder()
                .id(company.getId())
                .name(company.getName())
                .websiteUrl(company.getWebsiteUrl())
                .careersUrl(company.getCareersUrl())
                .headquartersCountry(company.getHeadquartersCountry())
                .headquartersCity(company.getHeadquartersCity())
                .industry(company.getIndustry())
                .companySize(company.getCompanySize())
                .logoUrl(company.getLogoUrl())
                .verifiedSponsor(company.isVerifiedSponsor())
                .sponsorConfidenceLevel(company.getSponsorConfidenceLevel())
                .build();
    }

    private SponsorshipBreakdownDto buildSponsorshipBreakdown(
            Job job,
            Company company,
            List<CompanySponsorshipRecord> registryRecords,
            JobAiAnalysis aiAnalysis
    ) {
        JobSponsorStatus status = job.getSponsorStatus() != null
                ? job.getSponsorStatus()
                : JobSponsorStatus.UNCONFIRMED;

        Optional<CountrySponsorshipProfile> countryProfileOpt =
                countrySponsorshipCatalog.findCountryProfile(job.getCountry());
        String canonicalCountry = countryProfileOpt.map(CountrySponsorshipProfile::country).orElse(job.getCountry());

        OfficialSponsorRegistryEntry syncedRegistryMatch = resolveSyncedRegistryEntry(job, canonicalCountry);
        List<CompanySponsorshipRecord> countryScopedRecords = registryRecords.stream()
                .filter(rec -> canonicalCountry == null || canonicalCountry.isBlank()
                        || canonicalCountry.equalsIgnoreCase(rec.getCountry()))
                .toList();

        SponsorshipClassifier.VacancyAssessment vacancyAssessment =
                sponsorshipClassifier.assessVacancy(job, aiAnalysis, null);

        boolean hasActiveRegistryRecord = registryRecords.stream()
                .anyMatch(rec -> rec.getStatus() == SponsorshipRecordStatus.RECOGNIZED_SPONSOR
                        || rec.getStatus() == SponsorshipRecordStatus.VISA_CAPABLE)
                || (syncedRegistryMatch != null && syncedRegistryMatch.isActive());

        boolean verifiedSponsor = status != JobSponsorStatus.NO_SPONSORSHIP
                && !vacancyAssessment.explicitRejection()
                && (status == JobSponsorStatus.VERIFIED_SPONSOR
                || (company != null && company.isVerifiedSponsor())
                || hasActiveRegistryRecord);

        SponsorConfidenceLevel confidenceLevel = vacancyAssessment.explicitRejection()
                ? SponsorConfidenceLevel.LOW
                : resolveConfidenceLevel(status, company, hasActiveRegistryRecord);

        List<String> signals = new ArrayList<>();
        for (CompanySponsorshipRecord record : registryRecords) {
            if (record.getStatus() == SponsorshipRecordStatus.RECOGNIZED_SPONSOR
                    || record.getStatus() == SponsorshipRecordStatus.VISA_CAPABLE) {
                signals.add("Official registry verification (" + record.getCountry()
                        + " · " + record.getRegistrySource() + ")");
            } else {
                signals.add("Registry notice: " + record.getStatus().name()
                        + " in " + record.getCountry() + " (" + record.getRegistrySource() + ")");
            }
        }
        if (syncedRegistryMatch != null && syncedRegistryMatch.isActive()
                && registryRecords.stream().noneMatch(rec -> syncedRegistryMatch.getRegistrySource().equalsIgnoreCase(rec.getRegistrySource()))) {
            signals.add("Official registry verification (" + syncedRegistryMatch.getCountry()
                    + " · " + syncedRegistryMatch.getRegistrySource() + ")");
        }

        String postingText = ((job.getTitle() != null ? job.getTitle() : "") + "\n"
                + (job.getDescription() != null ? job.getDescription() : "")).toLowerCase(Locale.ROOT);

        if (status == JobSponsorStatus.NO_SPONSORSHIP
                || containsAny(postingText,
                        "no visa sponsorship", "not sponsor visas", "unable to sponsor",
                        "without visa sponsorship", "sponsorship is not available",
                        "must already have work authorization", "valid eu work permit required")) {
            signals.add("Posting explicitly indicates visa sponsorship is unavailable or existing authorization is required");
        }
        if (containsAny(postingText,
                "visa sponsorship", "sponsor visa", "eu blue card",
                "highly skilled migrant", "ind sponsor", "kennismigrant",
                "work permit support", "work permit sponsorship")) {
            signals.add("Job description explicitly mentions visa sponsorship or EU work permit pathways");
        }
        if (containsAny(postingText,
                "relocation support", "relocation package", "relocation assistance",
                "relocation budget", "relocation offered")) {
            signals.add("Job description mentions relocation assistance or package");
        }

        if (signals.isEmpty() && company != null && company.isVerifiedSponsor()) {
            signals.add("Matched employer is flagged as a verified visa sponsor in company intelligence");
        }

        boolean syncedForCountry = canonicalCountry != null && !canonicalCountry.isBlank()
                && officialSponsorRegistryEntryRepository.countByCountryIgnoreCaseAndActiveTrue(canonicalCountry) > 0;

        VisaSponsorshipStatus detailedStatus;
        String vacancyCaveat;
        if (countryProfileOpt.isPresent()) {
            SponsorshipClassifier.ClassificationResult verdict = sponsorshipClassifier.classify(
                    job.getCompanyName() != null ? job.getCompanyName() : "Employer",
                    countryProfileOpt.get(),
                    company,
                    countryScopedRecords,
                    syncedRegistryMatch,
                    vacancyAssessment,
                    syncedForCountry,
                    job
            );
            detailedStatus = verdict.status();
            vacancyCaveat = verdict.vacancyCaveat();
        } else {
            detailedStatus = mapFallbackStatus(status, vacancyAssessment);
            vacancyCaveat = verifiedSponsor
                    ? CountrySponsorshipCatalog.DEFAULT_VACANCY_CAVEAT
                    : null;
        }

        String summary = buildSponsorshipSummary(status, company, registryRecords.size(), verifiedSponsor, vacancyAssessment);

        List<SponsorshipRegistryRecordDto> recordDtos = new ArrayList<>(registryRecords.stream()
                .map(record -> SponsorshipRegistryRecordDto.builder()
                        .id(record.getId())
                        .country(record.getCountry())
                        .registrySource(record.getRegistrySource())
                        .registryIdentifier(record.getRegistryIdentifier())
                        .officialRegisteredName(record.getOfficialRegisteredName())
                        .status(record.getStatus())
                        .evidenceNotes(record.getEvidenceNotes())
                        .sourceUrl(record.getSourceUrl())
                        .verifiedAt(record.getVerifiedAt())
                        .build())
                .toList());

        if (syncedRegistryMatch != null && syncedRegistryMatch.isActive()
                && recordDtos.stream().noneMatch(dto -> syncedRegistryMatch.getRegistrySource().equalsIgnoreCase(dto.getRegistrySource()))) {
            recordDtos.add(SponsorshipRegistryRecordDto.builder()
                    .id(syncedRegistryMatch.getId())
                    .country(syncedRegistryMatch.getCountry())
                    .registrySource(syncedRegistryMatch.getRegistrySource())
                    .registryIdentifier(syncedRegistryMatch.getRegistryIdentifier())
                    .officialRegisteredName(syncedRegistryMatch.getOfficialName())
                    .status(syncedRegistryMatch.getStatus())
                    .evidenceNotes(syncedRegistryMatch.getEvidenceNotes())
                    .sourceUrl(syncedRegistryMatch.getSourceUrl())
                    .verifiedAt(syncedRegistryMatch.getSyncedAt())
                    .build());
        }

        Instant latestVerifiedAt = registryRecords.stream()
                .map(CompanySponsorshipRecord::getVerifiedAt)
                .filter(java.util.Objects::nonNull)
                .findFirst()
                .orElse(syncedRegistryMatch != null ? syncedRegistryMatch.getSyncedAt() : null);

        return SponsorshipBreakdownDto.builder()
                .sponsorStatus(status)
                .detailedStatus(detailedStatus)
                .detailedStatusLabel(detailedStatus.getLabel())
                .vacancyStatus(vacancyAssessment.vacancyStatus())
                .vacancyStatusLabel(vacancyAssessment.vacancyStatus().getLabel())
                .vacancySponsorshipConfirmed(vacancyAssessment.explicitSponsorshipMention())
                .vacancySponsorshipAssumed(false)
                .verifiedSponsor(verifiedSponsor)
                .confidenceLevel(confidenceLevel)
                .summary(summary)
                .vacancyCaveat(vacancyCaveat)
                .countryGuideSummary(countryProfileOpt.map(CountrySponsorshipProfile::guidanceSummary).orElse(null))
                .officialRegisterUrl(countryProfileOpt.map(CountrySponsorshipProfile::publicRegisterUrl).orElse(null))
                .verifiedAt(latestVerifiedAt)
                .evidenceSignals(List.copyOf(signals))
                .registryRecords(List.copyOf(recordDtos))
                .build();
    }

    private OfficialSponsorRegistryEntry resolveSyncedRegistryEntry(Job job, String canonicalCountry) {
        if (canonicalCountry == null || canonicalCountry.isBlank()
                || job.getCompanyName() == null || job.getCompanyName().isBlank()) {
            return null;
        }
        String canonicalKey = canonicalHashGenerator.normalizeCompanyKey(job.getCompanyName());
        if (!canonicalKey.isBlank()) {
            Optional<OfficialSponsorRegistryEntry> match = officialSponsorRegistryEntryRepository
                    .findFirstByCountryIgnoreCaseAndNormalizedNameIgnoreCaseAndActiveTrueOrderBySyncedAtDesc(
                            canonicalCountry, canonicalKey);
            if (match.isPresent()) {
                return match.get();
            }
        }
        String lowerTrim = job.getCompanyName().trim().toLowerCase(Locale.ROOT);
        if (!lowerTrim.equals(canonicalKey)) {
            return officialSponsorRegistryEntryRepository
                    .findFirstByCountryIgnoreCaseAndNormalizedNameIgnoreCaseAndActiveTrueOrderBySyncedAtDesc(
                            canonicalCountry, lowerTrim)
                    .orElse(null);
        }
        return null;
    }

    private VisaSponsorshipStatus mapFallbackStatus(
            JobSponsorStatus status,
            SponsorshipClassifier.VacancyAssessment vacancyAssessment
    ) {
        if (status == JobSponsorStatus.NO_SPONSORSHIP || vacancyAssessment.explicitRejection()) {
            return VisaSponsorshipStatus.UNAVAILABLE;
        }
        if (status == JobSponsorStatus.VERIFIED_SPONSOR) {
            return VisaSponsorshipStatus.VERIFIED_SPONSOR;
        }
        if (status == JobSponsorStatus.SPONSORSHIP_MENTIONED) {
            return VisaSponsorshipStatus.SPONSORSHIP_MENTIONED;
        }
        if (status == JobSponsorStatus.RELOCATION_OFFERED) {
            return VisaSponsorshipStatus.RELOCATION_ASSISTANCE_MENTIONED;
        }
        return vacancyAssessment.plausibilityClue()
                ? VisaSponsorshipStatus.POSSIBLE_UNCONFIRMED
                : VisaSponsorshipStatus.NOT_MENTIONED;
    }

    private SponsorConfidenceLevel resolveConfidenceLevel(
            JobSponsorStatus status,
            Company company,
            boolean hasActiveRegistryRecord
    ) {
        if (status == JobSponsorStatus.NO_SPONSORSHIP) {
            return SponsorConfidenceLevel.LOW;
        }
        if (company != null && company.getSponsorConfidenceLevel() != null
                && company.getSponsorConfidenceLevel() != SponsorConfidenceLevel.UNVERIFIED) {
            return company.getSponsorConfidenceLevel();
        }
        if (hasActiveRegistryRecord || status == JobSponsorStatus.VERIFIED_SPONSOR) {
            return SponsorConfidenceLevel.VERIFIED;
        }
        return switch (status) {
            case SPONSORSHIP_MENTIONED, RELOCATION_OFFERED -> SponsorConfidenceLevel.MEDIUM;
            case NO_SPONSORSHIP -> SponsorConfidenceLevel.LOW;
            default -> SponsorConfidenceLevel.UNVERIFIED;
        };
    }

    private String buildSponsorshipSummary(
            JobSponsorStatus status,
            Company company,
            int registryCount,
            boolean verifiedSponsor,
            SponsorshipClassifier.VacancyAssessment vacancyAssessment
    ) {
        if (status == JobSponsorStatus.NO_SPONSORSHIP || vacancyAssessment.explicitRejection()) {
            return "This posting states that visa sponsorship is not offered or existing local work authorization is required.";
        }
        if (verifiedSponsor && registryCount > 0) {
            return vacancyAssessment.explicitSponsorshipMention()
                    ? "Employer is matched against " + registryCount + " official government sponsor registry record(s) and vacancy confirms sponsorship."
                    : "Employer is matched against " + registryCount + " official government sponsor registry record(s); verify vacancy-level sponsorship eligibility.";
        }
        if (verifiedSponsor) {
            return vacancyAssessment.explicitSponsorshipMention()
                    ? "Verified visa sponsor employer" + (company != null ? " (" + company.getName() + ")" : "") + " confirming vacancy sponsorship."
                    : "Verified visa sponsor employer" + (company != null ? " (" + company.getName() + ")" : "") + "; confirm sponsorship for this specific vacancy.";
        }
        return switch (status) {
            case SPONSORSHIP_MENTIONED ->
                    "Job posting text mentions visa sponsorship or EU work permit support; verify registry status before applying.";
            case RELOCATION_OFFERED ->
                    "Job posting offers relocation assistance, though explicit visa sponsorship is unconfirmed in official registers.";
            default -> vacancyAssessment.plausibilityClue()
                    ? "Possible but unconfirmed: role shows international hiring clues without official sponsor confirmation."
                    : "No explicit visa sponsorship mention or official sponsor registry match has been confirmed for this posting yet.";
        };
    }

    private JobMatchBreakdownDto toMatchBreakdownDto(JobMatchScore matchScore) {
        return JobMatchBreakdownMapper.toDto(matchScore);
    }

    private JobAiAnalysisDto toAiAnalysisDto(JobAiAnalysis analysis) {
        if (analysis == null) {
            return null;
        }
        return JobAiAnalysisDto.builder()
                .id(analysis.getId())
                .jobId(analysis.getJob() != null ? analysis.getJob().getId() : null)
                .analysisVersion(analysis.getAnalysisVersion())
                .extractedTitle(analysis.getExtractedTitle())
                .extractedCompany(analysis.getExtractedCompany())
                .extractedCountry(analysis.getExtractedCountry())
                .extractedLocation(analysis.getExtractedLocation())
                .requiredExperienceYears(analysis.getRequiredExperienceYears())
                .requiredExperienceLevel(analysis.getRequiredExperienceLevel())
                .requiredExperienceSummary(analysis.getRequiredExperienceSummary())
                .requiredSkills(safeCopy(analysis.getRequiredSkills()))
                .niceToHaveSkills(safeCopy(analysis.getNiceToHaveSkills()))
                .responsibilities(safeCopy(analysis.getResponsibilities()))
                .benefits(safeCopy(analysis.getBenefits()))
                .languageRequirements(safeCopy(analysis.getLanguageRequirements()))
                .salaryMin(analysis.getSalaryMin())
                .salaryMax(analysis.getSalaryMax())
                .salaryCurrency(analysis.getSalaryCurrency())
                .salaryPeriod(analysis.getSalaryPeriod())
                .workArrangement(analysis.getWorkArrangement())
                .employmentType(analysis.getEmploymentType())
                .sponsorshipOffered(analysis.getSponsorshipOffered())
                .visaSponsorshipAnalysis(analysis.getVisaSponsorshipAnalysis())
                .relocationAssistanceOffered(analysis.getRelocationAssistanceOffered())
                .relocationAnalysis(analysis.getRelocationAnalysis())
                .summary(analysis.getSummary())
                .aiProvider(analysis.getAiProvider())
                .aiModelUsed(analysis.getAiModelUsed())
                .analyzedAt(analysis.getUpdatedAt() != null ? analysis.getUpdatedAt() : analysis.getCreatedAt())
                .build();
    }

    private JobApplicationSummaryDto toApplicationSummaryDto(JobApplication application) {
        if (application == null) {
            return null;
        }
        return JobApplicationSummaryDto.builder()
                .uuid(application.getUuid())
                .status(application.getStatus())
                .appliedDate(application.getAppliedDate())
                .nextFollowUpDate(application.getNextFollowUpDate())
                .nextInterviewDate(application.getNextInterviewDate())
                .build();
    }

    private JobSummaryDto toSummaryDto(Job job, UserJobInteraction interaction, JobMatchScore matchScore) {
        return JobSummaryDto.builder()
                .id(job.getId())
                .title(job.getTitle())
                .companyName(job.getCompanyName())
                .location(job.getLocation())
                .city(job.getCity())
                .country(job.getCountry())
                .workArrangement(job.getWorkArrangement())
                .employmentType(job.getEmploymentType())
                .experienceLevel(job.getExperienceLevel())
                .salaryMin(job.getSalaryMin())
                .salaryMax(job.getSalaryMax())
                .salaryCurrency(job.getSalaryCurrency())
                .salaryPeriod(job.getSalaryPeriod())
                .sponsorStatus(job.getSponsorStatus())
                .matchPercentage(toMatchPercentage(matchScore))
                .sourceProvider(job.getSourceProvider())
                .postedAt(job.getPostedAt())
                .saved(interaction != null && interaction.isSaved())
                .interested(interaction != null && interaction.isInterested())
                .hidden(interaction != null && interaction.isHidden())
                .skills(safeCopy(job.getSkills()))
                .descriptionSnippet(resolveSnippet(job))
                .originalUrl(job.getOriginalUrl())
                .build();
    }

    private Integer toMatchPercentage(JobMatchScore matchScore) {
        if (matchScore == null || matchScore.getFinalScore() == null) {
            return null;
        }
        int rounded = matchScore.getFinalScore().setScale(0, RoundingMode.HALF_UP).intValue();
        return Math.max(0, Math.min(100, rounded));
    }

    private String resolveSnippet(Job job) {
        if (job.getDescriptionSnippet() != null && !job.getDescriptionSnippet().isBlank()) {
            return job.getDescriptionSnippet();
        }
        String description = job.getDescription();
        if (description == null || description.isBlank()) {
            return null;
        }
        String collapsed = description.trim().replaceAll("\\s+", " ");
        if (collapsed.length() <= SNIPPET_MAX_LENGTH) {
            return collapsed;
        }
        return collapsed.substring(0, SNIPPET_MAX_LENGTH - 3).trim() + "...";
    }

    private boolean containsAny(String haystack, String... needles) {
        for (String needle : needles) {
            if (haystack.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    private List<String> safeCopy(List<String> values) {
        return values == null ? List.of() : List.copyOf(values);
    }

    private <T> List<T> safeList(List<T> values) {
        return values == null ? List.of() : values;
    }
}
