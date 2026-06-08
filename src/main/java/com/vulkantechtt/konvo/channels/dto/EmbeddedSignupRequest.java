package com.vulkantechtt.konvo.channels.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Payload from the browser after Meta's WhatsApp Embedded Signup completes.
 * The OAuth {@code code} is exchanged for an access token server-side; the
 * caller may supply a {@code displayName}, otherwise we fall back to Meta's
 * verified business name.
 */
public record EmbeddedSignupRequest(
        @NotBlank String code,
        @NotBlank @Size(max = 64) String phoneNumberId,
        @NotBlank @Size(max = 64) String wabaId,
        @Size(max = 120) String displayName) {
}
