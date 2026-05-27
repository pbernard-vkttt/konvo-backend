package com.vulkantechtt.konvo.notifications.dto;

import java.time.Instant;
import java.util.UUID;

public record NotificationResponse(
        UUID id,
        String type,
        String title,
        String body,
        String link,
        Instant readAt,
        Instant createdAt) {
}
