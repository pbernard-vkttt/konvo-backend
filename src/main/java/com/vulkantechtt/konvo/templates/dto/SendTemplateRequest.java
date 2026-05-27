package com.vulkantechtt.konvo.templates.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

/**
 * Send-template body. Either {@code conversationId} (existing thread, channel
 * + customer resolved from it) or {@code channelId}+{@code toPhoneE164}
 * (greenfield outbound — useful for "send template to a fresh number outside
 * the 24h window") must be provided.
 *
 * {@code bodyParameters} maps to the {@code components[type=body].parameters}
 * placeholders in the template (Meta uses positional {{1}}/{{2}}/...).
 */
public record SendTemplateRequest(
        UUID conversationId,
        UUID channelId,
        String toPhoneE164,
        @NotBlank String language,
        @NotNull List<String> bodyParameters) {
}
