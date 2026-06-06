package com.vulkantechtt.konvo.scheduling.dto;

import com.vulkantechtt.konvo.scheduling.BookingMode;
import java.time.Instant;
import java.util.List;

/**
 * Slots available for booking. When {@code bookingMode} is not GOOGLE the slot
 * list is empty (LINK → share the Calendly link instead; DISABLED → no booking).
 */
public record AvailabilityResponse(
        BookingMode bookingMode,
        int durationMinutes,
        String timezone,
        String calendlyUrl,
        List<Instant> slots) {
}
