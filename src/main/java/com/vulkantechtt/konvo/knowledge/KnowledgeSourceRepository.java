package com.vulkantechtt.konvo.knowledge;

import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface KnowledgeSourceRepository extends JpaRepository<KnowledgeSource, UUID> {

    Page<KnowledgeSource> findByTenantIdOrderByCreatedAtDesc(UUID tenantId, Pageable pageable);
}
