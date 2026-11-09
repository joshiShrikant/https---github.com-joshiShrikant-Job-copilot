package com.eurojobcopilot.modules.user.service;

import com.eurojobcopilot.modules.auth.security.AuthenticatedUserPrincipal;
import com.eurojobcopilot.modules.user.dto.JobPreferencesResponse;
import com.eurojobcopilot.modules.user.dto.UpdateJobPreferencesRequest;

public interface JobPreferenceService {

    JobPreferencesResponse getPreferences(AuthenticatedUserPrincipal currentUser);

    JobPreferencesResponse updatePreferences(
            AuthenticatedUserPrincipal currentUser,
            UpdateJobPreferencesRequest request
    );
}

