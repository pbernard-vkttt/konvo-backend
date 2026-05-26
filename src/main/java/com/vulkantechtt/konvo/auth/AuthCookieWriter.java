package com.vulkantechtt.konvo.auth;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

/**
 * Builds the HTTP-only refresh-token cookie. Centralised so login, refresh,
 * register-owner, accept-invitation, and logout all share one definition.
 */
@Component
public class AuthCookieWriter {

    private final AuthProperties props;

    public AuthCookieWriter(AuthProperties props) {
        this.props = props;
    }

    public void apply(ResponseEntity.BodyBuilder builder, String rawToken) {
        builder.header(HttpHeaders.SET_COOKIE, build(rawToken).toString());
    }

    public ResponseCookie build(String rawToken) {
        return ResponseCookie.from(props.getRefreshCookieName(), rawToken)
                .httpOnly(true)
                .secure(props.isRefreshCookieSecure())
                .sameSite(props.getRefreshCookieSameSite())
                .path(props.getRefreshCookiePath())
                .maxAge(Duration.ofDays(props.getRefreshTokenTtlDays()))
                .build();
    }

    public ResponseCookie buildCleared() {
        return ResponseCookie.from(props.getRefreshCookieName(), "")
                .httpOnly(true)
                .secure(props.isRefreshCookieSecure())
                .sameSite(props.getRefreshCookieSameSite())
                .path(props.getRefreshCookiePath())
                .maxAge(0)
                .build();
    }

    public String readFromRequest(HttpServletRequest req) {
        if (req.getCookies() == null) {
            return null;
        }
        for (var c : req.getCookies()) {
            if (props.getRefreshCookieName().equals(c.getName())) {
                return c.getValue();
            }
        }
        return null;
    }
}
