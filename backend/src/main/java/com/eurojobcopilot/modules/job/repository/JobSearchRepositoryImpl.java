package com.eurojobcopilot.modules.job.repository;

import com.eurojobcopilot.modules.job.dto.JobSearchCriteria;
import com.eurojobcopilot.modules.job.dto.JobSortOption;
import com.eurojobcopilot.modules.job.entity.Job;
import com.eurojobcopilot.modules.job.entity.UserJobInteraction;
import com.eurojobcopilot.modules.matching.entity.JobMatchScore;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Repository
public class JobSearchRepositoryImpl implements JobSearchRepository {

    private static final BigDecimal UNSCORED_SORT_SENTINEL = new BigDecimal("-1.00");

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public Page<Job> searchActiveJobs(
            Long userId,
            JobSearchCriteria criteria,
            String normalizedCountry,
            Pageable pageable,
            JobSortOption sortOption,
            Instant now
    ) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();

        CriteriaQuery<Long> countQuery = cb.createQuery(Long.class);
        Root<Job> countRoot = countQuery.from(Job.class);
        countQuery.select(cb.count(countRoot));
        countQuery.where(JobSpecifications.buildPredicate(
                countRoot, countQuery, cb, userId, criteria, normalizedCountry, now
        ));

        long totalElements = entityManager.createQuery(countQuery).getSingleResult();
        if (totalElements == 0 || pageable.getOffset() >= totalElements) {
            return new PageImpl<>(List.of(), pageable, totalElements);
        }

        CriteriaQuery<Job> dataQuery = cb.createQuery(Job.class);
        Root<Job> jobRoot = dataQuery.from(Job.class);
        dataQuery.select(jobRoot);
        dataQuery.where(JobSpecifications.buildPredicate(
                jobRoot, dataQuery, cb, userId, criteria, normalizedCountry, now
        ));
        dataQuery.orderBy(buildOrders(cb, dataQuery, jobRoot, userId, sortOption));

        TypedQuery<Job> typedQuery = entityManager.createQuery(dataQuery);
        typedQuery.setFirstResult((int) pageable.getOffset());
        typedQuery.setMaxResults(pageable.getPageSize());

        List<Job> content = typedQuery.getResultList();
        return new PageImpl<>(content, pageable, totalElements);
    }

    @Override
    public Page<Job> searchCompanyActiveJobs(
            Long userId,
            Long companyId,
            String companyName,
            boolean includeHidden,
            Pageable pageable,
            JobSortOption sortOption,
            Instant now
    ) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();

        CriteriaQuery<Long> countQuery = cb.createQuery(Long.class);
        Root<Job> countRoot = countQuery.from(Job.class);
        countQuery.select(cb.count(countRoot));
        countQuery.where(buildCompanyJobsPredicate(
                countRoot, countQuery, cb, userId, companyId, companyName, includeHidden, now
        ));

        long totalElements = entityManager.createQuery(countQuery).getSingleResult();
        if (totalElements == 0 || pageable.getOffset() >= totalElements) {
            return new PageImpl<>(List.of(), pageable, totalElements);
        }

        CriteriaQuery<Job> dataQuery = cb.createQuery(Job.class);
        Root<Job> jobRoot = dataQuery.from(Job.class);
        dataQuery.select(jobRoot);
        dataQuery.where(buildCompanyJobsPredicate(
                jobRoot, dataQuery, cb, userId, companyId, companyName, includeHidden, now
        ));
        dataQuery.orderBy(buildOrders(cb, dataQuery, jobRoot, userId, sortOption));

        TypedQuery<Job> typedQuery = entityManager.createQuery(dataQuery);
        typedQuery.setFirstResult((int) pageable.getOffset());
        typedQuery.setMaxResults(pageable.getPageSize());

        return new PageImpl<>(typedQuery.getResultList(), pageable, totalElements);
    }

    private Predicate buildCompanyJobsPredicate(
            Root<Job> root,
            CriteriaQuery<?> query,
            CriteriaBuilder cb,
            Long userId,
            Long companyId,
            String companyName,
            boolean includeHidden,
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

        Predicate linkedMatch = cb.equal(root.get("company").get("id"), companyId);
        if (companyName != null && !companyName.isBlank()) {
            Predicate unlinkedExactMatch = cb.and(
                    cb.isNull(root.get("company")),
                    cb.equal(cb.lower(root.get("companyName")), companyName.trim().toLowerCase(Locale.ROOT))
            );
            predicates.add(cb.or(linkedMatch, unlinkedExactMatch));
        } else {
            predicates.add(linkedMatch);
        }

        if (userId != null && !includeHidden) {
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

        return cb.and(predicates.toArray(Predicate[]::new));
    }

    private List<Order> buildOrders(
            CriteriaBuilder cb,
            CriteriaQuery<Job> dataQuery,
            Root<Job> jobRoot,
            Long userId,
            JobSortOption sortOption
    ) {
        List<Order> orders = new ArrayList<>();
        JobSortOption effectiveSort = sortOption != null ? sortOption : JobSortOption.POSTED_AT_DESC;

        switch (effectiveSort) {
            case POSTED_AT_ASC -> {
                orders.add(cb.asc(jobRoot.get("postedAt")));
                orders.add(cb.asc(jobRoot.get("id")));
            }
            case SALARY_MAX_DESC -> {
                orders.add(cb.desc(cb.coalesce(jobRoot.get("salaryMax"), UNSCORED_SORT_SENTINEL)));
                orders.add(cb.desc(jobRoot.get("postedAt")));
                orders.add(cb.desc(jobRoot.get("id")));
            }
            case MATCH_SCORE_DESC -> {
                if (userId != null) {
                    Subquery<BigDecimal> scoreSubquery = dataQuery.subquery(BigDecimal.class);
                    Root<JobMatchScore> matchRoot = scoreSubquery.from(JobMatchScore.class);
                    scoreSubquery.select(matchRoot.get("finalScore"))
                            .where(
                                    cb.equal(matchRoot.get("job").get("id"), jobRoot.get("id")),
                                    cb.equal(matchRoot.get("user").get("id"), userId)
                            );
                    orders.add(cb.desc(cb.coalesce(scoreSubquery, UNSCORED_SORT_SENTINEL)));
                }
                orders.add(cb.desc(jobRoot.get("postedAt")));
                orders.add(cb.desc(jobRoot.get("id")));
            }
            case POSTED_AT_DESC -> {
                orders.add(cb.desc(jobRoot.get("postedAt")));
                orders.add(cb.desc(jobRoot.get("id")));
            }
        }
        return orders;
    }
}

