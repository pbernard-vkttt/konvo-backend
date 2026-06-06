package com.vulkantechtt.konvo.scheduling.dto;

import com.vulkantechtt.konvo.scheduling.AppointmentSource;
import com.vulkantechtt.konvo.scheduling.AppointmentStatus;
import java.time.Instant;
import java.util.UUID;

public record AppointmentResponse(
        UUID id,
        UUID customerId,
        String customerName,
        UUID conversationId,
        String title,
        String notes,
        Instant startsAt,
        Instant endsAt,
        AppointmentStatus status,
        AppointmentSource source,
        boolean onGoogleCalendar,
        Instant createdAt) {
}
