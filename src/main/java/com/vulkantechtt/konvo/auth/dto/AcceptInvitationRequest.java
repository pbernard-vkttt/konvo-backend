package com.vulkantechtt.konvo.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AcceptInvitationRequest(
        @NotBlank String token,
        @NotBlank @Size(max = 160) String fullName,
        @NotBlank @Size(min = 10, max = 100) String password) {
}
