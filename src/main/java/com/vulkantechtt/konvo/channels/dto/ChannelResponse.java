package com.vulkantechtt.konvo.channels.dto;

import com.vulkantechtt.konvo.channels.ChannelProvider;
import com.vulkantechtt.konvo.channels.ChannelStatus;
import java.time.Instant;
import java.util.UUID;

/**
 * Sent to the Settings → Channels page. Deliberately does not expose
 * {@code accessToken} or {@code appSecret} — those are write-only fields the
 * user types in the form and we store; the UI never reads them back.
 */
public record ChannelResponse(
        UUID id,
        ChannelProvider provider,
        String displayName,
        ChannelStatus status,
        String phoneNumber,
        String phoneNumberId,
        String wabaId,
        String webhookUrl,
        String webhookVerifyToken,
        Instant createdAt) {
}
