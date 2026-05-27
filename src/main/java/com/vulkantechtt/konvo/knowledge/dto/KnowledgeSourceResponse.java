package com.vulkantechtt.konvo.knowledge.dto;

import com.vulkantechtt.konvo.knowledge.KnowledgeSourceStatus;
import com.vulkantechtt.konvo.knowledge.KnowledgeSourceType;
import java.time.Instant;
import java.util.UUID;

public record KnowledgeSourceResponse(
        UUID id,
        String title,
        KnowledgeSourceType type,
        KnowledgeSourceStatus status,
        int charCount,
        int chunkCount,
        Instant createdAt,
        Instant updatedAt) {
}
