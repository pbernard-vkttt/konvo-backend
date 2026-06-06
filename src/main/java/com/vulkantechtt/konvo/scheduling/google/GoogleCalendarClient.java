package com.vulkantechtt.konvo.scheduling.google;

import com.vulkantechtt.konvo.common.KonvoException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * Thin Google Calendar REST adapter (no Google SDK — same hand-rolled RestClient
 * approach as {@code MetaWhatsAppProvider}). Covers the OAuth token exchange /
 * refresh, account lookup, free/busy availability, and event create/delete.
 * Token lifecycle (refresh + persistence) is owned by {@link GoogleTokenService};
 * callers here pass a known-valid access token.
 */
@Component
public class GoogleCalendarClient {

    private static final Logger log = LoggerFactory.getLogger(GoogleCalendarClient.class);
    private static final String CALENDAR_BASE = "https://www.googleapis.com/calendar/v3";

    private final SchedulingGoogleProperties props;
    private final RestClient http;

    @Autowired
    public GoogleCalendarClient(SchedulingGoogleProperties props) {
        this(props, RestClient.builder());
    }

    GoogleCalendarClient(SchedulingGoogleProperties props, RestClient.Builder builder) {
        this.props = props;
        this.http = builder.build();
    }

    /** Exchanges the authorization code from the consent redirect for tokens. */
    public TokenResult exchangeCode(String code) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("code", code);
        form.add("client_id", props.getClientId());
        form.add("client_secret", props.getClientSecret());
        form.add("redirect_uri", props.getRedirectUri());
        form.add("grant_type", "authorization_code");
        return postToken(form);
    }

    /** Trades a stored refresh token for a fresh access token. */
    public TokenResult refreshAccessToken(String refreshToken) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("refresh_token", refreshToken);
        form.add("client_id", props.getClientId());
        form.add("client_secret", props.getClientSecret());
        form.add("grant_type", "refresh_token");
        return postToken(form);
    }

    private TokenResult postToken(MultiValueMap<String, String> form) {
        try {
            TokenResponse resp = http.post()
                    .uri(props.getTokenUri())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(TokenResponse.class);
            if (resp == null || resp.access_token() == null) {
                throw new KonvoException(HttpStatus.BAD_GATEWAY, "google_token_failed",
                        "Google returned no access token");
            }
            Instant expiresAt = Instant.now().plusSeconds(resp.expires_in() > 0 ? resp.expires_in() : 3600);
            return new TokenResult(resp.access_token(), resp.refresh_token(), expiresAt);
        } catch (RestClientResponseException e) {
            log.warn("Google token exchange failed status={} body={}",
                    e.getStatusCode(), e.getResponseBodyAsString());
            throw new KonvoException(HttpStatus.BAD_GATEWAY, "google_token_failed",
                    "Could not complete Google authentication");
        }
    }

    /** Returns the email of the account that granted access. */
    public String fetchEmail(String accessToken) {
        try {
            UserInfo info = http.get()
                    .uri(props.getUserInfoUri())
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .retrieve()
                    .body(UserInfo.class);
            return info != null ? info.email() : null;
        } catch (RestClientResponseException e) {
            log.warn("Google userinfo failed status={} body={}", e.getStatusCode(), e.getResponseBodyAsString());
            return null;
        }
    }

    /** Busy intervals for the calendar in [timeMin, timeMax) (RFC3339 instants). */
    public List<BusyInterval> freeBusy(String accessToken, String calendarId, Instant timeMin, Instant timeMax) {
        Map<String, Object> body = Map.of(
                "timeMin", timeMin.toString(),
                "timeMax", timeMax.toString(),
                "items", List.of(Map.of("id", calendarId)));
        try {
            FreeBusyResponse resp = http.post()
                    .uri(CALENDAR_BASE + "/freeBusy")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(FreeBusyResponse.class);
            if (resp == null || resp.calendars() == null) {
                return List.of();
            }
            FreeBusyResponse.CalendarBusy cal = resp.calendars().get(calendarId);
            if (cal == null || cal.busy() == null) {
                return List.of();
            }
            return cal.busy().stream()
                    .map(b -> new BusyInterval(Instant.parse(b.start()), Instant.parse(b.end())))
                    .toList();
        } catch (RestClientResponseException e) {
            log.warn("Google freeBusy failed status={} body={}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new KonvoException(HttpStatus.BAD_GATEWAY, "google_freebusy_failed",
                    "Could not read calendar availability");
        }
    }

    /** Creates a timed event; returns the Google event id. */
    public String insertEvent(String accessToken, String calendarId, EventRequest event) {
        var start = new java.util.LinkedHashMap<String, Object>();
        start.put("dateTime", event.start().toString());
        start.put("timeZone", event.timeZone());
        var end = new java.util.LinkedHashMap<String, Object>();
        end.put("dateTime", event.end().toString());
        end.put("timeZone", event.timeZone());

        var body = new java.util.LinkedHashMap<String, Object>();
        body.put("summary", event.summary());
        if (event.description() != null) {
            body.put("description", event.description());
        }
        body.put("start", start);
        body.put("end", end);
        if (event.attendeeEmail() != null && !event.attendeeEmail().isBlank()) {
            body.put("attendees", List.of(Map.of("email", event.attendeeEmail())));
        }
        try {
            EventResponse resp = http.post()
                    .uri(CALENDAR_BASE + "/calendars/{calId}/events", calendarId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(EventResponse.class);
            if (resp == null || resp.id() == null) {
                throw new KonvoException(HttpStatus.BAD_GATEWAY, "google_event_failed",
                        "Google returned no event id");
            }
            return resp.id();
        } catch (RestClientResponseException e) {
            log.warn("Google event insert failed status={} body={}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new KonvoException(HttpStatus.BAD_GATEWAY, "google_event_failed",
                    "Could not create the calendar event");
        }
    }

    /** Deletes an event. A 404/410 (already gone) is treated as success. */
    public void deleteEvent(String accessToken, String calendarId, String eventId) {
        try {
            http.delete()
                    .uri(CALENDAR_BASE + "/calendars/{calId}/events/{eventId}", calendarId, eventId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException e) {
            int status = e.getStatusCode().value();
            if (status == 404 || status == 410) {
                return;
            }
            log.warn("Google event delete failed status={} body={}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new KonvoException(HttpStatus.BAD_GATEWAY, "google_event_delete_failed",
                    "Could not cancel the calendar event");
        }
    }

    // --- value types -------------------------------------------------------

    public record TokenResult(String accessToken, String refreshToken, Instant expiresAt) {}

    public record BusyInterval(Instant start, Instant end) {}

    public record EventRequest(
            String summary, String description, Instant start, Instant end,
            String timeZone, String attendeeEmail) {}

    // --- wire shapes (Jackson 3) -------------------------------------------

    record TokenResponse(String access_token, String refresh_token, long expires_in,
                         String scope, String token_type, String id_token) {}

    record UserInfo(String email, String sub, String name) {}

    record FreeBusyResponse(Map<String, CalendarBusy> calendars) {
        record CalendarBusy(List<Slot> busy) {}
        record Slot(String start, String end) {}
    }

    record EventResponse(String id, String status, String htmlLink) {}
}
