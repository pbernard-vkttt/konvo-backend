package com.vulkantechtt.konvo.templates.dto;

import com.vulkantechtt.konvo.templates.TemplateCategory;
import com.vulkantechtt.konvo.templates.TemplateStatus;
import java.time.Instant;
import java.util.UUID;

public record TemplateResponse(
        UUID id,
        String name,
        String language,
        TemplateCategory category,
        TemplateStatus status,
        String components,
        Instant updatedAt) {
}
