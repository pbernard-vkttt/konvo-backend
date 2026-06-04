package com.vulkantechtt.konvo.auth.dto;

import com.vulkantechtt.konvo.users.Role;
import java.util.UUID;

/** Body returned from /login, /refresh, /register-owner, /invitations/accept. */
public record AuthSessionResponse(
        String accessToken,
        int expiresInSeconds,
        UserSummary user,
        TenantSummary tenant) {

    public record UserSummary(UUID id, String email, String fullName) {}

    public record TenantSummary(UUID id, String name, String slug, Role role, boolean onboardingCompleted) {}
}
