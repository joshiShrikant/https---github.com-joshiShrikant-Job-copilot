package com.eurojobcopilot.modules.matching.mapper;

import com.eurojobcopilot.modules.job.dto.JobMatchBreakdownDto;
import com.eurojobcopilot.modules.matching.entity.JobMatchScore;

import java.math.RoundingMode;
import java.util.List;

/**
 * Shared read-only mapper keeping deterministic breakdown and AI semantic reasoning consistently
 * mapped across job detail, deterministic scoring, and semantic matching endpoints.
 */
public final class JobMatchBreakdownMapper {

    private JobMatchBreakdownMapper() {
    }

    public static JobMatchBreakdownDto toDto(JobMatchScore entity) {
        if (entity == null) {
            return null;
        }
        Integer rounded = entity.getFinalScore() != null
                ? Math.max(0, Math.min(100, entity.getFinalScore().setScale(0, RoundingMode.HALF_UP).intValue()))
                : null;
        return JobMatchBreakdownDto.builder()
                .finalScore(entity.getFinalScore())
                .matchPercentage(rounded)
                .deterministicScore(entity.getDeterministicScore())
                .aiScore(entity.getAiScore())
                .skillScore(entity.getSkillScore())
                .experienceScore(entity.getExperienceScore())
                .titleScore(entity.getTitleScore())
                .locationScore(entity.getLocationScore())
                .sponsorshipScore(entity.getSponsorshipScore())
                .salaryScore(entity.getSalaryScore())
                .matchingSkills(safeCopy(entity.getMatchingSkills()))
                .missingSkills(safeCopy(entity.getMissingSkills()))
                .experienceCompatibility(entity.getExperienceCompatibility())
                .recommendationReasons(safeCopy(entity.getRecommendationReasons()))
                .aiRationale(entity.getAiRationale())
                .aiStrongMatches(safeCopy(entity.getAiStrongMatches()))
                .aiMissingSkills(safeCopy(entity.getAiMissingSkills()))
                .aiExperienceGaps(safeCopy(entity.getAiExperienceGaps()))
                .aiPotentialConcerns(safeCopy(entity.getAiPotentialConcerns()))
                .aiImprovementSuggestions(safeCopy(entity.getAiImprovementSuggestions()))
                .aiProvider(entity.getAiProvider())
                .aiModelUsed(entity.getAiModelUsed())
                .aiEvaluatedAt(entity.getAiEvaluatedAt())
                .calculatedAt(entity.getCalculatedAt())
                .build();
    }

    private static List<String> safeCopy(List<String> values) {
        return values == null ? List.of() : List.copyOf(values);
    }
}

