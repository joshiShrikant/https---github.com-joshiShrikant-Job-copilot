package com.eurojobcopilot.modules.job.repository;

import com.eurojobcopilot.core.enums.WorkArrangement;
import com.eurojobcopilot.modules.job.dto.JobSearchCriteria;
import com.eurojobcopilot.modules.job.entity.Job;
import com.eurojobcopilot.modules.job.entity.UserJobInteraction;
import com.eurojobcopilot.modules.matching.entity.JobMatchScore;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.hibernate.query.criteria.JpaExpression;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Composable JPA Criteria predicates for multi-facet job search, sponsorship filtering,
 * user-scoped match score thresholds, and hidden-job exclusion.
 */
public final class JobSpecifications {

    private static final int MAX_QUERY_TOKENS = 5;
    private static final char LIKE_ESCAPE_CHAR = '\\';

    private JobSpecifications() {
    }

    public static Specification<Job> activeJobsMatching(
            Long userId,
            JobSearchCriteria criteria,
            String normalizedCountry,
            Instant now
    ) {
        return (root, query, cb) -> buildPredicate(
                root, query, cb, userId, criteria, normalizedCountry, now
        );
    }

    public static Predicate buildPredicate(
            Root<Job> root,
            CriteriaQuery<?> query,
            CriteriaBuilder cb,
            Long userId,
            JobSearchCriteria criteria,
            String normalizedCountry,
            Instant now
    ) {
        List<Predicate> predicates = new ArrayList<>();

        predicates.add(cb.isTrue(root.get("active")));
        if (now != null) {
            predicates.add(cb.or(
                    cb.isNull(root.get("expiresAt")),
                    cb.greaterThanOrEqualTo(root.get("expiresAt"), now)
            ));
        }

        if (criteria == null) {
            return cb.and(predicates.toArray(Predicate[]::new));
        }

        if (hasText(criteria.query())) {
            Set<String> tokens = extractSearchTokens(criteria.query());
            for (String token : tokens) {
                String pattern = "%" + escapeLike(token) + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("title")), pattern, LIKE_ESCAPE_CHAR),
                        cb.like(cb.lower(root.get("normalizedTitle")), pattern, LIKE_ESCAPE_CHAR),
                        cb.like(cb.lower(root.get("companyName")), pattern, LIKE_ESCAPE_CHAR),
                        cb.like(cb.lower(root.get("location")), pattern, LIKE_ESCAPE_CHAR),
                        cb.like(cb.lower(root.get("city")), pattern, LIKE_ESCAPE_CHAR),
                        cb.like(cb.lower(root.get("country")), pattern, LIKE_ESCAPE_CHAR),
                        cb.like(cb.lower(root.get("descriptionSnippet")), pattern, LIKE_ESCAPE_CHAR),
                        cb.like(cb.lower(asStringExpression(root.get("description"))), pattern, LIKE_ESCAPE_CHAR)
                ));
            }
        }

        if (hasText(criteria.country())) {
            String rawLower = criteria.country().trim().toLowerCase(Locale.ROOT);
            String canonicalLower = hasText(normalizedCountry)
                    ? normalizedCountry.trim().toLowerCase(Locale.ROOT)
                    : rawLower;
            if (canonicalLower.equals(rawLower)) {
                predicates.add(cb.equal(cb.lower(root.get("country")), canonicalLower));
            } else {
                predicates.add(cb.or(
                        cb.equal(cb.lower(root.get("country")), canonicalLower),
                        cb.equal(cb.lower(root.get("country")), rawLower)
                ));
            }
        }

        if (hasText(criteria.city())) {
            String cityPattern = "%" + escapeLike(criteria.city().trim().toLowerCase(Locale.ROOT)) + "%";
            predicates.add(cb.or(
                    cb.like(cb.lower(root.get("city")), cityPattern, LIKE_ESCAPE_CHAR),
                    cb.like(cb.lower(root.get("location")), cityPattern, LIKE_ESCAPE_CHAR)
            ));
        }

        if (criteria.workArrangement() != null && criteria.workArrangement() != WorkArrangement.ANY) {
            predicates.add(cb.equal(root.get("workArrangement"), criteria.workArrangement()));
        }

        if (criteria.employmentType() != null) {
            predicates.add(cb.equal(root.get("employmentType"), criteria.employmentType()));
        }

        if (criteria.experienceLevel() != null) {
            predicates.add(cb.equal(root.get("experienceLevel"), criteria.experienceLevel()));
        }

        if (criteria.sponsorStatus() != null) {
            predicates.add(cb.equal(root.get("sponsorStatus"), criteria.sponsorStatus()));
        }

        if (criteria.minSalary() != null && criteria.minSalary().compareTo(BigDecimal.ZERO) > 0) {
            Predicate salaryMaxMeets = cb.and(
                    cb.isNotNull(root.get("salaryMax")),
                    cb.greaterThanOrEqualTo(root.get("salaryMax"), criteria.minSalary())
            );
            Predicate salaryMinFallbackMeets = cb.and(
                    cb.isNull(root.get("salaryMax")),
                    cb.isNotNull(root.get("salaryMin")),
                    cb.greaterThanOrEqualTo(root.get("salaryMin"), criteria.minSalary())
            );
            predicates.add(cb.or(salaryMaxMeets, salaryMinFallbackMeets));
        }

        if (userId != null && query != null) {
            if (!criteria.includeHidden()) {
                Subquery<Long> hiddenSubquery = query.subquery(Long.class);
                Root<UserJobInteraction> hiddenRoot = hiddenSubquery.from(UserJobInteraction.class);
                hiddenSubquery.select(hiddenRoot.get("id"))
                        .where(
                                cb.equal(hiddenRoot.get("job").get("id"), root.get("id")),
                                cb.equal(hiddenRoot.get("user").get("id"), userId),
                                cb.isTrue(hiddenRoot.get("hidden"))
                        );
                predicates.add(cb.not(cb.exists(hiddenSubquery)));
            }

            if (criteria.savedOnly()) {
                Subquery<Long> savedSubquery = query.subquery(Long.class);
                Root<UserJobInteraction> savedRoot = savedSubquery.from(UserJobInteraction.class);
                savedSubquery.select(savedRoot.get("id"))
                        .where(
                                cb.equal(savedRoot.get("job").get("id"), root.get("id")),
                                cb.equal(savedRoot.get("user").get("id"), userId),
                                cb.isTrue(savedRoot.get("saved"))
                        );
                predicates.add(cb.exists(savedSubquery));
            }

            if (criteria.interestedOnly()) {
                Subquery<Long> interestedSubquery = query.subquery(Long.class);
                Root<UserJobInteraction> interestedRoot = interestedSubquery.from(UserJobInteraction.class);
                interestedSubquery.select(interestedRoot.get("id"))
                        .where(
                                cb.equal(interestedRoot.get("job").get("id"), root.get("id")),
                                cb.equal(interestedRoot.get("user").get("id"), userId),
                                cb.isTrue(interestedRoot.get("interested"))
                        );
                predicates.add(cb.exists(interestedSubquery));
            }

            if (criteria.minMatchScore() != null && criteria.minMatchScore() > 0) {
                Subquery<Long> matchSubquery = query.subquery(Long.class);
                Root<JobMatchScore> matchRoot = matchSubquery.from(JobMatchScore.class);
                matchSubquery.select(matchRoot.get("id"))
                        .where(
                                cb.equal(matchRoot.get("job").get("id"), root.get("id")),
                                cb.equal(matchRoot.get("user").get("id"), userId),
                                cb.greaterThanOrEqualTo(
                                        matchRoot.get("finalScore"),
                                        BigDecimal.valueOf(criteria.minMatchScore())
                                )
                        );
                predicates.add(cb.exists(matchSubquery));
            }
        }

        return cb.and(predicates.toArray(Predicate[]::new));
    }

    static Set<String> extractSearchTokens(String rawQuery) {
        Set<String> tokens = new LinkedHashSet<>();
        if (!hasText(rawQuery)) {
            return tokens;
        }
        String[] parts = rawQuery.trim().split("\\s+");
        for (String part : parts) {
            if (!part.isBlank()) {
                tokens.add(part.toLowerCase(Locale.ROOT));
                if (tokens.size() >= MAX_QUERY_TOKENS) {
                    break;
                }
            }
        }
        return tokens;
    }

    static String escapeLike(String raw) {
        return raw
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }

    private static Expression<String> asStringExpression(Expression<String> expression) {
        if (expression instanceof JpaExpression<?> jpaExpression) {
            return jpaExpression.cast(String.class);
        }
        return expression;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}

