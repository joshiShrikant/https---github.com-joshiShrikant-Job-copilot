package com.eurojobcopilot.modules.recruiter.repository;

import com.eurojobcopilot.modules.recruiter.entity.Recruiter;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface RecruiterRepository extends JpaRepository<Recruiter, Long> {

    Page<Recruiter> findAllByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    Optional<Recruiter> findByIdAndUserId(Long id, Long userId);

    @Query("""
            SELECT r FROM Recruiter r
            LEFT JOIN FETCH r.company c
            WHERE r.id = :id AND r.user.id = :userId
            """)
    Optional<Recruiter> findWithCompanyByIdAndUserId(@Param("id") Long id, @Param("userId") Long userId);

    @Query(
            value = """
                    SELECT r FROM Recruiter r
                    LEFT JOIN FETCH r.company c
                    WHERE r.user.id = :userId
                      AND (:companyId IS NULL OR c.id = :companyId)
                      AND (:query IS NULL OR :query = ''
                           OR LOWER(r.fullName) LIKE LOWER(CONCAT('%', :query, '%'))
                           OR LOWER(COALESCE(r.roleTitle, '')) LIKE LOWER(CONCAT('%', :query, '%'))
                           OR LOWER(COALESCE(r.companyName, '')) LIKE LOWER(CONCAT('%', :query, '%'))
                           OR LOWER(COALESCE(r.email, '')) LIKE LOWER(CONCAT('%', :query, '%')))
                    ORDER BY r.updatedAt DESC, r.id DESC
                    """,
            countQuery = """
                    SELECT COUNT(r) FROM Recruiter r
                    LEFT JOIN r.company c
                    WHERE r.user.id = :userId
                      AND (:companyId IS NULL OR c.id = :companyId)
                      AND (:query IS NULL OR :query = ''
                           OR LOWER(r.fullName) LIKE LOWER(CONCAT('%', :query, '%'))
                           OR LOWER(COALESCE(r.roleTitle, '')) LIKE LOWER(CONCAT('%', :query, '%'))
                           OR LOWER(COALESCE(r.companyName, '')) LIKE LOWER(CONCAT('%', :query, '%'))
                           OR LOWER(COALESCE(r.email, '')) LIKE LOWER(CONCAT('%', :query, '%')))
                    """
    )
    Page<Recruiter> searchByUserId(
            @Param("userId") Long userId,
            @Param("companyId") Long companyId,
            @Param("query") String query,
            Pageable pageable
    );
}
