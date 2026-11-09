package com.eurojobcopilot.modules.autopilot.service;

import com.eurojobcopilot.modules.auth.security.AuthenticatedUserPrincipal;
import com.eurojobcopilot.modules.autopilot.config.AutopilotProperties;
import com.eurojobcopilot.modules.user.entity.UserJobPreference;
import com.eurojobcopilot.modules.user.repository.UserJobPreferenceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class AutopilotScheduler {

    private final AutopilotProperties properties;
    private final UserJobPreferenceRepository preferenceRepository;
    private final AutopilotService autopilotService;

    @Scheduled(
            cron = "${app.autopilot.scheduler.cron:0 15 */6 * * *}",
            zone = "${app.autopilot.scheduler.zone:UTC}"
    )
    public void runScheduledAutopilot() {
        if (properties.getScheduler() == null || !properties.getScheduler().isEnabled()) {
            return;
        }

        List<UserJobPreference> subscribers = preferenceRepository.findAllActiveAutopilotSubscribers();
        if (subscribers.isEmpty()) {
            return;
        }

        log.info("Running scheduled autopilot scan for {} active subscriber(s)", subscribers.size());
        for (UserJobPreference pref : subscribers) {
            try {
                AuthenticatedUserPrincipal principal = AuthenticatedUserPrincipal.from(pref.getUser());
                autopilotService.executeScheduledScan(principal);
            } catch (RuntimeException ex) {
                log.error("Scheduled autopilot scan failed for userId={}: {}",
                        pref.getUser() != null ? pref.getUser().getId() : null, ex.getMessage(), ex);
            }
        }
    }
}

