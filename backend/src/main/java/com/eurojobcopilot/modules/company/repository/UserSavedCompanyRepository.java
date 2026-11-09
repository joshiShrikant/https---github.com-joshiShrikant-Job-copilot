package com.eurojobcopilot.modules.company.repository;

import com.eurojobcopilot.modules.company.entity.UserSavedCompany;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface UserSavedCompanyRepository extends JpaRepository<UserSavedCompany, Long> {

    Optional<UserSavedCompany> findByUserIdAndCompanyId(Long userId, Long companyId);

    List<UserSavedCompany> findAllByUserIdAndCompanyIdIn(Long userId, Collection<Long> companyIds);

    long countByUserId(Long userId);
}

