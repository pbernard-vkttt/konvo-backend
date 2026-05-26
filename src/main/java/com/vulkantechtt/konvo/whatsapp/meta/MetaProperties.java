package com.vulkantechtt.konvo.whatsapp.meta;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "konvo.whatsapp.meta")
public class MetaProperties {

    /** Graph API base, e.g. https://graph.facebook.com */
    private String graphBaseUrl = "https://graph.facebook.com";

    /** Pinned Graph API version. Bump deliberately; Meta deprecates yearly. */
    private String graphApiVersion = "v21.0";

    /** Fallback verify token used only by the legacy single-channel webhook
     *  shape (M1). M3+ stores a per-channel verify token in the channel row. */
    private String verifyToken = "konvo-dev-verify";

    public String getGraphBaseUrl() { return graphBaseUrl; }
    public void setGraphBaseUrl(String graphBaseUrl) { this.graphBaseUrl = graphBaseUrl; }

    public String getGraphApiVersion() { return graphApiVersion; }
    public void setGraphApiVersion(String graphApiVersion) { this.graphApiVersion = graphApiVersion; }

    public String getVerifyToken() { return verifyToken; }
    public void setVerifyToken(String verifyToken) { this.verifyToken = verifyToken; }
}
