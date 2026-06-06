package com.vulkantechtt.konvo.scheduling.google;

import com.vulkantechtt.konvo.common.KonvoException;
import com.vulkantechtt.konvo.scheduling.SchedulingSettings;
import com.vulkantechtt.konvo.scheduling.SchedulingSettingsRepository;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Hands out a valid Google access token for a tenant, refreshing and persisting
 * it transparently when the cached one is missing or about to expire. Callers
 * (availability, booking) never deal with refresh logic.
 */
@Service
public class GoogleTokenService {

    /** Refresh a little before actual expiry to avoid edge-of-window 401s. */
    private static final long SKEW_SECONDS = 60;

    private final SchedulingSettingsRepository settings;
    private final GoogleCalendarClient client;

    public GoogleTokenService(SchedulingSettingsRepository settings, GoogleCalendarClient client) {
        this.settings = settings;
        this.client = client;
    }

    @Transactional
    public String validAccessToken(SchedulingSettings s) {
        if (!s.isGoogleConnected() || s.getGoogleRefreshToken() == null) {
            throw new KonvoException(HttpStatus.CONFLICT, "google_not_connected",
                    "Google Calendar is not connected for this workspace");
        }
        Instant now = Instant.now();
        boolean fresh = s.getGoogleAccessToken() != null
                && s.getGoogleTokenExpiresAt() != null
                && s.getGoogleTokenExpiresAt().isAfter(now.plusSeconds(SKEW_SECONDS));
        if (fresh) {
            return s.getGoogleAccessToken();
        }
        GoogleCalendarClient.TokenResult refreshed = client.refreshAccessToken(s.getGoogleRefreshToken());
        s.setGoogleAccessToken(refreshed.accessToken());
        s.setGoogleTokenExpiresAt(refreshed.expiresAt());
        // Google only returns a refresh_token on first consent; keep the existing one otherwise.
        if (refreshed.refreshToken() != null && !refreshed.refreshToken().isBlank()) {
            s.setGoogleRefreshToken(refreshed.refreshToken());
        }
        settings.save(s);
        return refreshed.accessToken();
    }
}
