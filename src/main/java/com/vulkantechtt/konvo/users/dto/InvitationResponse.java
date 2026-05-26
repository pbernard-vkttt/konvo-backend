package com.vulkantechtt.konvo.users.dto;

import com.vulkantechtt.konvo.users.Role;
import java.time.Instant;
import java.util.UUID;

public record InvitationResponse(
        UUID id,
        String email,
        Role role,
        Instant expiresAt,
        Instant createdAt,
        String devToken) {
}
