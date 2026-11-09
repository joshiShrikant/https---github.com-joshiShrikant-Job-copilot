package com.eurojobcopilot.modules.notification.service;

import com.eurojobcopilot.modules.auth.entity.User;
import com.eurojobcopilot.modules.notification.config.NotificationProperties;
import com.eurojobcopilot.modules.user.entity.UserJobPreference;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Property-gated autopilot email digest notifier.
 * Never claims email dispatch when global relay (app.notifications.email.enabled)
 * or per-user opt-in (emailNotificationsEnabled) is false.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LoggingAutopilotEmailNotifier implements AutopilotEmailNotifier {

    private final NotificationProperties properties;

    @Override
    public boolean dispatchDigestIfEnabled(
            User user,
            UserJobPreference preference,
            List<String> highlightSummaries,
            int notificationsCreated
    ) {
        if (user == null || user.getEmail() == null || user.getEmail().isBlank()) {
            return false;
        }
        if (properties.getEmail() == null || !properties.getEmail().isEnabled()) {
            log.debug("Email digest skipped for userId={} because app.notifications.email.enabled=false", user.getId());
            return false;
        }
        if (preference == null || !preference.isEmailNotificationsEnabled()) {
            log.debug("Email digest skipped for userId={} because user opted out of email notifications", user.getId());
            return false;
        }
        if (notificationsCreated <= 0 && (highlightSummaries == null || highlightSummaries.isEmpty())) {
            return false;
        }

        log.info("Autopilot digest email queued from={} to={} newAlerts={} highlights={}",
                properties.getEmail().getFromAddress(),
                user.getEmail(),
                notificationsCreated,
                highlightSummaries != null ? highlightSummaries.size() : 0);
        return true;
    }
}

