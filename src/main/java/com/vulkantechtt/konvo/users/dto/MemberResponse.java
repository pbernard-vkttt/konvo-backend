package com.vulkantechtt.konvo.users.dto;

import com.vulkantechtt.konvo.users.Role;
import java.time.Instant;
import java.util.UUID;

public record MemberResponse(
        UUID membershipId,
        UUID userId,
        String email,
        String fullName,
        Role role,
        String status,
        Instant joinedAt,
        Instant lastLoginAt) {
}
