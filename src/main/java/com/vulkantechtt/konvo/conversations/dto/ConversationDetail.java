package com.vulkantechtt.konvo.conversations.dto;

import com.vulkantechtt.konvo.conversations.ConversationStatus;
import java.time.Instant;
import java.util.UUID;

/** Detail returned for the inbox thread header / right pane. */
public record ConversationDetail(
        UUID id,
        UUID channelId,
        String channelDisplayName,
        String channelProvider,
        UUID customerId,
        String customerName,
        String customerPhone,
        ConversationStatus status,
        UUID assignedUserId,
        boolean autoReplyEnabled,
        Instant lastMessageAt,
        // Timestamp of the customer's most recent inbound message; the WhatsApp
        // 24h free-form reply window is measured from here. Null if none yet.
        Instant lastInboundAt,
        Instant createdAt) {
}
