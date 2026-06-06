package com.vulkantechtt.konvo.scheduling;

/**
 * Derived from {@link SchedulingSettings}: GOOGLE when a live calendar is
 * connected (Vee/agents book real events), LINK when only a Calendly URL is set
 * (send the link), DISABLED when neither is present (no scheduling affordance).
 */
public enum BookingMode {
    GOOGLE,
    LINK,
    DISABLED
}
