package com.vulkantechtt.konvo.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Maps {@code konvo.security.jwt.*} from application.yml. The signing secret
 * is required at startup; dev profile ships a default that the prod profile
 * explicitly overrides via env var.
 */
@ConfigurationProperties(prefix = "konvo.security.jwt")
public class AuthProperties {

    /** HS256 signing secret. Must be at least 32 chars. */
    private String signingSecret;

    /** Lifetime of issued access tokens. */
    private int accessTokenTtlMinutes = 15;

    /** Lifetime of issued refresh tokens. */
    private int refreshTokenTtlDays = 30;

    /** Cookie name carrying the refresh token. */
    private String refreshCookieName = "konvo_refresh";

    /** Set the Secure flag on the refresh cookie. Disable in plain-HTTP dev. */
    private boolean refreshCookieSecure = false;

    /** Path scope of the refresh cookie. */
    private String refreshCookiePath = "/api/v1/auth";

    /** SameSite directive for the refresh cookie. */
    private String refreshCookieSameSite = "Lax";

    public String getSigningSecret() { return signingSecret; }
    public void setSigningSecret(String signingSecret) { this.signingSecret = signingSecret; }

    public int getAccessTokenTtlMinutes() { return accessTokenTtlMinutes; }
    public void setAccessTokenTtlMinutes(int accessTokenTtlMinutes) { this.accessTokenTtlMinutes = accessTokenTtlMinutes; }

    public int getRefreshTokenTtlDays() { return refreshTokenTtlDays; }
    public void setRefreshTokenTtlDays(int refreshTokenTtlDays) { this.refreshTokenTtlDays = refreshTokenTtlDays; }

    public String getRefreshCookieName() { return refreshCookieName; }
    public void setRefreshCookieName(String refreshCookieName) { this.refreshCookieName = refreshCookieName; }

    public boolean isRefreshCookieSecure() { return refreshCookieSecure; }
    public void setRefreshCookieSecure(boolean refreshCookieSecure) { this.refreshCookieSecure = refreshCookieSecure; }

    public String getRefreshCookiePath() { return refreshCookiePath; }
    public void setRefreshCookiePath(String refreshCookiePath) { this.refreshCookiePath = refreshCookiePath; }

    public String getRefreshCookieSameSite() { return refreshCookieSameSite; }
    public void setRefreshCookieSameSite(String refreshCookieSameSite) { this.refreshCookieSameSite = refreshCookieSameSite; }
}
