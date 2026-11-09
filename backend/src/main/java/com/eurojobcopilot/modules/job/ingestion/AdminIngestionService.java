package com.eurojobcopilot.modules.job.ingestion;

import com.eurojobcopilot.core.exception.BusinessValidationException;
import com.eurojobcopilot.core.exception.ResourceNotFoundException;
import com.eurojobcopilot.modules.job.entity.FetchStatus;
import com.eurojobcopilot.modules.job.entity.JobFetchLog;
import com.eurojobcopilot.modules.job.ingestion.dto.AdminIngestionTriggerResponse;
import com.eurojobcopilot.modules.job.ingestion.dto.IngestionStatusResponse;
import com.eurojobcopilot.modules.job.ingestion.dto.JobFetchLogDto;
import com.eurojobcopilot.modules.job.ingestion.dto.PagedFetchLogsResponse;
import com.eurojobcopilot.modules.job.ingestion.dto.ProviderHealthStatusDto;
import com.eurojobcopilot.modules.job.ingestion.dto.StaleJobCleanupResponse;
import com.eurojobcopilot.modules.job.ingestion.dto.TriggerIngestionRequest;
import com.eurojobcopilot.modules.job.repository.JobFetchLogRepository;
import com.eurojobcopilot.modules.job.repository.JobRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Administrative service orchestrating manual ingestion triggers, provider health status,
 * paginated fetch-log queries, and expired job cleanup.
 */
@Service
@RequiredArgsConstructor
public class AdminIngestionService {

    private static final List<String> DEFAULT_KEYWORDS = List.of(
            "Java", "Spring", "Angular", "Backend", "Full Stack", "Software Engineer"
    );

    private final JobIngestionService jobIngestionService;
    private final JobMaintenanceService jobMaintenanceService;
    private final JobIngestionProperties properties;
    private final JobFetchLogRepository jobFetchLogRepository;
    private final JobRepository jobRepository;

    public AdminIngestionTriggerResponse triggerManualIngestion(
            String queryProvider,
            TriggerIngestionRequest request
    ) {
        String requestedProvider = resolveRequestedProvider(queryProvider, request);
        IngestionQuery query = buildIngestionQuery(request);

        if (requestedProvider != null) {
            JobIngestionService.ProviderRegistration registration = jobIngestionService.getRegisteredProviders()
                    .stream()
                    .filter(p -> p.providerName().equalsIgnoreCase(requestedProvider))
                    .findFirst()
                    .orElseThrow(() -> new ResourceNotFoundException("Unknown job provider: " + requestedProvider));

            if (!registration.enabled()) {
                throw new BusinessValidationException(
                        "Job provider '" + registration.providerName()
                                + "' is currently disabled or missing required credentials"
                );
            }

            Instant startedAt = Instant.now();
            ProviderIngestionResult result = jobIngestionService.ingestFromProvider(registration.providerName(), query);
            Instant completedAt = Instant.now();

            return AdminIngestionTriggerResponse.builder()
                    .scope(registration.providerName())
                    .runStartedAt(startedAt)
                    .runCompletedAt(completedAt)
                    .totalDurationMs(result.executionDurationMs())
                    .providersExecuted(1)
                    .totalFetched(result.jobsFetched())
                    .totalInserted(result.jobsInserted())
                    .totalUpdated(result.jobsUpdated())
                    .totalSkippedDuplicates(result.jobsSkippedDuplicates())
                    .totalFailedNormalization(result.jobsFailedNormalization())
                    .providerResults(List.of(result))
                    .build();
        }

        IngestionExecutionSummary summary = jobIngestionService.ingestAll(query);
        return AdminIngestionTriggerResponse.builder()
                .scope("ALL")
                .runStartedAt(summary.runStartedAt())
                .runCompletedAt(summary.runCompletedAt())
                .totalDurationMs(summary.totalDurationMs())
                .providersExecuted(summary.providersExecuted())
                .totalFetched(summary.totalFetched())
                .totalInserted(summary.totalInserted())
                .totalUpdated(summary.totalUpdated())
                .totalSkippedDuplicates(summary.totalSkippedDuplicates())
                .totalFailedNormalization(summary.totalFailedNormalization())
                .providerResults(summary.providerResults())
                .build();
    }

