package com.vulkantechtt.konvo.audit.dto;

import java.time.Instant;
import java.util.UUID;

public record AuditEntry(
        UUID id,
        UUID actorUserId,
        String actorEmail,
        String action,
        String entityType,
        UUID entityId,
        String summary,
        String diff,
        Instant createdAt) {
}
