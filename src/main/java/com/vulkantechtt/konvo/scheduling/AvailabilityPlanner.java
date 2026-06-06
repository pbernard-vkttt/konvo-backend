package com.vulkantechtt.konvo.scheduling;

import com.vulkantechtt.konvo.scheduling.google.GoogleCalendarClient.BusyInterval;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Pure slot math: turns a workspace's working-hours config plus the calendar's
 * busy intervals into a list of bookable slot start instants. No I/O — kept
 * standalone so it can be unit-tested with a fixed clock.
 */
public final class AvailabilityPlanner {

    private AvailabilityPlanner() {}

    public static List<Instant> slots(
            Instant now,
            ZoneId zone,
            Set<DayOfWeek> workDays,
            int startHour,
            int endHour,
            int durationMinutes,
            int windowDays,
            List<BusyInterval> busy,
            int maxSlots) {

        List<Instant> out = new ArrayList<>();
        if (workDays.isEmpty() || durationMinutes <= 0 || endHour <= startHour) {
            return out;
        }
        LocalDate today = now.atZone(zone).toLocalDate();
        for (int d = 0; d <= windowDays && out.size() < maxSlots; d++) {
            LocalDate date = today.plusDays(d);
            if (!workDays.contains(date.getDayOfWeek())) {
                continue;
            }
            ZonedDateTime dayStart = date.atStartOfDay(zone).plusHours(startHour);
            ZonedDateTime dayEnd = date.atStartOfDay(zone).plusHours(endHour);
            ZonedDateTime cursor = dayStart;
            while (out.size() < maxSlots) {
                ZonedDateTime slotEnd = cursor.plusMinutes(durationMinutes);
                if (slotEnd.isAfter(dayEnd)) {
                    break;
                }
                Instant s = cursor.toInstant();
                Instant e = slotEnd.toInstant();
                if (!s.isBefore(now) && !overlapsBusy(s, e, busy)) {
                    out.add(s);
                }
                cursor = slotEnd;
            }
        }
        return out;
    }

    /** Parses "MON,TUE,..." into DayOfWeek set; unknown tokens ignored. */
    public static Set<DayOfWeek> parseWorkDays(String csv) {
        Set<DayOfWeek> days = EnumSet.noneOf(DayOfWeek.class);
        if (csv == null) {
            return days;
        }
        for (String token : csv.split(",")) {
            switch (token.trim().toUpperCase()) {
                case "MON" -> days.add(DayOfWeek.MONDAY);
                case "TUE" -> days.add(DayOfWeek.TUESDAY);
                case "WED" -> days.add(DayOfWeek.WEDNESDAY);
                case "THU" -> days.add(DayOfWeek.THURSDAY);
                case "FRI" -> days.add(DayOfWeek.FRIDAY);
                case "SAT" -> days.add(DayOfWeek.SATURDAY);
                case "SUN" -> days.add(DayOfWeek.SUNDAY);
                default -> { /* ignore */ }
            }
        }
        return days;
    }

    private static boolean overlapsBusy(Instant s, Instant e, List<BusyInterval> busy) {
        for (BusyInterval b : busy) {
            if (s.isBefore(b.end()) && b.start().isBefore(e)) {
                return true;
            }
        }
        return false;
    }
}
