package com.eurojobcopilot.modules.sponsorship.ind;

import com.eurojobcopilot.modules.sponsorship.config.SponsorshipProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.sponsorship.ind", name = "scheduler-enabled", havingValue = "true")
public class IndRegisterScheduler {

    private final IndRegisterSyncService syncService;
    private final SponsorshipProperties properties;

    @Scheduled(
            cron = "${app.sponsorship.ind.sync-cron:0 15 3 * * *}",
            zone = "${app.sponsorship.ind.sync-zone:UTC}"
    )
    public void scheduledIndRegisterSync() {
        if (!properties.getInd().isEnabled()) {
            return;
        }
        try {
            var result = syncService.syncIndRegister();
            log.info("Scheduled NL IND sync finished status={} upserted={} companiesLinked={}",
                    result.getStatus(), result.getEntriesUpserted(), result.getCompaniesLinked());
        } catch (Exception ex) {
            log.warn("Scheduled NL IND sync skipped/failed: {}", ex.getMessage());
        }
    }
}

