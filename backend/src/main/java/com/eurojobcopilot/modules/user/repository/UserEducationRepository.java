package com.eurojobcopilot.modules.user.repository;

import com.eurojobcopilot.modules.user.entity.UserEducation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserEducationRepository extends JpaRepository<UserEducation, Long> {

    List<UserEducation> findAllByUserIdOrderByStartDateDesc(Long userId);

    Optional<UserEducation> findByIdAndUserId(Long id, Long userId);

    long countByUserId(Long userId);
}

