package com.eurojobcopilot.modules.analytics.service;

import com.eurojobcopilot.core.enums.WorkArrangement;
import com.eurojobcopilot.core.exception.UnauthorizedAccessException;
import com.eurojobcopilot.modules.analytics.dto.CountryMarketInsightDto;
import com.eurojobcopilot.modules.analytics.dto.DiscoveryTrendPointDto;
import com.eurojobcopilot.modules.analytics.dto.MarketInsightsResponse;
import com.eurojobcopilot.modules.analytics.dto.MarketTotalsDto;
import com.eurojobcopilot.modules.analytics.dto.SourceCoverageDto;
import com.eurojobcopilot.modules.analytics.service.MarketJobClassifier.EnglishLanguageStatus;
import com.eurojobcopilot.modules.analytics.service.MarketJobClassifier.JobClassification;
import com.eurojobcopilot.modules.auth.security.AuthenticatedUserPrincipal;
import com.eurojobcopilot.modules.job.entity.Job;
import com.eurojobcopilot.modules.job.entity.JobAiAnalysis;
import com.eurojobcopilot.modules.job.entity.JobFetchLog;
import com.eurojobcopilot.modules.job.entity.JobSponsorStatus;
import com.eurojobcopilot.modules.job.normalization.JobNormalizer;
import com.eurojobcopilot.modules.job.repository.JobAiAnalysisRepository;
import com.eurojobcopilot.modules.job.repository.JobFetchLogRepository;
import com.eurojobcopilot.modules.job.repository.JobRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class MarketAnalyticsServiceImpl implements MarketAnalyticsService {

    private static final int DEFAULT_TREND_DAYS = 30;
    private static final int MIN_TREND_DAYS = 7;
    private static final int MAX_TREND_DAYS = 180;

    private static final String COVERAGE_DISCLAIMER =
            "Analytics reflect only active job listings collected via EuroJob Copilot's configured discovery "
                    + "connectors (e.g., Arbeitnow, Adzuna) and do not represent a complete national or regional "
                    + "labor-market census. Salary ranges are normalized to annual compensation within the dominant "
                    + "reporting currency per country when salary data is provided by the posting.";

    private static final Set<JobSponsorStatus> SPONSORSHIP_STATUSES = EnumSet.of(
            JobSponsorStatus.VERIFIED_SPONSOR,
            JobSponsorStatus.SPONSORSHIP_MENTIONED,
            JobSponsorStatus.RELOCATION_OFFERED
    );

    private final JobRepository jobRepository;
    private final JobAiAnalysisRepository jobAiAnalysisRepository;
    private final JobFetchLogRepository jobFetchLogRepository;
    private final JobNormalizer jobNormalizer;
    private final MarketJobClassifier classifier;

    @Override
    @Transactional(readOnly = true)
    public MarketInsightsResponse getMarketInsights(
            AuthenticatedUserPrincipal currentUser,
            List<String> countries,
            int trendWindowDays
    ) {
        if (currentUser == null || currentUser.getId() == null) {
            throw new UnauthorizedAccessException("Authenticated user context is required");
        }

        Instant now = Instant.now();
        int clampedDays = Math.max(MIN_TREND_DAYS, Math.min(MAX_TREND_DAYS, trendWindowDays > 0 ? trendWindowDays : DEFAULT_TREND_DAYS));

        List<Job> activeJobs = jobRepository.findAllByActiveTrue().stream()
                .filter(job -> job.getExpiresAt() == null || !job.getExpiresAt().isBefore(now))
                .toList();

        Map<Long, JobAiAnalysis> aiAnalysesByJobId = loadAnalyses(activeJobs);
        Map<Job, JobClassification> classifications = new LinkedHashMap<>(activeJobs.size());
        for (Job job : activeJobs) {
            classifications.put(job, classifier.classify(job, aiAnalysesByJobId.get(job.getId())));
        }

        Map<String, List<Job>> jobsByCountry = activeJobs.stream()
                .filter(job -> job.getCountry() != null && !job.getCountry().isBlank())
                .collect(Collectors.groupingBy(job -> job.getCountry().trim(), LinkedHashMap::new, Collectors.toList()));

        List<String> availableCountries = jobsByCountry.entrySet().stream()
                .sorted(Comparator
                        .<Map.Entry<String, List<Job>>>comparingInt(e -> e.getValue().size()).reversed()
                        .thenComparing(Map.Entry::getKey, String.CASE_INSENSITIVE_ORDER))
                .map(Map.Entry::getKey)
                .toList();

        List<String> selectedCountries = resolveSelectedCountries(countries, availableCountries);
        Set<String> selectedCountryLower = selectedCountries.stream()
                .map(c -> c.toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());

        List<Job> scopedJobs = selectedCountryLower.isEmpty()
                ? activeJobs
                : activeJobs.stream()
                .filter(j -> j.getCountry() != null && selectedCountryLower.contains(j.getCountry().trim().toLowerCase(Locale.ROOT)))
                .toList();

        MarketTotalsDto totals = computeTotals(scopedJobs, classifications);

        List<CountryMarketInsightDto> countryComparisons = selectedCountries.stream()
                .map(country -> buildCountryInsight(
                        country,
                        jobsByCountry.getOrDefault(country, List.of()),
                        classifications
                ))
                .toList();

        List<DiscoveryTrendPointDto> discoveryTrends = buildDiscoveryTrends(scopedJobs, classifications, now, clampedDays);
        List<JobFetchLog> fetchLogs = jobFetchLogRepository.findAll();
        List<SourceCoverageDto> sourceCoverage = buildSourceCoverage(activeJobs, fetchLogs);
        Instant lastRefreshAt = resolveLastDataRefresh(activeJobs, fetchLogs);

        log.debug("Computed market insights userUuid={} totalActive={} selectedCountries={}",
                currentUser.getUuid(), activeJobs.size(), selectedCountries);

        return MarketInsightsResponse.builder()
                .generatedAt(now)
                .lastDataRefreshAt(lastRefreshAt)
                .dataCoverageDisclaimer(COVERAGE_DISCLAIMER)
                .trendWindowDays(clampedDays)
                .availableCountries(availableCountries)
                .selectedCountries(selectedCountries)
                .totals(totals)
                .countryComparisons(countryComparisons)
                .discoveryTrends(discoveryTrends)
                .sourceCoverage(sourceCoverage)
                .build();
    }

    private Map<Long, JobAiAnalysis> loadAnalyses(List<Job> jobs) {
        if (jobs.isEmpty()) {
            return Map.of();
        }
        List<Long> jobIds = jobs.stream().map(Job::getId).toList();
        return jobAiAnalysisRepository.findAllByJobIdIn(jobIds).stream()
                .collect(Collectors.toMap(
                        a -> a.getJob().getId(),
                        Function.identity(),
                        (first, second) -> first
                ));
    }

    private List<String> resolveSelectedCountries(List<String> requested, List<String> availableCountries) {
        if (requested == null || requested.isEmpty()) {
            return availableCountries;
        }
        Map<String, String> availableLowerLookup = new HashMap<>();
        for (String country : availableCountries) {
            availableLowerLookup.put(country.toLowerCase(Locale.ROOT), country);
        }

        LinkedHashSet<String> resolved = new LinkedHashSet<>();
        for (String raw : requested) {
            if (raw == null || raw.isBlank()) {
                continue;
            }
            for (String token : raw.split(",")) {
                String trimmed = token.trim();
                if (trimmed.isEmpty()) {
                    continue;
                }
                String normalized = jobNormalizer.normalizeCountry(trimmed);
                String canonical = availableLowerLookup.getOrDefault(
                        normalized.toLowerCase(Locale.ROOT),
                        normalized
                );
                resolved.add(canonical);
            }
        }
        return resolved.isEmpty() ? availableCountries : List.copyOf(resolved);
    }

    private MarketTotalsDto computeTotals(List<Job> jobs, Map<Job, JobClassification> classifications) {
        long angular = 0;
        long frontend = 0;
        long java = 0;
        long fullStack = 0;
        long sponsorship = 0;
        long verified = 0;
        long english = 0;
        long localLang = 0;
        long unspecifiedLang = 0;
        long remote = 0;
        long hybrid = 0;
        long onsite = 0;

        for (Job job : jobs) {
            JobClassification c = classifications.get(job);
            if (c.angular()) angular++;
            if (c.frontend()) frontend++;
            if (c.java()) java++;
            if (c.fullStack()) fullStack++;

            if (job.getSponsorStatus() != null && SPONSORSHIP_STATUSES.contains(job.getSponsorStatus())) {
                sponsorship++;
            }
            if (job.getSponsorStatus() == JobSponsorStatus.VERIFIED_SPONSOR) {
                verified++;
            }

            switch (c.englishStatus()) {
                case CONFIRMED -> english++;
                case LOCAL_LANGUAGE_REQUIRED -> localLang++;
                case UNSPECIFIED -> unspecifiedLang++;
            }

            WorkArrangement arrangement = job.getWorkArrangement() != null ? job.getWorkArrangement() : WorkArrangement.ONSITE;
            switch (arrangement) {
                case REMOTE -> remote++;
                case HYBRID -> hybrid++;
                case ONSITE, ANY -> onsite++;
            }
        }

        return MarketTotalsDto.builder()
                .totalActiveJobs(jobs.size())
                .angularJobs(angular)
                .frontendJobs(frontend)
                .javaJobs(java)
                .fullStackJobs(fullStack)
                .sponsorshipOpportunities(sponsorship)
                .verifiedSponsorJobs(verified)
                .englishConfirmedJobs(english)
                .localLanguageRequiredJobs(localLang)
                .languageUnspecifiedJobs(unspecifiedLang)
                .remoteJobs(remote)
                .hybridJobs(hybrid)
                .onsiteJobs(onsite)
                .build();
    }

    private CountryMarketInsightDto buildCountryInsight(
            String country,
            List<Job> countryJobs,
            Map<Job, JobClassification> classifications
    ) {
        MarketTotalsDto totals = computeTotals(countryJobs, classifications);

        Map<String, List<BigDecimal>> salariesByCurrency = new LinkedHashMap<>();
        for (Job job : countryJobs) {
            BigDecimal normalizedSalary = classifier.normalizeAnnualSalaryMidpoint(job);
            if (normalizedSalary != null) {
                String currency = (job.getSalaryCurrency() != null && !job.getSalaryCurrency().isBlank())
                        ? job.getSalaryCurrency().trim().toUpperCase(Locale.ROOT)
                        : "EUR";
                salariesByCurrency.computeIfAbsent(currency, ignored -> new ArrayList<>()).add(normalizedSalary);
            }
        }

        String primaryCurrency = null;
        BigDecimal min = null;
        BigDecimal median = null;
        BigDecimal max = null;
        long sampleCount = 0;

        if (!salariesByCurrency.isEmpty()) {
            Map.Entry<String, List<BigDecimal>> dominant = salariesByCurrency.entrySet().stream()
                    .max(Comparator.<Map.Entry<String, List<BigDecimal>>>comparingInt(e -> e.getValue().size())
                            .thenComparing(e -> "EUR".equals(e.getKey()) ? 1 : 0))
                    .orElseThrow();
            primaryCurrency = dominant.getKey();
            List<BigDecimal> sorted = dominant.getValue().stream().sorted().toList();
            sampleCount = sorted.size();
            min = sorted.getFirst();
            max = sorted.getLast();
            median = computeMedian(sorted);
        }

        return CountryMarketInsightDto.builder()
                .country(country)
                .totalJobs(totals.getTotalActiveJobs())
                .angularJobs(totals.getAngularJobs())
                .frontendJobs(totals.getFrontendJobs())
                .javaJobs(totals.getJavaJobs())
                .fullStackJobs(totals.getFullStackJobs())
                .sponsorshipOpportunities(totals.getSponsorshipOpportunities())
                .verifiedSponsorJobs(totals.getVerifiedSponsorJobs())
                .englishConfirmedJobs(totals.getEnglishConfirmedJobs())
                .localLanguageRequiredJobs(totals.getLocalLanguageRequiredJobs())
                .languageUnspecifiedJobs(totals.getLanguageUnspecifiedJobs())
                .remoteJobs(totals.getRemoteJobs())
                .hybridJobs(totals.getHybridJobs())
                .onsiteJobs(totals.getOnsiteJobs())
                .salarySampleCount(sampleCount)
                .primarySalaryCurrency(primaryCurrency)
                .salaryMin(min)
                .salaryMedian(median)
                .salaryMax(max)
                .build();
    }

    private BigDecimal computeMedian(List<BigDecimal> sorted) {
        int n = sorted.size();
        if (n == 0) {
            return null;
        }
        if (n % 2 == 1) {
            return sorted.get(n / 2);
        }
        return sorted.get((n / 2) - 1)
                .add(sorted.get(n / 2))
                .divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP);
    }

    private List<DiscoveryTrendPointDto> buildDiscoveryTrends(
            List<Job> scopedJobs,
            Map<Job, JobClassification> classifications,
            Instant now,
            int windowDays
    ) {
        LocalDate endDate = now.atZone(ZoneOffset.UTC).toLocalDate();
        LocalDate startDate = endDate.minusDays(windowDays - 1L);

        Map<LocalDate, TrendAccumulator> buckets = new TreeMap<>();
        for (int i = 0; i < windowDays; i++) {
            buckets.put(startDate.plusDays(i), new TrendAccumulator());
        }

        for (Job job : scopedJobs) {
            Instant stamp = job.getPostedAt() != null ? job.getPostedAt() : job.getCreatedAt();
            if (stamp == null) {
                continue;
            }
            LocalDate day = stamp.atZone(ZoneOffset.UTC).toLocalDate();
            if (day.isBefore(startDate) || day.isAfter(endDate)) {
                continue;
            }
            TrendAccumulator acc = buckets.get(day);
            if (acc == null) {
                continue;
            }
            JobClassification c = classifications.get(job);
            acc.total++;
            if (c.angular()) acc.angular++;
            if (c.frontend()) acc.frontend++;
            if (c.java()) acc.java++;
            if (c.fullStack()) acc.fullStack++;
            if (job.getSponsorStatus() != null && SPONSORSHIP_STATUSES.contains(job.getSponsorStatus())) {
                acc.sponsorship++;
            }
            if (job.getWorkArrangement() == WorkArrangement.REMOTE) {
                acc.remote++;
            }
        }

        return buckets.entrySet().stream()
                .map(entry -> DiscoveryTrendPointDto.builder()
                        .date(entry.getKey().toString())
                        .totalDiscovered(entry.getValue().total)
                        .angularDiscovered(entry.getValue().angular)
                        .frontendDiscovered(entry.getValue().frontend)
                        .javaDiscovered(entry.getValue().java)
                        .fullStackDiscovered(entry.getValue().fullStack)
                        .sponsorshipDiscovered(entry.getValue().sponsorship)
                        .remoteDiscovered(entry.getValue().remote)
                        .build())
                .toList();
    }

    private List<SourceCoverageDto> buildSourceCoverage(List<Job> activeJobs, List<JobFetchLog> fetchLogs) {
        Map<String, Long> jobCountsByProvider = activeJobs.stream()
                .collect(Collectors.groupingBy(
                        job -> normalizeProvider(job.getSourceProvider()),
                        Collectors.counting()
                ));

        Map<String, List<JobFetchLog>> logsByProvider = fetchLogs.stream()
                .collect(Collectors.groupingBy(
                        logEntry -> normalizeProvider(logEntry.getProviderName())
                ));

        Set<String> allProviders = new LinkedHashSet<>();
        allProviders.addAll(jobCountsByProvider.keySet());
        allProviders.addAll(logsByProvider.keySet());

        long totalJobs = activeJobs.size();
        List<SourceCoverageDto> list = new ArrayList<>(allProviders.size());

        for (String provider : allProviders) {
            long count = jobCountsByProvider.getOrDefault(provider, 0L);
            BigDecimal share = totalJobs > 0
                    ? BigDecimal.valueOf(count * 100.0 / totalJobs).setScale(1, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO.setScale(1, RoundingMode.HALF_UP);

            List<JobFetchLog> providerLogs = logsByProvider.getOrDefault(provider, List.of());
            Optional<JobFetchLog> latestLog = providerLogs.stream()
                    .filter(l -> l.getFetchedAt() != null)
                    .max(Comparator.comparing(JobFetchLog::getFetchedAt));

            list.add(SourceCoverageDto.builder()
                    .sourceProvider(provider)
                    .activeJobsCount(count)
                    .sharePercentage(share)
                    .lastFetchAt(latestLog.map(JobFetchLog::getFetchedAt).orElse(null))
                    .lastFetchStatus(latestLog.map(l -> l.getStatus().name()).orElse("NO_LOG_RECORDED"))
                    .totalRunsLogged(providerLogs.size())
                    .build());
        }

        list.sort(Comparator.comparingLong(SourceCoverageDto::getActiveJobsCount).reversed()
                .thenComparing(SourceCoverageDto::getSourceProvider));
        return list;
    }

    private Instant resolveLastDataRefresh(List<Job> activeJobs, List<JobFetchLog> fetchLogs) {
        Instant latestFetch = fetchLogs.stream()
                .map(JobFetchLog::getFetchedAt)
                .filter(java.util.Objects::nonNull)
                .max(Instant::compareTo)
                .orElse(null);
        Instant latestJob = activeJobs.stream()
                .map(job -> job.getUpdatedAt() != null ? job.getUpdatedAt() : job.getPostedAt())
                .filter(java.util.Objects::nonNull)
                .max(Instant::compareTo)
                .orElse(null);

        if (latestFetch == null) {
            return latestJob;
        }
        if (latestJob == null) {
            return latestFetch;
        }
        return latestFetch.isAfter(latestJob) ? latestFetch : latestJob;
    }

    private String normalizeProvider(String provider) {
        if (provider == null || provider.isBlank()) {
            return "UNKNOWN";
        }
        return provider.trim().toUpperCase(Locale.ROOT);
    }

    private static final class TrendAccumulator {
        long total;
        long angular;
        long frontend;
        long java;
        long fullStack;
        long sponsorship;
        long remote;
    }
}

