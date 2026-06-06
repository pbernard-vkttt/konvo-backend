package com.vulkantechtt.konvo.scheduling;

import com.vulkantechtt.konvo.common.BaseEntity;
import com.vulkantechtt.konvo.security.crypto.EncryptedStringConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * One row per tenant: the workspace's single shared Google Calendar connection
 * plus the Calendly fallback link and booking knobs. The two Google token
 * fields are AES-encrypted at rest via {@link EncryptedStringConverter}, exactly
 * like {@code Channel.accessToken}. Booking mode is derived (see
 * {@link SchedulingService#bookingMode}).
 */
@Entity
@Table(name = "scheduling_settings")
public class SchedulingSettings extends BaseEntity {

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "google_connected", nullable = false)
    private boolean googleConnected = false;

    @Column(name = "google_account_email", length = 254)
    private String googleAccountEmail;

    @Column(name = "google_calendar_id", nullable = false, length = 255)
    private String googleCalendarId = "primary";

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "google_refresh_token", columnDefinition = "text")
    private String googleRefreshToken;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "google_access_token", columnDefinition = "text")
    private String googleAccessToken;

    @Column(name = "google_token_expires_at")
    private Instant googleTokenExpiresAt;

    @Column(name = "calendly_url", length = 500)
    private String calendlyUrl;

    @Column(name = "meeting_duration_minutes", nullable = false)
    private int meetingDurationMinutes = 30;

    @Column(name = "timezone", nullable = false, length = 64)
    private String timezone = "UTC";

    @Column(name = "booking_window_days", nullable = false)
    private int bookingWindowDays = 14;

    @Column(name = "work_day_start_hour", nullable = false)
    private int workDayStartHour = 9;

    @Column(name = "work_day_end_hour", nullable = false)
    private int workDayEndHour = 17;

    @Column(name = "work_days", nullable = false, length = 32)
    private String workDays = "MON,TUE,WED,THU,FRI";

    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

    public boolean isGoogleConnected() { return googleConnected; }
    public void setGoogleConnected(boolean googleConnected) { this.googleConnected = googleConnected; }

    public String getGoogleAccountEmail() { return googleAccountEmail; }
    public void setGoogleAccountEmail(String googleAccountEmail) { this.googleAccountEmail = googleAccountEmail; }

    public String getGoogleCalendarId() { return googleCalendarId; }
    public void setGoogleCalendarId(String googleCalendarId) { this.googleCalendarId = googleCalendarId; }

    public String getGoogleRefreshToken() { return googleRefreshToken; }
    public void setGoogleRefreshToken(String googleRefreshToken) { this.googleRefreshToken = googleRefreshToken; }

    public String getGoogleAccessToken() { return googleAccessToken; }
    public void setGoogleAccessToken(String googleAccessToken) { this.googleAccessToken = googleAccessToken; }

    public Instant getGoogleTokenExpiresAt() { return googleTokenExpiresAt; }
    public void setGoogleTokenExpiresAt(Instant googleTokenExpiresAt) { this.googleTokenExpiresAt = googleTokenExpiresAt; }

    public String getCalendlyUrl() { return calendlyUrl; }
    public void setCalendlyUrl(String calendlyUrl) { this.calendlyUrl = calendlyUrl; }

    public int getMeetingDurationMinutes() { return meetingDurationMinutes; }
    public void setMeetingDurationMinutes(int meetingDurationMinutes) { this.meetingDurationMinutes = meetingDurationMinutes; }

    public String getTimezone() { return timezone; }
    public void setTimezone(String timezone) { this.timezone = timezone; }

    public int getBookingWindowDays() { return bookingWindowDays; }
    public void setBookingWindowDays(int bookingWindowDays) { this.bookingWindowDays = bookingWindowDays; }

    public int getWorkDayStartHour() { return workDayStartHour; }
    public void setWorkDayStartHour(int workDayStartHour) { this.workDayStartHour = workDayStartHour; }

    public int getWorkDayEndHour() { return workDayEndHour; }
    public void setWorkDayEndHour(int workDayEndHour) { this.workDayEndHour = workDayEndHour; }

    public String getWorkDays() { return workDays; }
    public void setWorkDays(String workDays) { this.workDays = workDays; }
}
