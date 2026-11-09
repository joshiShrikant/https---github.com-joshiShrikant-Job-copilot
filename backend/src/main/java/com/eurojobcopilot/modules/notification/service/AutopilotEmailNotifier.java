package com.eurojobcopilot.modules.notification.service;

import com.eurojobcopilot.modules.auth.entity.User;
import com.eurojobcopilot.modules.user.entity.UserJobPreference;

import java.util.List;

public interface AutopilotEmailNotifier {

    boolean dispatchDigestIfEnabled(
            User user,
            UserJobPreference preference,
            List<String> highlightSummaries,
            int notificationsCreated
    );
}

