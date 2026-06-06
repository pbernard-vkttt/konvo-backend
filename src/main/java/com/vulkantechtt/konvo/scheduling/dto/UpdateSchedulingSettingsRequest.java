package com.vulkantechtt.konvo.scheduling.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Updates the non-OAuth scheduling knobs. Google connect/disconnect is handled
 * by the dedicated OAuth endpoints, not here. {@code calendlyUrl} blank/null
 * clears the fallback link. {@code workDays} is a comma-separated subset of
 * MON..SUN.
 */
public record UpdateSchedulingSettingsRequest(
        @Size(max = 500)
        @Pattern(regexp = "^$|^https://([a-zA-Z0-9-]+\\.)?calendly\\.com/.+",
                message = "Must be a https://calendly.com/... link")
        String calendlyUrl,

        @Min(5) @Max(480) int meetingDurationMinutes,

        @Size(max = 64) String timezone,

        @Min(1) @Max(90) int bookingWindowDays,

        @Min(0) @Max(23) int workDayStartHour,

        @Min(1) @Max(24) int workDayEndHour,

        @Pattern(regexp = "^(MON|TUE|WED|THU|FRI|SAT|SUN)(,(MON|TUE|WED|THU|FRI|SAT|SUN))*$",
                message = "Comma-separated weekday codes, e.g. MON,TUE,WED")
        String workDays) {
}
