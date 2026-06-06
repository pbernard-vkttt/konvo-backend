package com.vulkantechtt.konvo.scheduling.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

/**
 * Agent manual booking from the inbox. {@code startsAt} must be in the future;
 * duration defaults to the workspace meeting length when omitted; title defaults
 * to a customer-named meeting.
 */
public record CreateAppointmentRequest(
        @NotNull UUID customerId,
        UUID conversationId,
        @NotNull Instant startsAt,
        Integer durationMinutes,
        @Size(max = 200) String title,
        @Size(max = 2000) String notes) {
}
