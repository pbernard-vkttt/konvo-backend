package com.vulkantechtt.konvo.scheduling;

import static org.assertj.core.api.Assertions.assertThat;

import com.vulkantechtt.konvo.scheduling.google.GoogleCalendarClient.BusyInterval;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.ZoneId;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class AvailabilityPlannerTest {

    private static final ZoneId UTC = ZoneId.of("UTC");
    private static final Set<DayOfWeek> ALL_DAYS = EnumSet.allOf(DayOfWeek.class);

    @Test
    void generatesHourlySlotsWithinWorkingHours() {
        Instant now = Instant.parse("2026-06-08T08:00:00Z");
        List<Instant> slots = AvailabilityPlanner.slots(
                now, UTC, ALL_DAYS, 9, 17, 60, 0, List.of(), 20);
        // 09..16 starts => 8 slots
        assertThat(slots).hasSize(8);
        assertThat(slots.get(0)).isEqualTo(Instant.parse("2026-06-08T09:00:00Z"));
        assertThat(slots).last().isEqualTo(Instant.parse("2026-06-08T16:00:00Z"));
    }

    @Test
    void excludesBusyIntervals() {
        Instant now = Instant.parse("2026-06-08T08:00:00Z");
        List<Instant> slots = AvailabilityPlanner.slots(
                now, UTC, ALL_DAYS, 9, 17, 60, 0,
                List.of(new BusyInterval(
                        Instant.parse("2026-06-08T10:00:00Z"),
                        Instant.parse("2026-06-08T11:00:00Z"))),
                20);
        assertThat(slots).hasSize(7);
        assertThat(slots).doesNotContain(Instant.parse("2026-06-08T10:00:00Z"));
    }

    @Test
    void excludesSlotsInThePast() {
        Instant now = Instant.parse("2026-06-08T13:30:00Z");
        List<Instant> slots = AvailabilityPlanner.slots(
                now, UTC, ALL_DAYS, 9, 17, 60, 0, List.of(), 20);
        // only 14:00, 15:00, 16:00 remain
        assertThat(slots).containsExactly(
                Instant.parse("2026-06-08T14:00:00Z"),
                Instant.parse("2026-06-08T15:00:00Z"),
                Instant.parse("2026-06-08T16:00:00Z"));
    }

    @Test
    void skipsNonWorkingDays() {
        // 2026-06-08 is a Monday; restrict to Tue-only so Monday yields nothing
        Instant now = Instant.parse("2026-06-08T08:00:00Z");
        List<Instant> slots = AvailabilityPlanner.slots(
                now, UTC, EnumSet.of(DayOfWeek.TUESDAY), 9, 17, 60, 0, List.of(), 20);
        assertThat(slots).isEmpty();
    }

    @Test
    void respectsMaxSlots() {
        Instant now = Instant.parse("2026-06-08T08:00:00Z");
        List<Instant> slots = AvailabilityPlanner.slots(
                now, UTC, ALL_DAYS, 9, 17, 60, 7, List.of(), 3);
        assertThat(slots).hasSize(3);
    }

    @Test
    void parsesWorkDays() {
        assertThat(AvailabilityPlanner.parseWorkDays("MON,WED,FRI"))
                .containsExactlyInAnyOrder(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY);
        assertThat(AvailabilityPlanner.parseWorkDays("")).isEmpty();
        assertThat(AvailabilityPlanner.parseWorkDays(null)).isEmpty();
    }
}
