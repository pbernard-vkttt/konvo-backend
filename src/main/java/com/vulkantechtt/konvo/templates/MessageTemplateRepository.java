package com.vulkantechtt.konvo.templates;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MessageTemplateRepository extends JpaRepository<MessageTemplate, UUID> {

    Page<MessageTemplate> findByTenantIdOrderByNameAsc(UUID tenantId, Pageable pageable);

    Optional<MessageTemplate> findByTenantIdAndNameAndLanguage(UUID tenantId, String name, String language);
}
