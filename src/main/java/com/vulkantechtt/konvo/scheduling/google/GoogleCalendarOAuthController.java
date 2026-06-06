package com.vulkantechtt.konvo.scheduling.google;

import com.vulkantechtt.konvo.common.KonvoException;
import com.vulkantechtt.konvo.scheduling.SchedulingService;
import com.vulkantechtt.konvo.scheduling.dto.SchedulingSettingsResponse;
import com.vulkantechtt.konvo.security.KonvoPrincipal;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Separate Google authorization-code flow for the per-workspace Calendar
 * connection (distinct from {@code oauth2Login} platform sign-in). The
 * {@code /authorize} URL is built for the authenticated initiator and carries a
 * signed {@code state}; the browser hits Google, then Google redirects to
 * {@code /callback} (which is public — no JWT — so it's whitelisted in
 * SecurityConfig and trusts the signed state instead).
 */
@RestController
@RequestMapping("/api/v1/scheduling/google")
public class GoogleCalendarOAuthController {

    private static final Logger log = LoggerFactory.getLogger(GoogleCalendarOAuthController.class);

    private final SchedulingGoogleProperties props;
    private final GoogleCalendarClient client;
    private final OAuthStateCodec state;
    private final SchedulingService scheduling;
    private final String appBaseUrl;

    public GoogleCalendarOAuthController(SchedulingGoogleProperties props,
                                         GoogleCalendarClient client,
                                         OAuthStateCodec state,
                                         SchedulingService scheduling,
                                         @Value("${konvo.public-base-url.app}") String appBaseUrl) {
        this.props = props;
        this.client = client;
        this.state = state;
        this.scheduling = scheduling;
        this.appBaseUrl = appBaseUrl != null && appBaseUrl.endsWith("/")
                ? appBaseUrl.substring(0, appBaseUrl.length() - 1) : appBaseUrl;
    }

    /** Returns the Google consent URL for the frontend to redirect the browser to. */
    @GetMapping("/authorize")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
    public Map<String, String> authorize(@AuthenticationPrincipal KonvoPrincipal principal) {
        if (!props.isConfigured()) {
            throw new KonvoException(HttpStatus.SERVICE_UNAVAILABLE, "google_not_configured",
                    "Google Calendar is not configured on this server");
        }
        String url = UriComponentsBuilder.fromUriString(props.getAuthUri())
                .queryParam("client_id", props.getClientId())
                .queryParam("redirect_uri", props.getRedirectUri())
                .queryParam("response_type", "code")
                .queryParam("scope", props.getScope())
                .queryParam("access_type", "offline")
                .queryParam("prompt", "consent")
                .queryParam("include_granted_scopes", "true")
                .queryParam("state", state.issue(principal.tenantId(), principal.userId()))
                .build()
                .encode()
                .toUriString();
        return Map.of("url", url);
    }

    /** Google redirects here after consent. Public endpoint — trusts the signed state. */
    @GetMapping("/callback")
    public void callback(@RequestParam(required = false) String code,
                         @RequestParam(required = false) String state,
                         @RequestParam(required = false) String error,
                         HttpServletResponse response) throws IOException {
        String target = appBaseUrl + "/app/settings/scheduling";
        if (error != null || code == null || state == null) {
            log.warn("Google calendar callback rejected: error={} hasCode={} hasState={}",
                    error, code != null, state != null);
            response.sendRedirect(target + "?google=error");
            return;
        }
        try {
            OAuthStateCodec.State st = this.state.verify(state);
            GoogleCalendarClient.TokenResult tokens = client.exchangeCode(code);
            String email = client.fetchEmail(tokens.accessToken());
            scheduling.connectGoogle(st.tenantId(), email, tokens);
            response.sendRedirect(target + "?google=connected");
        } catch (RuntimeException ex) {
            log.warn("Google calendar connect failed", ex);
            response.sendRedirect(target + "?google=error");
        }
    }

    @DeleteMapping
    @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
    public SchedulingSettingsResponse disconnect(@AuthenticationPrincipal KonvoPrincipal principal) {
        return scheduling.disconnectGoogle(principal);
    }
}
