package com.vulkantechtt.konvo.conversations.dto;

import com.vulkantechtt.konvo.conversations.MessageDirection;
import com.vulkantechtt.konvo.conversations.MessageStatus;
import java.time.Instant;
import java.util.UUID;

public record MessageResponse(
        UUID id,
        UUID conversationId,
        MessageDirection direction,
        String contentType,
        String body,
        MessageStatus status,
        Instant sentAt,
        Instant deliveredAt,
        Instant readAt,
        String errorCode,
        String errorMessage) {
}
