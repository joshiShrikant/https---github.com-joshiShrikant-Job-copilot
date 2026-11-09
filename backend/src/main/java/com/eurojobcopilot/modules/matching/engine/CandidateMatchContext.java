package com.eurojobcopilot.modules.matching.engine;

import com.eurojobcopilot.modules.user.entity.UserJobPreference;
import com.eurojobcopilot.modules.user.entity.UserProfile;
import com.eurojobcopilot.modules.user.entity.UserSkill;
import com.eurojobcopilot.modules.user.entity.UserWorkExperience;

import java.util.List;

/**
 * Read-only candidate snapshot used by {@link DeterministicJobMatchingEngine} to score jobs fairly
 * without database lazy-loading coupling inside unit tests.
 */
public record CandidateMatchContext(
        UserProfile profile,
        List<UserSkill> skills,
        List<UserWorkExperience> workExperiences,
        UserJobPreference preference
) {
    public CandidateMatchContext {
        skills = skills == null ? List.of() : List.copyOf(skills);
        workExperiences = workExperiences == null ? List.of() : List.copyOf(workExperiences);
    }

    public static CandidateMatchContext empty() {
        return new CandidateMatchContext(null, List.of(), List.of(), null);
    }
}

