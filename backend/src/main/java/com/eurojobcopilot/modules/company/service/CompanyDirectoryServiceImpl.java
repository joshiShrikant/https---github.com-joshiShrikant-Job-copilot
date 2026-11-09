package com.eurojobcopilot.modules.company.service;

import com.eurojobcopilot.core.enums.WorkArrangement;
import com.eurojobcopilot.core.exception.BusinessValidationException;
import com.eurojobcopilot.core.exception.ResourceNotFoundException;
import com.eurojobcopilot.core.exception.UnauthorizedAccessException;
import com.eurojobcopilot.modules.auth.entity.User;
import com.eurojobcopilot.modules.auth.repository.UserRepository;
import com.eurojobcopilot.modules.auth.security.AuthenticatedUserPrincipal;
import com.eurojobcopilot.modules.company.dto.CompanyDetailDto;
import com.eurojobcopilot.modules.company.dto.CompanySaveResponse;
import com.eurojobcopilot.modules.company.dto.CompanySearchCriteria;
import com.eurojobcopilot.modules.company.dto.CompanySortOption;
import com.eurojobcopilot.modules.company.dto.CompanySummaryDto;
import com.eurojobcopilot.modules.company.dto.PagedCompaniesResponse;
import com.eurojobcopilot.modules.company.dto.SaveCompanyRequest;
import com.eurojobcopilot.modules.company.entity.CompanyPriorityTier;
import com.eurojobcopilot.modules.company.entity.UserSavedCompany;
import com.eurojobcopilot.modules.company.repository.CompanyDirectoryRepository;
import com.eurojobcopilot.modules.company.repository.UserSavedCompanyRepository;
import com.eurojobcopilot.modules.job.dto.PagedJobsResponse;
import com.eurojobcopilot.modules.job.dto.SponsorshipRegistryRecordDto;
import com.eurojobcopilot.modules.job.entity.Job;
import com.eurojobcopilot.modules.job.entity.JobSponsorStatus;
import com.eurojobcopilot.modules.job.normalization.CanonicalHashGenerator;
import com.eurojobcopilot.modules.job.normalization.JobNormalizer;
import com.eurojobcopilot.modules.job.repository.JobRepository;
import com.eurojobcopilot.modules.job.service.JobQueryService;
import com.eurojobcopilot.modules.sponsorship.catalog.CountrySponsorshipCatalog;
import com.eurojobcopilot.modules.sponsorship.catalog.CountrySponsorshipProfile;
import com.eurojobcopilot.modules.sponsorship.entity.Company;
import com.eurojobcopilot.modules.sponsorship.entity.CompanySponsorshipRecord;
import com.eurojobcopilot.modules.sponsorship.entity.OfficialSponsorRegistryEntry;
import com.eurojobcopilot.modules.sponsorship.entity.SponsorConfidenceLevel;
import com.eurojobcopilot.modules.sponsorship.entity.SponsorshipRecordStatus;
import com.eurojobcopilot.modules.sponsorship.repository.CompanyRepository;
import com.eurojobcopilot.modules.sponsorship.repository.CompanySponsorshipRecordRepository;
import com.eurojobcopilot.modules.sponsorship.repository.OfficialSponsorRegistryEntryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CompanyDirectoryServiceImpl implements CompanyDirectoryService {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;
    private static final int SUMMARY_MAX_TECH_STACK = 12;
    private static final int DETAIL_MAX_TECH_STACK = 24;
    private static final int MAX_NOTES_LENGTH = 2000;

    private final CompanyRepository companyRepository;
    private final CompanyDirectoryRepository companyDirectoryRepository;
    private final UserSavedCompanyRepository userSavedCompanyRepository;
    private final CompanySponsorshipRecordRepository companySponsorshipRecordRepository;
    private final OfficialSponsorRegistryEntryRepository registryEntryRepository;
    private final JobRepository jobRepository;
    private final JobQueryService jobQueryService;
    private final UserRepository userRepository;
    private final CountrySponsorshipCatalog countrySponsorshipCatalog;
    private final JobNormalizer jobNormalizer;
    private final CanonicalHashGenerator canonicalHashGenerator;

    @Override
    @Transactional(readOnly = true)
    public PagedCompaniesResponse searchCompanies(
            AuthenticatedUserPrincipal currentUser,
            CompanySearchCriteria criteria
    ) {
        Long userId = requireUserId(currentUser);
        CompanySearchCriteria effective = criteria != null
                ? criteria
                : CompanySearchCriteria.builder()
                        .page(0)
                        .size(DEFAULT_PAGE_SIZE)
                        .sort(CompanySortOption.ACTIVE_JOBS_DESC.getParamValue())
                        .build();

        CompanySortOption sortOption = CompanySortOption.fromParam(effective.sort());
        int safePage = Math.max(0, effective.page());
        int requestedSize = effective.size() > 0 ? effective.size() : DEFAULT_PAGE_SIZE;
        int safeSize = Math.min(MAX_PAGE_SIZE, requestedSize);

        String normalizedCountry = normalizeCountryFilter(effective.country());
        Instant now = Instant.now();

        Page<Company> companyPage = companyDirectoryRepository.searchCompanies(
                userId,
                effective,
                normalizedCountry,
                PageRequest.of(safePage, safeSize),
                sortOption,
                now
        );

        List<Company> companies = companyPage.getContent();
        long verifiedSponsorsTotal = companyDirectoryRepository.countVerifiedSponsorCompanies();
        long savedCompaniesTotal = userSavedCompanyRepository.countByUserId(userId);

        if (companies.isEmpty()) {
            return PagedCompaniesResponse.builder()
                    .content(List.of())
                    .pageNumber(companyPage.getNumber())
                    .pageSize(companyPage.getSize())
                    .totalElements(companyPage.getTotalElements())
                    .totalPages(companyPage.getTotalPages())
                    .first(companyPage.isFirst())
                    .last(companyPage.isLast())
                    .hasNext(companyPage.hasNext())
                    .hasPrevious(companyPage.hasPrevious())
                    .verifiedSponsorsTotal(verifiedSponsorsTotal)
                    .savedCompaniesTotal(savedCompaniesTotal)
                    .build();
        }

        List<Long> companyIds = companies.stream().map(Company::getId).toList();
        Set<String> lowerCompanyNames = companies.stream()
                .map(Company::getName)
                .filter(this::hasText)
                .map(name -> name.trim().toLowerCase(Locale.ROOT))
                .collect(Collectors.toCollection(LinkedHashSet::new));

        Map<Long, List<Job>> jobsByCompanyId = batchLoadCompanyJobs(companies, companyIds, lowerCompanyNames, now);

        Map<Long, List<CompanySponsorshipRecord>> recordsByCompanyId = companySponsorshipRecordRepository
                .findAllByCompanyIdInOrderByVerifiedAtDesc(companyIds)
                .stream()
                .collect(Collectors.groupingBy(rec -> rec.getCompany().getId()));

        Set<String> lookupKeys = new LinkedHashSet<>();
        for (Company company : companies) {
            for (String key : resolveLookupKeys(company)) {
                lookupKeys.add(key);
            }
        }
        Map<String, List<OfficialSponsorRegistryEntry>> registryByKey = lookupKeys.isEmpty()
                ? Map.of()
                : registryEntryRepository.findAllByNormalizedNameInAndActiveTrueOrderBySyncedAtDesc(lookupKeys)
                        .stream()
                        .collect(Collectors.groupingBy(
                                entry -> entry.getNormalizedName().toLowerCase(Locale.ROOT)
                        ));

        Map<Long, UserSavedCompany> savedByCompanyId = userSavedCompanyRepository
                .findAllByUserIdAndCompanyIdIn(userId, companyIds)
                .stream()
                .collect(Collectors.toMap(
                        saved -> saved.getCompany().getId(),
                        Function.identity(),
                        (first, second) -> first
                ));

        List<CompanySummaryDto> summaries = companies.stream()
                .map(company -> toSummaryDto(
                        company,
                        jobsByCompanyId.getOrDefault(company.getId(), List.of()),
                        recordsByCompanyId.getOrDefault(company.getId(), List.of()),
                        resolveMatchedRegistryEntries(company, registryByKey),
                        savedByCompanyId.get(company.getId())
                ))
                .toList();

        return PagedCompaniesResponse.builder()
                .content(summaries)
                .pageNumber(companyPage.getNumber())
                .pageSize(companyPage.getSize())
                .totalElements(companyPage.getTotalElements())
                .totalPages(companyPage.getTotalPages())
                .first(companyPage.isFirst())
                .last(companyPage.isLast())
                .hasNext(companyPage.hasNext())
                .hasPrevious(companyPage.hasPrevious())
                .verifiedSponsorsTotal(verifiedSponsorsTotal)
                .savedCompaniesTotal(savedCompaniesTotal)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public CompanyDetailDto getCompanyDetail(AuthenticatedUserPrincipal currentUser, Long companyId) {
        Long userId = requireUserId(currentUser);
        Company company = requireCompany(companyId);
        Instant now = Instant.now();

        List<Job> activeJobs = loadSingleCompanyJobs(company, now);
        List<CompanySponsorshipRecord> persistedRecords = safeList(
                companySponsorshipRecordRepository.findAllByCompanyIdOrderByVerifiedAtDesc(company.getId())
        );

        List<OfficialSponsorRegistryEntry> syncedEntries = loadSingleCompanyRegistryEntries(company);
        List<SponsorshipRegistryRecordDto> registryDtos = mergeRegistryRecords(persistedRecords, syncedEntries);

        boolean effectiveVerified = resolveEffectiveVerifiedSponsor(company, persistedRecords, syncedEntries);
        SponsorConfidenceLevel confidenceLevel = resolveConfidenceLevel(company, effectiveVerified);

        Optional<CountrySponsorshipProfile> hqProfileOpt =
                countrySponsorshipCatalog.findCountryProfile(company.getHeadquartersCountry());
        String vacancyCaveat = resolveVacancyCaveat(hqProfileOpt, effectiveVerified, registryDtos);
        String countryGuideSummary = hqProfileOpt.map(CountrySponsorshipProfile::guidanceSummary).orElse(null);
        String officialRegisterUrl = sanitizeHttpUrl(
                hqProfileOpt.map(CountrySponsorshipProfile::publicRegisterUrl).orElse(null)
        );

        JobMetrics metrics = computeJobMetrics(activeJobs);
        List<String> consolidatedTech = buildConsolidatedTechStack(company, activeJobs, DETAIL_MAX_TECH_STACK);
        List<String> activeCountries = buildDistinctLocations(company.getHeadquartersCountry(), activeJobs, true);
        List<String> activeCities = buildDistinctLocations(company.getHeadquartersCity(), activeJobs, false);

        UserSavedCompany saved = userSavedCompanyRepository.findByUserIdAndCompanyId(userId, company.getId()).orElse(null);

        log.debug("Loaded company detail companyId={} userId={} activeJobs={} verified={} saved={}",
                company.getId(), userId, metrics.activeJobsCount(), effectiveVerified, saved != null);

        return CompanyDetailDto.builder()
                .id(company.getId())
                .name(company.getName())
                .normalizedName(company.getNormalizedName())
                .description(company.getDescription())
                .websiteUrl(sanitizeHttpUrl(company.getWebsiteUrl()))
                .careersUrl(sanitizeHttpUrl(company.getCareersUrl()))
                .logoUrl(sanitizeHttpUrl(company.getLogoUrl()))
                .headquartersCountry(company.getHeadquartersCountry())
                .headquartersCity(company.getHeadquartersCity())
                .industry(company.getIndustry())
                .companySize(company.getCompanySize())
                .verifiedSponsor(effectiveVerified)
                .sponsorConfidenceLevel(confidenceLevel)
                .activeJobsCount(metrics.activeJobsCount())
                .sponsorshipJobsCount(metrics.sponsorshipJobsCount())
                .remoteJobsCount(metrics.remoteJobsCount())
                .techStack(consolidatedTech)
                .activeCountries(activeCountries)
                .activeCities(activeCities)
                .vacancyCaveat(vacancyCaveat)
                .countryGuideSummary(countryGuideSummary)
                .officialRegisterUrl(officialRegisterUrl)
                .registryRecords(registryDtos)
                .saved(saved != null)
                .savedAt(saved != null ? saved.getSavedAt() : null)
                .priorityTier(saved != null ? saved.getPriorityTier() : null)
                .userNotes(saved != null ? saved.getNotes() : null)
                .createdAt(company.getCreatedAt())
                .updatedAt(company.getUpdatedAt())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public PagedJobsResponse getCompanyJobs(
            AuthenticatedUserPrincipal currentUser,
            Long companyId,
            boolean includeHidden,
            int page,
            int size,
            String sort
    ) {
        requireUserId(currentUser);
        Company company = requireCompany(companyId);
        return jobQueryService.getCompanyActiveJobs(
                currentUser,
                company.getId(),
                company.getName(),
                includeHidden,
                page,
                size,
                sort
        );
    }

    @Override
    @Transactional
    public CompanySaveResponse saveCompany(
            AuthenticatedUserPrincipal currentUser,
            Long companyId,
            SaveCompanyRequest request
    ) {
        User user = requireAuthenticatedUser(currentUser);
        Company company = requireCompany(companyId);

        Optional<UserSavedCompany> existingOpt =
                userSavedCompanyRepository.findByUserIdAndCompanyId(user.getId(), company.getId());

        String normalizedNotes = null;
        boolean hasNotesUpdate = false;
        if (request != null && request.getNotes() != null) {
            String trimmed = request.getNotes().trim();
            if (trimmed.length() > MAX_NOTES_LENGTH) {
                throw new BusinessValidationException("Notes must not exceed " + MAX_NOTES_LENGTH + " characters");
            }
            normalizedNotes = trimmed.isEmpty() ? null : trimmed;
            hasNotesUpdate = true;
        }

        UserSavedCompany savedEntity;
        if (existingOpt.isPresent()) {
            savedEntity = existingOpt.get();
            if (request != null && request.getPriorityTier() != null) {
                savedEntity.setPriorityTier(request.getPriorityTier());
            }
            if (hasNotesUpdate) {
                savedEntity.setNotes(normalizedNotes);
            }
            savedEntity = userSavedCompanyRepository.save(savedEntity);
        } else {
            CompanyPriorityTier tier = (request != null && request.getPriorityTier() != null)
                    ? request.getPriorityTier()
                    : CompanyPriorityTier.TARGET;
            savedEntity = userSavedCompanyRepository.save(UserSavedCompany.builder()
                    .user(user)
                    .company(company)
                    .priorityTier(tier)
                    .notes(normalizedNotes)
                    .savedAt(Instant.now().truncatedTo(ChronoUnit.SECONDS))
                    .build());
        }

        log.info("Saved target company userUuid={} companyId={} tier={}",
                user.getUuid(), company.getId(), savedEntity.getPriorityTier());

        return CompanySaveResponse.builder()
                .companyId(company.getId())
                .companyName(company.getName())
                .saved(true)
                .priorityTier(savedEntity.getPriorityTier())
                .notes(savedEntity.getNotes())
                .savedAt(savedEntity.getSavedAt())
                .build();
    }

    @Override
    @Transactional
    public CompanySaveResponse unsaveCompany(AuthenticatedUserPrincipal currentUser, Long companyId) {
        User user = requireAuthenticatedUser(currentUser);
        Company company = requireCompany(companyId);

        userSavedCompanyRepository.findByUserIdAndCompanyId(user.getId(), company.getId())
                .ifPresent(userSavedCompanyRepository::delete);

        log.info("Removed saved target company userUuid={} companyId={}", user.getUuid(), company.getId());

        return CompanySaveResponse.builder()
                .companyId(company.getId())
                .companyName(company.getName())
                .saved(false)
                .priorityTier(null)
                .notes(null)
                .savedAt(null)
                .build();
    }

    private CompanySummaryDto toSummaryDto(
            Company company,
            List<Job> activeJobs,
            List<CompanySponsorshipRecord> persistedRecords,
            List<OfficialSponsorRegistryEntry> syncedEntries,
            UserSavedCompany saved
    ) {
        boolean effectiveVerified = resolveEffectiveVerifiedSponsor(company, persistedRecords, syncedEntries);
        SponsorConfidenceLevel confidenceLevel = resolveConfidenceLevel(company, effectiveVerified);
        JobMetrics metrics = computeJobMetrics(activeJobs);
        List<String> techStack = buildConsolidatedTechStack(company, activeJobs, SUMMARY_MAX_TECH_STACK);
        List<String> activeCountries = buildDistinctLocations(company.getHeadquartersCountry(), activeJobs, true);
        List<String> registryCountries = buildRegistryCountries(persistedRecords, syncedEntries);

        return CompanySummaryDto.builder()
                .id(company.getId())
                .name(company.getName())
                .headquartersCountry(company.getHeadquartersCountry())
                .headquartersCity(company.getHeadquartersCity())
                .industry(company.getIndustry())
                .companySize(company.getCompanySize())
                .websiteUrl(sanitizeHttpUrl(company.getWebsiteUrl()))
                .careersUrl(sanitizeHttpUrl(company.getCareersUrl()))
                .logoUrl(sanitizeHttpUrl(company.getLogoUrl()))
                .verifiedSponsor(effectiveVerified)
                .sponsorConfidenceLevel(confidenceLevel)
                .activeJobsCount(metrics.activeJobsCount())
                .sponsorshipJobsCount(metrics.sponsorshipJobsCount())
                .remoteJobsCount(metrics.remoteJobsCount())
                .techStack(techStack)
                .activeCountries(activeCountries)
                .registryCountries(registryCountries)
                .saved(saved != null)
                .savedAt(saved != null ? saved.getSavedAt() : null)
                .priorityTier(saved != null ? saved.getPriorityTier() : null)
                .build();
    }

    private Map<Long, List<Job>> batchLoadCompanyJobs(
            List<Company> companies,
            List<Long> companyIds,
            Set<String> lowerCompanyNames,
            Instant now
    ) {
        Map<Long, List<Job>> result = new HashMap<>();
        Map<Long, Set<Long>> seenJobIdsByCompany = new HashMap<>();
        Map<String, List<Long>> companyIdsByNameLower = new HashMap<>();

        for (Company company : companies) {
            result.put(company.getId(), new ArrayList<>());
            seenJobIdsByCompany.put(company.getId(), new LinkedHashSet<>());
            if (hasText(company.getName())) {
                companyIdsByNameLower
                        .computeIfAbsent(company.getName().trim().toLowerCase(Locale.ROOT), ignored -> new ArrayList<>())
                        .add(company.getId());
            }
        }

        if (!companyIds.isEmpty()) {
            for (Job job : jobRepository.findActiveNonExpiredByCompanyIdIn(companyIds, now)) {
                if (job.getCompany() != null && job.getCompany().getId() != null) {
                    Long compId = job.getCompany().getId();
                    if (seenJobIdsByCompany.containsKey(compId) && seenJobIdsByCompany.get(compId).add(job.getId())) {
                        result.get(compId).add(job);
                    }
                }
            }
        }

        if (!lowerCompanyNames.isEmpty()) {
            for (Job job : jobRepository.findActiveNonExpiredUnlinkedByCompanyNameIn(lowerCompanyNames, now)) {
                if (hasText(job.getCompanyName())) {
                    List<Long> matchingIds = companyIdsByNameLower.get(job.getCompanyName().trim().toLowerCase(Locale.ROOT));
                    if (matchingIds != null) {
                        for (Long compId : matchingIds) {
                            if (seenJobIdsByCompany.get(compId).add(job.getId())) {
                                result.get(compId).add(job);
                            }
                        }
                    }
                }
            }
        }

        return result;
    }

    private List<Job> loadSingleCompanyJobs(Company company, Instant now) {
        Map<Long, Job> deduped = new LinkedHashMap<>();
        for (Job linked : jobRepository.findActiveNonExpiredByCompanyIdIn(List.of(company.getId()), now)) {
            deduped.putIfAbsent(linked.getId(), linked);
        }
        if (hasText(company.getName())) {
            for (Job unlinked : jobRepository.findActiveNonExpiredUnlinkedByCompanyNameIn(
                    List.of(company.getName().trim().toLowerCase(Locale.ROOT)), now)) {
                deduped.putIfAbsent(unlinked.getId(), unlinked);
            }
        }
        return new ArrayList<>(deduped.values());
    }

    private List<OfficialSponsorRegistryEntry> loadSingleCompanyRegistryEntries(Company company) {
        Set<String> keys = resolveLookupKeys(company);
        if (keys.isEmpty()) {
            return List.of();
        }
        return registryEntryRepository.findAllByNormalizedNameInAndActiveTrueOrderBySyncedAtDesc(keys);
    }

    private List<OfficialSponsorRegistryEntry> resolveMatchedRegistryEntries(
            Company company,
            Map<String, List<OfficialSponsorRegistryEntry>> registryByKey
    ) {
        if (registryByKey.isEmpty()) {
            return List.of();
        }
        Map<Long, OfficialSponsorRegistryEntry> matched = new LinkedHashMap<>();
        for (String key : resolveLookupKeys(company)) {
            for (OfficialSponsorRegistryEntry entry : registryByKey.getOrDefault(key.toLowerCase(Locale.ROOT), List.of())) {
                matched.putIfAbsent(entry.getId(), entry);
            }
        }
        return new ArrayList<>(matched.values());
    }

    private Set<String> resolveLookupKeys(Company company) {
        Set<String> keys = new LinkedHashSet<>();
        if (hasText(company.getNormalizedName())) {
            keys.add(company.getNormalizedName().trim().toLowerCase(Locale.ROOT));
        }
        if (hasText(company.getName())) {
            String canonical = canonicalHashGenerator.normalizeCompanyKey(company.getName());
            if (!canonical.isBlank()) {
                keys.add(canonical);
            }
            keys.add(company.getName().trim().toLowerCase(Locale.ROOT));
        }
        return keys;
    }

    private List<SponsorshipRegistryRecordDto> mergeRegistryRecords(
            List<CompanySponsorshipRecord> persistedRecords,
            List<OfficialSponsorRegistryEntry> syncedEntries
    ) {
        List<SponsorshipRegistryRecordDto> result = new ArrayList<>();
        Set<String> seenCountryAndSource = new LinkedHashSet<>();

        for (CompanySponsorshipRecord record : persistedRecords) {
            String key = dedupeRegistryKey(record.getCountry(), record.getRegistrySource());
            seenCountryAndSource.add(key);
            result.add(SponsorshipRegistryRecordDto.builder()
                    .id(record.getId())
                    .country(record.getCountry())
                    .registrySource(record.getRegistrySource())
                    .registryIdentifier(record.getRegistryIdentifier())
                    .officialRegisteredName(record.getOfficialRegisteredName())
                    .status(record.getStatus())
                    .evidenceNotes(record.getEvidenceNotes())
                    .sourceUrl(sanitizeHttpUrl(record.getSourceUrl()))
                    .verifiedAt(record.getVerifiedAt())
                    .build());
        }

        for (OfficialSponsorRegistryEntry entry : syncedEntries) {
            if (!entry.isActive()) {
                continue;
            }
            String key = dedupeRegistryKey(entry.getCountry(), entry.getRegistrySource());
            if (seenCountryAndSource.add(key)) {
                result.add(SponsorshipRegistryRecordDto.builder()
                        .id(entry.getId())
                        .country(entry.getCountry())
                        .registrySource(entry.getRegistrySource())
                        .registryIdentifier(entry.getRegistryIdentifier())
                        .officialRegisteredName(entry.getOfficialName())
                        .status(entry.getStatus() != null ? entry.getStatus() : SponsorshipRecordStatus.RECOGNIZED_SPONSOR)
                        .evidenceNotes(entry.getEvidenceNotes())
                        .sourceUrl(sanitizeHttpUrl(entry.getSourceUrl()))
                        .verifiedAt(entry.getSyncedAt())
                        .build());
            }
        }
        return List.copyOf(result);
    }

    private String dedupeRegistryKey(String country, String registrySource) {
        return (country == null ? "" : country.trim().toLowerCase(Locale.ROOT))
                + "|"
                + (registrySource == null ? "" : registrySource.trim().toLowerCase(Locale.ROOT));
    }

    private boolean resolveEffectiveVerifiedSponsor(
            Company company,
            List<CompanySponsorshipRecord> persistedRecords,
            List<OfficialSponsorRegistryEntry> syncedEntries
    ) {
        if (company.isVerifiedSponsor()) {
            return true;
        }
        boolean hasRecognizedRecord = persistedRecords.stream()
                .anyMatch(rec -> rec.getStatus() == SponsorshipRecordStatus.RECOGNIZED_SPONSOR
                        || rec.getStatus() == SponsorshipRecordStatus.VISA_CAPABLE);
        if (hasRecognizedRecord) {
            return true;
        }
        return syncedEntries.stream().anyMatch(OfficialSponsorRegistryEntry::isActive);
    }

    private SponsorConfidenceLevel resolveConfidenceLevel(Company company, boolean effectiveVerified) {
        SponsorConfidenceLevel current = company.getSponsorConfidenceLevel();
        if (current != null && current != SponsorConfidenceLevel.UNVERIFIED) {
            return current;
        }
        return effectiveVerified ? SponsorConfidenceLevel.VERIFIED : SponsorConfidenceLevel.UNVERIFIED;
    }

    private String resolveVacancyCaveat(
            Optional<CountrySponsorshipProfile> hqProfileOpt,
            boolean effectiveVerified,
            List<SponsorshipRegistryRecordDto> registryDtos
    ) {
        if (effectiveVerified || !registryDtos.isEmpty()) {
            return hqProfileOpt
                    .map(CountrySponsorshipProfile::vacancyRuleWarning)
                    .orElse(CountrySponsorshipCatalog.DEFAULT_VACANCY_CAVEAT);
        }
        return hqProfileOpt
                .map(CountrySponsorshipProfile::vacancyRuleWarning)
                .orElse("No official sponsor verification recorded yet; confirm visa sponsorship availability per opening directly with the employer.");
    }

    private JobMetrics computeJobMetrics(List<Job> activeJobs) {
        long activeCount = activeJobs.size();
        long sponsorshipCount = 0;
        long remoteCount = 0;

        for (Job job : activeJobs) {
            if (job.getSponsorStatus() == JobSponsorStatus.VERIFIED_SPONSOR
                    || job.getSponsorStatus() == JobSponsorStatus.SPONSORSHIP_MENTIONED) {
                sponsorshipCount++;
            }
            if (job.getWorkArrangement() == WorkArrangement.REMOTE) {
                remoteCount++;
            }
        }
        return new JobMetrics(activeCount, sponsorshipCount, remoteCount);
    }

    private List<String> buildConsolidatedTechStack(Company company, List<Job> activeJobs, int maxItems) {
        Map<String, String> displayByLower = new LinkedHashMap<>();
        List<String> curatedOrder = new ArrayList<>();

        if (company.getTechStack() != null) {
            for (String skill : company.getTechStack()) {
                if (hasText(skill)) {
                    String cleaned = skill.trim();
                    String key = cleaned.toLowerCase(Locale.ROOT);
                    if (!displayByLower.containsKey(key)) {
                        displayByLower.put(key, cleaned);
                        curatedOrder.add(key);
                    }
                }
            }
        }

        Map<String, Integer> frequency = new HashMap<>();
        for (Job job : activeJobs) {
            if (job.getSkills() == null) {
                continue;
            }
            Set<String> seenInJob = new LinkedHashSet<>();
            for (String skill : job.getSkills()) {
                if (hasText(skill)) {
                    String cleaned = skill.trim();
                    String key = cleaned.toLowerCase(Locale.ROOT);
                    displayByLower.putIfAbsent(key, cleaned);
                    if (seenInJob.add(key)) {
                        frequency.merge(key, 1, Integer::sum);
                    }
                }
            }
        }

        Set<String> resultKeys = new LinkedHashSet<>(curatedOrder);
        frequency.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue(Comparator.reverseOrder())
                        .thenComparing(Map.Entry.comparingByKey()))
                .map(Map.Entry::getKey)
                .forEach(resultKeys::add);

        return resultKeys.stream()
                .limit(maxItems)
                .map(displayByLower::get)
                .toList();
    }

    private List<String> buildDistinctLocations(String primaryHqValue, List<Job> activeJobs, boolean countries) {
        Map<String, String> ordered = new LinkedHashMap<>();
        if (hasText(primaryHqValue)) {
            String trimmed = primaryHqValue.trim();
            ordered.putIfAbsent(trimmed.toLowerCase(Locale.ROOT), trimmed);
        }
        for (Job job : activeJobs) {
            String value = countries ? job.getCountry() : job.getCity();
            if (hasText(value)) {
                String trimmed = value.trim();
                ordered.putIfAbsent(trimmed.toLowerCase(Locale.ROOT), trimmed);
            }
        }
        return List.copyOf(ordered.values());
    }

    private List<String> buildRegistryCountries(
            List<CompanySponsorshipRecord> persistedRecords,
            List<OfficialSponsorRegistryEntry> syncedEntries
    ) {
        Map<String, String> ordered = new LinkedHashMap<>();
        for (CompanySponsorshipRecord record : persistedRecords) {
            if (hasText(record.getCountry())) {
                String c = record.getCountry().trim();
                ordered.putIfAbsent(c.toLowerCase(Locale.ROOT), c);
            }
        }
        for (OfficialSponsorRegistryEntry entry : syncedEntries) {
            if (entry.isActive() && hasText(entry.getCountry())) {
                String c = entry.getCountry().trim();
                ordered.putIfAbsent(c.toLowerCase(Locale.ROOT), c);
            }
        }
        return List.copyOf(ordered.values());
    }

    private String normalizeCountryFilter(String rawCountry) {
        if (!hasText(rawCountry)) {
            return null;
        }
        Optional<CountrySponsorshipProfile> catalogMatch = countrySponsorshipCatalog.findCountryProfile(rawCountry);
        if (catalogMatch.isPresent()) {
            return catalogMatch.get().country();
        }
        String normalized = jobNormalizer.normalizeCountry(rawCountry);
        return hasText(normalized) ? normalized : rawCountry.trim();
    }

    private Company requireCompany(Long companyId) {
        if (companyId == null || companyId <= 0) {
            throw new BusinessValidationException("Company ID must be a positive integer");
        }
        return companyRepository.findById(companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Company not found with id: " + companyId));
    }

    private Long requireUserId(AuthenticatedUserPrincipal principal) {
        if (principal == null || principal.getId() == null) {
            throw new UnauthorizedAccessException("Authenticated user context is required");
        }
        return principal.getId();
    }

    private User requireAuthenticatedUser(AuthenticatedUserPrincipal principal) {
        Long userId = requireUserId(principal);
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Authenticated user no longer exists"));
    }

    private String sanitizeHttpUrl(String rawUrl) {
        if (!hasText(rawUrl)) {
            return null;
        }
        try {
            URI uri = URI.create(rawUrl.trim());
            String scheme = uri.getScheme();
            if (scheme == null
                    || (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme))
                    || !hasText(uri.getHost())) {
                return null;
            }
            return uri.toString();
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private <T> List<T> safeList(List<T> items) {
        return items == null ? List.of() : items;
    }

    private record JobMetrics(long activeJobsCount, long sponsorshipJobsCount, long remoteJobsCount) {
    }
}

