package com.vulkantechtt.konvo.conversations.dto;

import com.vulkantechtt.konvo.conversations.ConversationStatus;
import java.time.Instant;
import java.util.UUID;

/** Detail returned for the inbox thread header / right pane. */
public record ConversationDetail(
        UUID id,
        UUID channelId,
        String channelDisplayName,
        UUID customerId,
        String customerName,
        String customerPhone,
        ConversationStatus status,
        UUID assignedUserId,
        Instant lastMessageAt,
        Instant createdAt) {
}
