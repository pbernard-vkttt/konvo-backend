package com.vulkantechtt.konvo.ai;

import java.util.UUID;

/** Envelope on {@code konvo.ai.reply}. */
public record AiReplyCommand(
        UUID tenantId,
        UUID conversationId,
        UUID channelId,
        UUID customerId,
        UUID inboundMessageId,
        String inboundBody) {}
