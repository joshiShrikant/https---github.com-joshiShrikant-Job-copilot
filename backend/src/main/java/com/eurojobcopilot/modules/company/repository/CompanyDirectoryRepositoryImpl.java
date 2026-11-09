package com.eurojobcopilot.modules.company.repository;

import com.eurojobcopilot.modules.company.dto.CompanySearchCriteria;
import com.eurojobcopilot.modules.company.dto.CompanySortOption;
import com.eurojobcopilot.modules.company.entity.UserSavedCompany;
import com.eurojobcopilot.modules.job.entity.Job;
import com.eurojobcopilot.modules.sponsorship.entity.Company;
import com.eurojobcopilot.modules.sponsorship.entity.CompanySponsorshipRecord;
import com.eurojobcopilot.modules.sponsorship.entity.SponsorshipRecordStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.hibernate.query.criteria.JpaExpression;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Repository
public class CompanyDirectoryRepositoryImpl implements CompanyDirectoryRepository {

    private static final int MAX_QUERY_TOKENS = 5;
    private static final char LIKE_ESCAPE_CHAR = '\\';
    private static final Instant UNSAVED_SORT_SENTINEL = Instant.EPOCH;

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public Page<Company> searchCompanies(
            Long userId,
            CompanySearchCriteria criteria,
            String normalizedCountry,
            Pageable pageable,
            CompanySortOption sortOption,
            Instant now
    ) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();

        CriteriaQuery<Long> countQuery = cb.createQuery(Long.class);
        Root<Company> countRoot = countQuery.from(Company.class);
        countQuery.select(cb.count(countRoot));
        countQuery.where(buildPredicate(countRoot, countQuery, cb, userId, criteria, normalizedCountry, now));

        long totalElements = entityManager.createQuery(countQuery).getSingleResult();
        if (totalElements == 0 || pageable.getOffset() >= totalElements) {
            return new PageImpl<>(List.of(), pageable, totalElements);
        }

        CriteriaQuery<Company> dataQuery = cb.createQuery(Company.class);
        Root<Company> companyRoot = dataQuery.from(Company.class);
        dataQuery.select(companyRoot);
        dataQuery.where(buildPredicate(companyRoot, dataQuery, cb, userId, criteria, normalizedCountry, now));
        dataQuery.orderBy(buildOrders(cb, dataQuery, companyRoot, userId, sortOption, now));

        TypedQuery<Company> typedQuery = entityManager.createQuery(dataQuery);
        typedQuery.setFirstResult((int) pageable.getOffset());
        typedQuery.setMaxResults(pageable.getPageSize());