    @Transactional(readOnly = true)
    public IngestionStatusResponse getStatus() {
        IngestionRuntimeSnapshot snapshot = jobIngestionService.getRuntimeSnapshot();
        JobIngestionProperties.SchedulerProperties scheduler = properties.getScheduler();

        List<ProviderHealthStatusDto> providerStatuses = jobIngestionService.getRegisteredProviders()
                .stream()
                .map(reg -> ProviderHealthStatusDto.builder()
                        .providerName(reg.providerName())
                        .enabled(reg.enabled())
                        .lastRun(jobFetchLogRepository
                                .findTopByProviderNameOrderByFetchedAtDesc(reg.providerName())
                                .map(this::toLogDto)
                                .orElse(null))
                        .build())
                .toList();

        return IngestionStatusResponse.builder()
                .ingestionEnabled(properties.isEnabled())
                .schedulerEnabled(scheduler != null && scheduler.isEnabled())
                .cronExpression(scheduler != null ? scheduler.getCron() : null)
                .cronZone(scheduler != null ? scheduler.getZone() : "UTC")
                .staleExpirationDays(scheduler != null ? scheduler.getStaleExpirationDays() : 45)
                .running(snapshot.running())
                .activeTarget(snapshot.activeTarget())
                .lastRunStartedAt(snapshot.lastRunStartedAt())
                .lastRunCompletedAt(snapshot.lastRunCompletedAt())
                .activeJobsCount(jobRepository.countByActiveTrue())
                .totalJobsCount(jobRepository.count())
                .providers(providerStatuses)
                .build();
    }

    @Transactional(readOnly = true)
    public PagedFetchLogsResponse listFetchLogs(String provider, FetchStatus status, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        String normalizedProvider = provider != null && !provider.isBlank() ? provider.trim() : null;

        Page<JobFetchLog> resultPage;
        if (normalizedProvider != null && status != null) {
            resultPage = jobFetchLogRepository.findAllByProviderNameIgnoreCaseAndStatusOrderByFetchedAtDesc(
                    normalizedProvider, status, pageable
            );
        } else if (normalizedProvider != null) {
            resultPage = jobFetchLogRepository.findAllByProviderNameIgnoreCaseOrderByFetchedAtDesc(
                    normalizedProvider, pageable
            );
        } else if (status != null) {
            resultPage = jobFetchLogRepository.findAllByStatusOrderByFetchedAtDesc(status, pageable);
        } else {
            resultPage = jobFetchLogRepository.findAllByOrderByFetchedAtDesc(pageable);
        }

        return PagedFetchLogsResponse.builder()
                .items(resultPage.getContent().stream().map(this::toLogDto).toList())
                .page(resultPage.getNumber())
                .size(resultPage.getSize())
                .totalElements(resultPage.getTotalElements())
                .totalPages(resultPage.getTotalPages())
                .last(resultPage.isLast())
                .build();
    }

    public StaleJobCleanupResponse cleanupStaleJobs(Integer staleDays) {
        int defaultDays = properties.getScheduler() != null
                ? properties.getScheduler().getStaleExpirationDays()
                : 45;
        int effectiveDays = staleDays != null ? Math.max(1, staleDays) : Math.max(1, defaultDays);
        int deactivated = jobMaintenanceService.deactivateExpiredOrStaleJobs(effectiveDays);
        return StaleJobCleanupResponse.builder()
                .deactivatedCount(deactivated)
                .staleExpirationDays(effectiveDays)
                .executedAt(Instant.now())
                .build();
    }

    private String resolveRequestedProvider(String queryProvider, TriggerIngestionRequest request) {
        if (queryProvider != null && !queryProvider.isBlank()) {
            return queryProvider.trim();
        }
        if (request != null && request.provider() != null && !request.provider().isBlank()) {
            return request.provider().trim();
        }
        return null;
    }

    private IngestionQuery buildIngestionQuery(TriggerIngestionRequest request) {
        List<String> countries = request != null && request.countries() != null && !request.countries().isEmpty()
                ? request.countries()
                : properties.getTargetCountries();
        List<String> keywords = request != null && request.keywords() != null && !request.keywords().isEmpty()
                ? request.keywords()
                : DEFAULT_KEYWORDS;
        int maxPages = request != null && request.maxPages() != null
                ? request.maxPages()
                : properties.getDefaultMaxPages();
        boolean remoteOnly = request != null && Boolean.TRUE.equals(request.remoteOnly());
        Instant since = request != null && request.sinceDays() != null
                ? Instant.now().minus(request.sinceDays(), ChronoUnit.DAYS)
                : null;

        return IngestionQuery.builder()
                .countries(countries)
                .keywords(keywords)
                .maxPages(maxPages)
                .pageSize(50)
                .remoteOnly(remoteOnly)
                .since(since)
                .build();
    }

    private JobFetchLogDto toLogDto(JobFetchLog entity) {
        return JobFetchLogDto.builder()
                .id(entity.getId())
                .providerName(entity.getProviderName())
                .status(entity.getStatus())
                .jobsFetched(entity.getJobsFetched())
                .jobsInserted(entity.getJobsInserted())
                .jobsUpdated(entity.getJobsUpdated())
                .jobsSkippedDuplicates(entity.getJobsSkippedDuplicates())
                .executionDurationMs(entity.getExecutionDurationMs())
                .errorMessage(entity.getErrorMessage())
                .fetchedAt(entity.getFetchedAt())
                .build();
    }
}

