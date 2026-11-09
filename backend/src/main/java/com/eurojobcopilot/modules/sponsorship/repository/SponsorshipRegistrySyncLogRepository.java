package com.eurojobcopilot.modules.sponsorship.repository;

import com.eurojobcopilot.modules.sponsorship.entity.SponsorshipRegistrySyncLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SponsorshipRegistrySyncLogRepository extends JpaRepository<SponsorshipRegistrySyncLog, Long> {

    Optional<SponsorshipRegistrySyncLog> findTopByRegistrySourceOrderBySyncedAtDescIdDesc(String registrySource);
}