        return new PageImpl<>(typedQuery.getResultList(), pageable, totalElements);
    }

    @Override
    public long countVerifiedSponsorCompanies() {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Long> query = cb.createQuery(Long.class);
        Root<Company> root = query.from(Company.class);
        query.select(cb.count(root));
        query.where(buildVerifiedSponsorPredicate(root, query, cb));
        return entityManager.createQuery(query).getSingleResult();
    }

    private Predicate buildPredicate(
            Root<Company> root,
            CriteriaQuery<?> query,
            CriteriaBuilder cb,
            Long userId,
            CompanySearchCriteria criteria,
            String normalizedCountry,
            Instant now
    ) {
        List<Predicate> predicates = new ArrayList<>();
        if (criteria == null) {
            return cb.conjunction();
        }

        if (hasText(criteria.query())) {
            for (String token : extractSearchTokens(criteria.query())) {
                String pattern = "%" + escapeLike(token) + "%";

                Subquery<Long> recordQuery = query.subquery(Long.class);
                Root<CompanySponsorshipRecord> recRoot = recordQuery.from(CompanySponsorshipRecord.class);
                recordQuery.select(recRoot.get("id"))
                        .where(
                                cb.equal(recRoot.get("company").get("id"), root.get("id")),
                                cb.or(
                                        cb.like(cb.lower(recRoot.get("officialRegisteredName")), pattern, LIKE_ESCAPE_CHAR),
                                        cb.like(cb.lower(recRoot.get("registryIdentifier")), pattern, LIKE_ESCAPE_CHAR),
                                        cb.like(cb.lower(recRoot.get("country")), pattern, LIKE_ESCAPE_CHAR)
                                )
                        );

                Subquery<Long> jobQuery = query.subquery(Long.class);
                Root<Job> jobRoot = jobQuery.from(Job.class);
                jobQuery.select(jobRoot.get("id"))
                        .where(
                                activeNonExpiredJobPredicate(cb, jobRoot, now),
                                jobBelongsToCompanyPredicate(cb, jobRoot, root),
                                cb.or(
                                        cb.like(cb.lower(jobRoot.get("title")), pattern, LIKE_ESCAPE_CHAR),
                                        cb.like(cb.lower(jobRoot.get("city")), pattern, LIKE_ESCAPE_CHAR),
                                        cb.like(cb.lower(jobRoot.get("country")), pattern, LIKE_ESCAPE_CHAR)
                                )
                        );

                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("name")), pattern, LIKE_ESCAPE_CHAR),
                        cb.like(cb.lower(root.get("normalizedName")), pattern, LIKE_ESCAPE_CHAR),
                        cb.like(cb.lower(root.get("headquartersCountry")), pattern, LIKE_ESCAPE_CHAR),
                        cb.like(cb.lower(root.get("headquartersCity")), pattern, LIKE_ESCAPE_CHAR),
                        cb.like(cb.lower(root.get("industry")), pattern, LIKE_ESCAPE_CHAR),
                        cb.like(cb.lower(asStringExpression(root.get("description"))), pattern, LIKE_ESCAPE_CHAR),
                        cb.exists(recordQuery),
                        cb.exists(jobQuery)
                ));
            }
        }

        if (hasText(criteria.country())) {
            String rawLower = criteria.country().trim().toLowerCase(Locale.ROOT);
            String canonicalLower = hasText(normalizedCountry)
                    ? normalizedCountry.trim().toLowerCase(Locale.ROOT)
                    : rawLower;

            Predicate hqCountryMatch = canonicalLower.equals(rawLower)
                    ? cb.equal(cb.lower(root.get("headquartersCountry")), canonicalLower)
                    : cb.or(
                            cb.equal(cb.lower(root.get("headquartersCountry")), canonicalLower),
                            cb.equal(cb.lower(root.get("headquartersCountry")), rawLower)
                    );

            Subquery<Long> jobCountrySubquery = query.subquery(Long.class);
            Root<Job> jobRoot = jobCountrySubquery.from(Job.class);
            Predicate jobCountryPredicate = canonicalLower.equals(rawLower)
                    ? cb.equal(cb.lower(jobRoot.get("country")), canonicalLower)
                    : cb.or(
                            cb.equal(cb.lower(jobRoot.get("country")), canonicalLower),
                            cb.equal(cb.lower(jobRoot.get("country")), rawLower)
                    );
            jobCountrySubquery.select(jobRoot.get("id"))
                    .where(
                            activeNonExpiredJobPredicate(cb, jobRoot, now),
                            jobBelongsToCompanyPredicate(cb, jobRoot, root),
                            jobCountryPredicate
                    );

            Subquery<Long> recordCountrySubquery = query.subquery(Long.class);
            Root<CompanySponsorshipRecord> recRoot = recordCountrySubquery.from(CompanySponsorshipRecord.class);
            Predicate recordCountryPredicate = canonicalLower.equals(rawLower)
                    ? cb.equal(cb.lower(recRoot.get("country")), canonicalLower)
                    : cb.or(
                            cb.equal(cb.lower(recRoot.get("country")), canonicalLower),
                            cb.equal(cb.lower(recRoot.get("country")), rawLower)
                    );
            recordCountrySubquery.select(recRoot.get("id"))
                    .where(
                            cb.equal(recRoot.get("company").get("id"), root.get("id")),
                            recordCountryPredicate
                    );

            predicates.add(cb.or(hqCountryMatch, cb.exists(jobCountrySubquery), cb.exists(recordCountrySubquery)));
        }

        if (hasText(criteria.industry())) {
            String industryPattern = "%" + escapeLike(criteria.industry().trim().toLowerCase(Locale.ROOT)) + "%";
            predicates.add(cb.like(cb.lower(root.get("industry")), industryPattern, LIKE_ESCAPE_CHAR));
        }

        if (hasText(criteria.tech())) {
            String techPattern = "%" + escapeLike(criteria.tech().trim().toLowerCase(Locale.ROOT)) + "%";

            Subquery<Long> jobTechSubquery = query.subquery(Long.class);
            Root<Job> jobRoot = jobTechSubquery.from(Job.class);
            jobTechSubquery.select(jobRoot.get("id"))
                    .where(
                            activeNonExpiredJobPredicate(cb, jobRoot, now),
                            jobBelongsToCompanyPredicate(cb, jobRoot, root),
                            cb.or(
                                    cb.like(cb.lower(asStringExpression(jobRoot.get("skills"))), techPattern, LIKE_ESCAPE_CHAR),
                                    cb.like(cb.lower(jobRoot.get("title")), techPattern, LIKE_ESCAPE_CHAR),
                                    cb.like(cb.lower(asStringExpression(jobRoot.get("description"))), techPattern, LIKE_ESCAPE_CHAR)
                            )
                    );

            predicates.add(cb.or(
                    cb.like(cb.lower(asStringExpression(root.get("techStack"))), techPattern, LIKE_ESCAPE_CHAR),
                    cb.exists(jobTechSubquery)
            ));
        }

        if (criteria.verifiedSponsorOnly()) {
            predicates.add(buildVerifiedSponsorPredicate(root, query, cb));
        }

        if (criteria.withOpenJobsOnly()) {
            Subquery<Long> openJobsSubquery = query.subquery(Long.class);
            Root<Job> jobRoot = openJobsSubquery.from(Job.class);
            openJobsSubquery.select(jobRoot.get("id"))
                    .where(
                            activeNonExpiredJobPredicate(cb, jobRoot, now),
                            jobBelongsToCompanyPredicate(cb, jobRoot, root)
                    );
            predicates.add(cb.exists(openJobsSubquery));
        }

        if (criteria.savedOnly()) {
            if (userId == null) {
                predicates.add(cb.disjunction());
            } else {
                Subquery<Long> savedSubquery = query.subquery(Long.class);
                Root<UserSavedCompany> savedRoot = savedSubquery.from(UserSavedCompany.class);
                savedSubquery.select(savedRoot.get("id"))
                        .where(
                                cb.equal(savedRoot.get("company").get("id"), root.get("id")),
                                cb.equal(savedRoot.get("user").get("id"), userId)
                        );
                predicates.add(cb.exists(savedSubquery));
            }
        }

        return cb.and(predicates.toArray(Predicate[]::new));
    }

    private Predicate buildVerifiedSponsorPredicate(
            Root<Company> root,
            CriteriaQuery<?> query,
            CriteriaBuilder cb
    ) {
        Subquery<Long> recordSubquery = query.subquery(Long.class);
        Root<CompanySponsorshipRecord> recRoot = recordSubquery.from(CompanySponsorshipRecord.class);
        recordSubquery.select(recRoot.get("id"))
                .where(
                        cb.equal(recRoot.get("company").get("id"), root.get("id")),
                        recRoot.get("status").in(
                                SponsorshipRecordStatus.RECOGNIZED_SPONSOR,
                                SponsorshipRecordStatus.VISA_CAPABLE
                        )
                );
        return cb.or(cb.isTrue(root.get("verifiedSponsor")), cb.exists(recordSubquery));
    }

    private List<Order> buildOrders(
            CriteriaBuilder cb,
            CriteriaQuery<Company> dataQuery,
            Root<Company> companyRoot,
            Long userId,
            CompanySortOption sortOption,
            Instant now
    ) {
        List<Order> orders = new ArrayList<>();
        CompanySortOption effectiveSort = sortOption != null ? sortOption : CompanySortOption.ACTIVE_JOBS_DESC;

        switch (effectiveSort) {
            case VERIFIED_DESC -> {
                orders.add(cb.desc(companyRoot.get("verifiedSponsor")));
                orders.add(cb.desc(activeJobsCountSubquery(cb, dataQuery, companyRoot, now)));
                orders.add(cb.asc(cb.lower(companyRoot.get("name"))));
                orders.add(cb.asc(companyRoot.get("id")));
            }
            case NAME_ASC -> {
                orders.add(cb.asc(cb.lower(companyRoot.get("name"))));
                orders.add(cb.asc(companyRoot.get("id")));
            }
            case SAVED_AT_DESC -> {
                if (userId != null) {
                    Subquery<Instant> savedAtSubquery = dataQuery.subquery(Instant.class);
                    Root<UserSavedCompany> savedRoot = savedAtSubquery.from(UserSavedCompany.class);
                    savedAtSubquery.select(savedRoot.get("savedAt"))
                            .where(
                                    cb.equal(savedRoot.get("company").get("id"), companyRoot.get("id")),
                                    cb.equal(savedRoot.get("user").get("id"), userId)
                            );
                    orders.add(cb.desc(cb.coalesce(savedAtSubquery, UNSAVED_SORT_SENTINEL)));
                }
                orders.add(cb.desc(companyRoot.get("verifiedSponsor")));
                orders.add(cb.asc(cb.lower(companyRoot.get("name"))));
                orders.add(cb.asc(companyRoot.get("id")));
            }
            case ACTIVE_JOBS_DESC -> {
                orders.add(cb.desc(activeJobsCountSubquery(cb, dataQuery, companyRoot, now)));
                orders.add(cb.desc(companyRoot.get("verifiedSponsor")));
                orders.add(cb.asc(cb.lower(companyRoot.get("name"))));
                orders.add(cb.asc(companyRoot.get("id")));
            }
        }
        return orders;
    }

    private Subquery<Long> activeJobsCountSubquery(
            CriteriaBuilder cb,
            CriteriaQuery<Company> dataQuery,
            Root<Company> companyRoot,
            Instant now
    ) {
        Subquery<Long> countSubquery = dataQuery.subquery(Long.class);
        Root<Job> jobRoot = countSubquery.from(Job.class);
        countSubquery.select(cb.count(jobRoot.get("id")))
                .where(
                        activeNonExpiredJobPredicate(cb, jobRoot, now),
                        jobBelongsToCompanyPredicate(cb, jobRoot, companyRoot)
                );
        return countSubquery;
    }

    private Predicate activeNonExpiredJobPredicate(CriteriaBuilder cb, Root<Job> jobRoot, Instant now) {
        if (now == null) {
            return cb.isTrue(jobRoot.get("active"));
        }
        return cb.and(
                cb.isTrue(jobRoot.get("active")),
                cb.or(
                        cb.isNull(jobRoot.get("expiresAt")),
                        cb.greaterThanOrEqualTo(jobRoot.get("expiresAt"), now)
                )
        );
    }

    private Predicate jobBelongsToCompanyPredicate(CriteriaBuilder cb, Root<Job> jobRoot, Root<Company> companyRoot) {
        return cb.or(
                cb.equal(jobRoot.get("company").get("id"), companyRoot.get("id")),
                cb.and(
                        cb.isNull(jobRoot.get("company")),
                        cb.equal(cb.lower(jobRoot.get("companyName")), cb.lower(companyRoot.get("name")))
                )
        );
    }

    private Expression<String> asStringExpression(Expression<?> expression) {
        if (expression instanceof JpaExpression<?> jpaExpression) {
            return jpaExpression.cast(String.class);
        }
        @SuppressWarnings("unchecked")
        Expression<String> stringExpr = (Expression<String>) expression;
        return stringExpr;
    }

    private Set<String> extractSearchTokens(String rawQuery) {
        Set<String> tokens = new LinkedHashSet<>();
        if (!hasText(rawQuery)) {
            return tokens;
        }
        for (String part : rawQuery.trim().split("\\s+")) {
            if (!part.isBlank()) {
                tokens.add(part.toLowerCase(Locale.ROOT));
                if (tokens.size() >= MAX_QUERY_TOKENS) {
                    break;
                }
            }
        }
        return tokens;
    }

    private String escapeLike(String raw) {
        return raw
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}

