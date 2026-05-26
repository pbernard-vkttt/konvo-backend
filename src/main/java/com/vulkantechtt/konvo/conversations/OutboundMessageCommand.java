package com.vulkantechtt.konvo.conversations;

import java.util.UUID;

/**
 * Drained from {@code konvo.whatsapp.outbound}. Created by services that want
 * to send a message — typically the AI reply pipeline (M5) or an agent reply
 * from the inbox (M4). M3 ships the queue + listener so those callers can
 * plug in with one publish call.
 */
public record OutboundMessageCommand(
        UUID tenantId,
        UUID conversationId,
        UUID channelId,
        UUID customerId,
        String toPhoneE164,
        String body) {}
