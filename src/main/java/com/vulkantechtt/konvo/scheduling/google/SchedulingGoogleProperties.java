package com.vulkantechtt.konvo.scheduling.google;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Google Calendar connect-flow config. Reuses the same OAuth client as platform
 * login ({@code GOOGLE_CLIENT_ID}/{@code GOOGLE_CLIENT_SECRET}) but a distinct
 * redirect URI and the Calendar scope with offline access — this is a separate
 * authorization-code flow from Spring's {@code oauth2Login} (which only does
 * OIDC sign-in). The Google Cloud OAuth client must list {@code redirectUri} as
 * an authorized redirect URI and have the Calendar API enabled.
 */
@ConfigurationProperties(prefix = "konvo.scheduling.google")
public class SchedulingGoogleProperties {

    private String clientId;
    private String clientSecret;
    /** Backend callback (no JWT) — must match a Google Cloud authorized redirect URI. */
    private String redirectUri;
    /** Space-delimited; calendar covers freeBusy + events, openid/email identify the account. */
    private String scope = "https://www.googleapis.com/auth/calendar openid email";
    private String authUri = "https://accounts.google.com/o/oauth2/v2/auth";
    private String tokenUri = "https://oauth2.googleapis.com/token";
    private String userInfoUri = "https://openidconnect.googleapis.com/v1/userinfo";

    public boolean isConfigured() {
        return clientId != null && !clientId.isBlank()
                && clientSecret != null && !clientSecret.isBlank()
                && redirectUri != null && !redirectUri.isBlank();
    }

    public String getClientId() { return clientId; }
    public void setClientId(String clientId) { this.clientId = clientId; }
    public String getClientSecret() { return clientSecret; }
    public void setClientSecret(String clientSecret) { this.clientSecret = clientSecret; }
    public String getRedirectUri() { return redirectUri; }
    public void setRedirectUri(String redirectUri) { this.redirectUri = redirectUri; }
    public String getScope() { return scope; }
    public void setScope(String scope) { this.scope = scope; }
    public String getAuthUri() { return authUri; }
    public void setAuthUri(String authUri) { this.authUri = authUri; }
    public String getTokenUri() { return tokenUri; }
    public void setTokenUri(String tokenUri) { this.tokenUri = tokenUri; }
    public String getUserInfoUri() { return userInfoUri; }
    public void setUserInfoUri(String userInfoUri) { this.userInfoUri = userInfoUri; }
}
