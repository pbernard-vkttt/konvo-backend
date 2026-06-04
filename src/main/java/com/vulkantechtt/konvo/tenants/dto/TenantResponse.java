package com.vulkantechtt.konvo.tenants.dto;

import java.time.Instant;
import java.util.UUID;

public record TenantResponse(
        UUID id,
        String name,
        String slug,
        String countryCode,
        String plan,
        String status,
        int customerMemoryMessageLimit,
        String workingHours,
        String businessOfferings,
        String customSystemPrompt,
        String industry,
        boolean onboardingCompleted,
        Instant createdAt) {
}
