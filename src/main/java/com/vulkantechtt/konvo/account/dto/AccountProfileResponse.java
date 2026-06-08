package com.vulkantechtt.konvo.account.dto;

import java.util.UUID;

public record AccountProfileResponse(
        UUID id,
        String email,
        String fullName,
        boolean emailVerified) {
}
