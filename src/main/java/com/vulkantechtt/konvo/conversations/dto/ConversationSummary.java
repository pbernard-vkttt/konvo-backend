package com.vulkantechtt.konvo.conversations.dto;

import com.vulkantechtt.konvo.conversations.ConversationStatus;
import java.time.Instant;
import java.util.UUID;

/**
 * Row shape used by the inbox list. Unread tracking is deferred to a later
 * milestone — for M4 the inbox just shows ordered threads and the agent
 * clicks to read.
 */
public record ConversationSummary(
        UUID id,
        UUID channelId,
        UUID customerId,
        String customerName,
        String customerPhone,
        ConversationStatus status,
        UUID assignedUserId,
        Instant lastMessageAt,
        String lastMessagePreview) {
}
