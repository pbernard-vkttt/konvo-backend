package com.vulkantechtt.konvo.security;

import com.vulkantechtt.konvo.users.Role;
import java.util.UUID;

/**
 * The authenticated user inside a single request. Built by JwtAuthenticationFilter
 * from claims on the access token. Domain code can read this via
 * {@code SecurityContextHolder.getContext().getAuthentication().getPrincipal()}
 * but should normally accept it as a method parameter using
 * {@code @AuthenticationPrincipal KonvoPrincipal principal}.
 */
public record KonvoPrincipal(
        UUID userId,
        String email,
        String fullName,
        UUID tenantId,
        Role role) {
}
