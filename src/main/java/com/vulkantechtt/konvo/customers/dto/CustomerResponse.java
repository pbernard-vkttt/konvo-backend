package com.vulkantechtt.konvo.customers.dto;

import java.time.Instant;
import java.util.UUID;

public record CustomerResponse(
        UUID id,
        String displayName,
        String profileName,
        String phone,
        String locale,
        Instant createdAt,
        Instant updatedAt) {
}
