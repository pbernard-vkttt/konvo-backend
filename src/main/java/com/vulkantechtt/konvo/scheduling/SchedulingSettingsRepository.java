package com.vulkantechtt.konvo.scheduling;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SchedulingSettingsRepository extends JpaRepository<SchedulingSettings, UUID> {

    Optional<SchedulingSettings> findByTenantId(UUID tenantId);
}
