package com.vulkantechtt.konvo.scheduling.dto;

import com.vulkantechtt.konvo.scheduling.BookingMode;

/**
 * Sent to the Settings → Scheduling page. Deliberately omits the Google tokens —
 * those are write-only secrets the OAuth flow stores; the UI only needs to know
 * whether a calendar is connected and which account. {@code bookingMode} is the
 * derived fallback chain so the frontend (and inbox) can show the right state.
 */
public record SchedulingSettingsResponse(
        BookingMode bookingMode,
        boolean googleConnected,
        String googleAccountEmail,
        String googleCalendarId,
        String calendlyUrl,
        int meetingDurationMinutes,
        String timezone,
        int bookingWindowDays,
        int workDayStartHour,
        int workDayEndHour,
        String workDays) {
}
