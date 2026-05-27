package com.vulkantechtt.konvo.audit;

import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {

    Page<AuditLog> findByTenantIdOrderByCreatedAtDesc(UUID tenantId, Pageable pageable);

    @Query("""
            select a from AuditLog a
            where a.tenantId = :tenantId
              and (:action is null or a.action = :action)
              and (:entityType is null or a.entityType = :entityType)
            order by a.createdAt desc
            """)
    Page<AuditLog> search(@Param("tenantId") UUID tenantId,
                          @Param("action") String action,
                          @Param("entityType") String entityType,
                          Pageable pageable);
}
