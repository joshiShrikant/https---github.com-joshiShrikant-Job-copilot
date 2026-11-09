package com.eurojobcopilot.modules.application.repository;

import com.eurojobcopilot.modules.application.entity.ApplicationStatusHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface ApplicationStatusHistoryRepository extends JpaRepository<ApplicationStatusHistory, Long> {

    @Query("SELECT h FROM ApplicationStatusHistory h WHERE h.application.id = :applicationId ORDER BY h.transitionedAt DESC, h.id DESC")
    List<ApplicationStatusHistory> findAllByApplicationIdOrderByTransitionedAtDesc(@Param("applicationId") Long applicationId);

    @Query("SELECT h FROM ApplicationStatusHistory h WHERE h.application.id IN :applicationIds ORDER BY h.transitionedAt DESC, h.id DESC")
    List<ApplicationStatusHistory> findAllByApplicationIdInOrderByTransitionedAtDesc(@Param("applicationIds") Collection<Long> applicationIds);

    @Modifying
    @Query("DELETE FROM ApplicationStatusHistory h WHERE h.application.id = :applicationId")
    void deleteAllByApplicationId(@Param("applicationId") Long applicationId);
}
