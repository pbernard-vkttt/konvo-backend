package com.vulkantechtt.konvo.audit;

import com.vulkantechtt.konvo.audit.dto.AuditEntry;
import com.vulkantechtt.konvo.common.PageResponse;
import com.vulkantechtt.konvo.security.KonvoPrincipal;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/**
 * Append-only record of meaningful tenant actions. Callers fire-and-forget
 * via {@link #record}; the write happens in the caller's transaction so a
 * rolled-back action doesn't leave a misleading log entry.
 */
@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    private final AuditLogRepository repo;
    private final ObjectMapper objectMapper;

    public AuditService(AuditLogRepository repo, ObjectMapper objectMapper) {
        this.repo = repo;
        this.objectMapper = objectMapper;
    }

    /**
     * Record an audited action for the principal's tenant. The actor's email
     * is snapshotted so deleting the user later doesn't erase the trail.
     */
    @Transactional
    public void record(KonvoPrincipal actor, AuditAction action, UUID entityId, String summary, Map<String, ?> diff) {
        recordFor(actor.tenantId(), actor.userId(), actor.email(), action, entityId, summary, diff);
    }

    /**
     * Record for a tenant when there is no principal (system action, e.g. the
     * auto-provisioned subscription for a newly-registered owner).
     */
    @Transactional
    public void recordSystem(UUID tenantId, AuditAction action, UUID entityId, String summary, Map<String, ?> diff) {
        recordFor(tenantId, null, "system", action, entityId, summary, diff);
    }

    private void recordFor(UUID tenantId, UUID actorUserId, String actorEmail,
                           AuditAction action, UUID entityId,
                           String summary, Map<String, ?> diff) {
        AuditLog row = new AuditLog();
        row.setTenantId(tenantId);
        row.setActorUserId(actorUserId);
        row.setActorEmail(actorEmail);
        row.setAction(action.code());
        row.setEntityType(action.entityType());
        row.setEntityId(entityId);
        row.setSummary(summary);
        if (diff != null && !diff.isEmpty()) {
            try {
                row.setDiff(objectMapper.writeValueAsString(diff));
            } catch (Exception e) {
                log.warn("Failed to serialise audit diff for {} — skipping diff payload", action.code(), e);
            }
        }
        repo.save(row);
    }

    @Transactional(readOnly = true)
    public PageResponse<AuditEntry> list(UUID tenantId, String action, String entityType, Pageable pageable) {
        return PageResponse.from(repo.search(tenantId, blankToNull(action), blankToNull(entityType), pageable)
                .map(AuditService::toEntry));
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }

    private static AuditEntry toEntry(AuditLog a) {
        return new AuditEntry(
                a.getId(),
                a.getActorUserId(),
                a.getActorEmail(),
                a.getAction(),
                a.getEntityType(),
                a.getEntityId(),
                a.getSummary(),
                a.getDiff(),
                a.getCreatedAt());
    }
}
