package com.vulkantechtt.konvo.conversations.dto;

import com.vulkantechtt.konvo.conversations.MessageStatus;
import java.time.Instant;
import java.util.UUID;

public record MessageStatusUpdatedEvent(
        UUID conversationId,
        UUID messageId,
        MessageStatus status,
        Instant deliveredAt,
        Instant readAt,
        String errorCode,
        String errorMessage) {

    public static final String EVENT_NAME = "message_status_updated";
}
